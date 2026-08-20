package cn.servicehub.catalog.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.domain.CaseMatchCandidate;
import cn.servicehub.catalog.domain.CaseMatchRecord;
import cn.servicehub.catalog.domain.CaseMatchRule;
import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.FormFieldType;
import cn.servicehub.catalog.domain.KnowledgeCase;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.catalog.domain.StandardTag;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.ticket.application.TicketCreateCommand;
import cn.servicehub.ticket.domain.TicketTag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ServiceCatalogService {
    private final ServiceCatalogRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock = Clock.systemUTC();

    public ServiceCatalogService(ServiceCatalogRepository repository, CurrentUserProvider currentUserProvider,
                                 AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.auditEventPublisher = auditEventPublisher;
    }

    public List<ServiceCatalogItem> listPublishedItems() {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        List<ServiceCatalogItem> items = repository.findPublishedItems();
        audit(actor, "SERVICE_CATALOG_LISTED", "collection", Map.of("returned", String.valueOf(items.size())));
        return items;
    }

    public ServiceCatalogItem getPublishedItem(String id) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ServiceCatalogItem item = publishedItem(id);
        audit(actor, "SERVICE_CATALOG_READ", item.id(), Map.of());
        return item;
    }

    /** Published forms have a stable version boundary even while requester-facing catalog administration is pending. */
    public ServiceCatalogItem getPublishedForm(String id, int formVersion) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ServiceCatalogItem item = publishedItem(id);
        requireCurrentFormVersion(formVersion);
        audit(actor, "SERVICE_CATALOG_FORM_READ", item.id(), Map.of("formVersion", String.valueOf(formVersion)));
        return item;
    }

    public DictionaryDefinition getPublishedDictionary(String code) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        DictionaryDefinition dictionary = repository.findDictionary(code)
            .filter(value -> value.publicationStatus() == cn.servicehub.catalog.domain.CatalogPublicationStatus.PUBLISHED)
            .orElseThrow(CatalogValidationException::new);
        audit(actor, "SERVICE_DICTIONARY_READ", code, Map.of());
        return dictionary;
    }

    public List<StandardTag> standardTags() {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        List<StandardTag> tags = repository.findEnabledStandardTags();
        audit(actor, "SERVICE_STANDARD_TAGS_LISTED", "collection", Map.of("returned", String.valueOf(tags.size())));
        return tags;
    }

    public DictionaryDefinition getPublishedDictionaryForField(String catalogItemId, int formVersion, String fieldCode,
                                                                 String dictionaryCode) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ServiceCatalogItem item = publishedItem(catalogItemId);
        requireCurrentFormVersion(formVersion);
        boolean assigned = item.fields().stream().anyMatch(field -> field.code().equals(fieldCode)
            && dictionaryCode.equals(field.dictionaryCode()));
        if (!assigned) throw new CatalogValidationException();
        DictionaryDefinition dictionary = repository.findDictionary(dictionaryCode)
            .filter(value -> value.publicationStatus() == cn.servicehub.catalog.domain.CatalogPublicationStatus.PUBLISHED)
            .orElseThrow(CatalogValidationException::new);
        audit(actor, "SERVICE_DICTIONARY_READ", dictionaryCode, Map.of("catalogItemId", item.id(), "fieldCode", fieldCode));
        return dictionary;
    }

    /** Called by ticket creation after authentication; this is the authoritative form/schema gate. */
    public ServiceCatalogItem validateTicketInput(TicketCreateCommand command) {
        ServiceCatalogItem item = publishedItem(command.serviceCatalogItemId());
        if (!item.supportedTicketTypes().contains(command.type())) {
            throw new CatalogValidationException();
        }
        validateFields(item, command.structuredFields());
        validateTags(command.tags());
        return item;
    }

    public CaseMatchResult match(CaseMatchCommand command) {
        return match(CURRENT_FORM_VERSION, command);
    }

    public CaseMatchResult match(int formVersion, CaseMatchCommand command) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ServiceCatalogItem item = publishedItem(command.serviceCatalogItemId());
        requireCurrentFormVersion(formVersion);
        validateFields(item, command.structuredFields());
        validateTags(command.tags());

        Map<String, KnowledgeCase> publishedCases = repository.findPublishedCases().stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeCase::id, value -> value));
        Map<String, CandidateAccumulator> matches = new HashMap<>();
        for (CaseMatchRule rule : repository.findEnabledRules()) {
            KnowledgeCase knowledgeCase = publishedCases.get(rule.caseId());
            if (knowledgeCase != null && matches(rule, command)) {
                matches.computeIfAbsent(rule.caseId(), ignored -> new CandidateAccumulator(knowledgeCase))
                    .add(rule.score(), ruleReasons(rule));
            }
        }
        List<CaseMatchCandidate> candidates = matches.values().stream()
            .map(CandidateAccumulator::candidate)
            .sorted(Comparator.comparingInt(CaseMatchCandidate::score).reversed().thenComparing(CaseMatchCandidate::caseId))
            .limit(10)
            .toList();
        String recordId = UUID.randomUUID().toString();
        repository.saveMatchRecord(new CaseMatchRecord(recordId, actor.iamUserId(), item.id(), criteriaHash(command),
            candidates.stream().map(CaseMatchCandidate::caseId).toList(), clock.instant()));
        audit(actor, "KNOWLEDGE_CASE_MATCHED", recordId, Map.of("catalogItemId", item.id(), "returned", String.valueOf(candidates.size())));
        return new CaseMatchResult(recordId, candidates);
    }

    public int currentFormVersion() { return CURRENT_FORM_VERSION; }

    /** Hash is an output cache validator, never a value trusted back from browser clients. */
    public String formSchemaHash(ServiceCatalogItem item) {
        String material = item.id() + '\u001f' + CURRENT_FORM_VERSION + '\u001f' + item.fields().stream()
            .sorted(Comparator.comparingInt(cn.servicehub.catalog.domain.FormFieldDefinition::sortOrder)
                .thenComparing(cn.servicehub.catalog.domain.FormFieldDefinition::code))
            .map(field -> field.code() + ':' + field.type() + ':' + field.required() + ':' + field.maxLength() + ':' + field.dictionaryCode())
            .collect(java.util.stream.Collectors.joining("|"));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ServiceCatalogItem publishedItem(String id) {
        return repository.findById(id).filter(ServiceCatalogItem::isPublished).orElseThrow(CatalogValidationException::new);
    }

    private void requireCurrentFormVersion(int formVersion) {
        if (formVersion != CURRENT_FORM_VERSION) throw new CatalogValidationException();
    }

    private void validateFields(ServiceCatalogItem item, Map<String, Object> values) {
        Map<String, FormFieldDefinition> definitions = item.fields().stream()
            .collect(java.util.stream.Collectors.toMap(FormFieldDefinition::code, value -> value));
        if (!definitions.keySet().containsAll(values.keySet())) {
            throw new CatalogValidationException();
        }
        for (FormFieldDefinition definition : definitions.values()) {
            Object value = values.get(definition.code());
            if (value == null) {
                if (definition.required()) throw new CatalogValidationException();
                continue;
            }
            validateFieldValue(definition, value);
        }
    }

    private void validateFieldValue(FormFieldDefinition definition, Object value) {
        switch (definition.type()) {
            case TEXT, CI_ID -> validateString(definition, value);
            case SINGLE_SELECT -> {
                String option = stringValue(value);
                if (definition.dictionaryCode() == null || !dictionary(definition.dictionaryCode()).permits(option)) {
                    throw new CatalogValidationException();
                }
            }
            case MULTI_SELECT -> {
                if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 50) throw new CatalogValidationException();
                for (Object option : list) {
                    String optionCode = stringValue(option);
                    if (definition.dictionaryCode() == null || !dictionary(definition.dictionaryCode()).permits(optionCode)) {
                        throw new CatalogValidationException();
                    }
                }
            }
        }
    }

    private void validateString(FormFieldDefinition definition, Object value) {
        String text = stringValue(value);
        if (text.isBlank() || (definition.maxLength() != null && text.length() > definition.maxLength())) {
            throw new CatalogValidationException();
        }
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string)) throw new CatalogValidationException();
        return string.trim();
    }

    private DictionaryDefinition dictionary(String code) {
        return repository.findDictionary(code).orElseThrow(CatalogValidationException::new);
    }

    private void validateTags(List<TicketTag> tags) {
        Set<String> standard = repository.findEnabledStandardTags().stream().map(StandardTag::name).collect(java.util.stream.Collectors.toSet());
        for (TicketTag tag : tags) {
            if (tag.kind() == TicketTag.Kind.STANDARD && !standard.contains(tag.name())) {
                throw new CatalogValidationException();
            }
        }
    }

    private boolean matches(CaseMatchRule rule, CaseMatchCommand command) {
        if (rule.catalogItemId() != null && !rule.catalogItemId().equals(command.serviceCatalogItemId())) return false;
        if (rule.configurationItemId() != null && !command.relatedConfigurationItemIds().contains(rule.configurationItemId())) return false;
        if (rule.fieldCode() != null && !valueMatches(command.structuredFields().get(rule.fieldCode()), rule.fieldValue())) return false;
        if (rule.tagName() != null && command.tags().stream().noneMatch(tag -> tag.name().equals(rule.tagName())
            && (rule.tagKind() == null || tag.kind() == rule.tagKind()))) return false;
        if (rule.errorCode() != null && command.structuredFields().values().stream().noneMatch(value -> valueMatches(value, rule.errorCode()))) return false;
        if (rule.keyword() != null && !normalize(command.keywords()).contains(normalize(rule.keyword()))) return false;
        return hasCriterion(rule);
    }

    private boolean hasCriterion(CaseMatchRule rule) {
        return rule.catalogItemId() != null || rule.configurationItemId() != null || rule.fieldCode() != null || rule.tagName() != null
            || rule.errorCode() != null || rule.keyword() != null;
    }

    private boolean valueMatches(Object actual, String expected) {
        if (actual instanceof String value) return value.trim().equalsIgnoreCase(expected.trim());
        if (actual instanceof Collection<?> values) return values.stream().anyMatch(value -> valueMatches(value, expected));
        return false;
    }

    private List<String> ruleReasons(CaseMatchRule rule) {
        List<String> values = new ArrayList<>();
        if (rule.catalogItemId() != null) values.add("CATALOG");
        if (rule.configurationItemId() != null) values.add("CI");
        if (rule.fieldCode() != null) values.add("FIELD:" + rule.fieldCode());
        if (rule.tagName() != null) values.add("TAG:" + rule.tagName());
        if (rule.errorCode() != null) values.add("ERROR_CODE");
        if (rule.keyword() != null) values.add("KEYWORD");
        return values;
    }

    private String criteriaHash(CaseMatchCommand command) {
        String source = command.serviceCatalogItemId() + '\u001f' + new java.util.TreeMap<>(command.structuredFields()) + '\u001f'
            + command.tags() + '\u001f' + command.relatedConfigurationItemIds() + '\u001f' + command.keywords();
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).trim(); }

    private void audit(CurrentUser actor, String action, String resourceId, Map<String, String> attributes) {
        String requestId = MDC.get("requestId");
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId == null ? "system" : requestId, actor.iamUserId(), action,
            "service-catalog", resourceId, attributes));
    }

    private static final class CandidateAccumulator {
        private final KnowledgeCase knowledgeCase;
        private int score;
        private final Set<String> reasons = new HashSet<>();
        private CandidateAccumulator(KnowledgeCase knowledgeCase) { this.knowledgeCase = knowledgeCase; }
        private void add(int matchedScore, List<String> matchedReasons) { score = Math.min(100, score + matchedScore); reasons.addAll(matchedReasons); }
        private CaseMatchCandidate candidate() { return new CaseMatchCandidate(knowledgeCase.id(), knowledgeCase.title(),
            knowledgeCase.resolutionSummary(), score, reasons.stream().sorted().toList()); }
    }

    private static final int CURRENT_FORM_VERSION = 1;
}
