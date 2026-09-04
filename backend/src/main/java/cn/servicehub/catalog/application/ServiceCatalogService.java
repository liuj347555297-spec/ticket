package cn.servicehub.catalog.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.domain.CaseMatchCandidate;
import cn.servicehub.catalog.domain.CaseMatchRecord;
import cn.servicehub.catalog.domain.CaseMatchRule;
import cn.servicehub.catalog.domain.CatalogPublicationStatus;
import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.FormFieldType;
import cn.servicehub.catalog.domain.KnowledgeCase;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.catalog.domain.StandardTag;
import cn.servicehub.catalog.config.ConfigurableFormFieldType;
import cn.servicehub.catalog.config.ConfiguredFormField;
import cn.servicehub.catalog.config.FormCondition;
import cn.servicehub.catalog.config.FormConditionOperator;
import cn.servicehub.catalog.config.FormConfigurationRepository;
import cn.servicehub.catalog.config.FormConfigurationStatus;
import cn.servicehub.catalog.config.ManagedFormConfiguration;
import cn.servicehub.catalog.config.RequesterCatalogForm;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.ticket.application.TicketCreateCommand;
import cn.servicehub.ticket.domain.TicketTag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
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
    private final FormConfigurationRepository formConfigurations;
    private final cn.servicehub.iam.domain.IamUserProjectionRepository iamUsers;
    private final Clock clock = Clock.systemUTC();

    public ServiceCatalogService(ServiceCatalogRepository repository, CurrentUserProvider currentUserProvider,
                                 AuditEventPublisher auditEventPublisher, FormConfigurationRepository formConfigurations,
                                 cn.servicehub.iam.domain.IamUserProjectionRepository iamUsers) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.auditEventPublisher = auditEventPublisher;
        this.formConfigurations = formConfigurations;
        this.iamUsers = iamUsers;
    }

    public List<ServiceCatalogItem> listPublishedItems() {
        return listPublishedItemsForOrganization(currentRequesterOrganization());
    }

    /** Portal projection only. The caller resolves the organization from the current IAM user.
     * A managed record shadows its legacy seed even when retired or drafted; absence of an
     * explicit managed organization is not permission to expose it to every requester.
     */
    public List<ServiceCatalogItem> listPublishedItemsForOrganization(String organizationId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (organizationId == null || organizationId.isBlank()) return List.of();
        List<ManagedFormConfiguration> managed = formConfigurations.findAll();
        Set<String> managedIds = managed.stream().map(ManagedFormConfiguration::id).collect(java.util.stream.Collectors.toSet());
        List<ServiceCatalogItem> visible = new ArrayList<>(repository.findPublishedItems().stream()
            .filter(item -> !managedIds.contains(item.id())).toList());
        managed.stream().filter(value -> requesterVisible(value, organizationId))
            .map(this::asCatalogItem).forEach(visible::add);
        visible.sort(Comparator.comparing(ServiceCatalogItem::id));
        audit(actor, "SERVICE_CATALOG_PORTAL_LISTED", "collection", Map.of("returned", String.valueOf(visible.size())));
        return List.copyOf(visible);
    }

    public ServiceCatalogItem getPublishedItem(String id) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ServiceCatalogItem item = publishedItem(id);
        audit(actor, "SERVICE_CATALOG_READ", item.id(), Map.of());
        return item;
    }

    public ServiceCatalogItem getRequesterItem(String id) {
        requireRequesterVisibility(id);
        return getPublishedItem(id);
    }

    /** Published forms have a stable version boundary even while requester-facing catalog administration is pending. */
    public ServiceCatalogItem getPublishedForm(String id, int formVersion) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        requireRequesterVisibility(id);
        ServiceCatalogItem item = publishedItem(id);
        requireFormVersion(id, formVersion);
        audit(actor, "SERVICE_CATALOG_FORM_READ", item.id(), Map.of("formVersion", String.valueOf(formVersion)));
        return item;
    }

    public RequesterCatalogForm getRequesterForm(String id) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        requireRequesterVisibility(id);
        RequesterCatalogForm form = requesterForm(id);
        audit(actor, "SERVICE_CATALOG_FORM_READ", id, Map.of("formVersion", String.valueOf(form.formVersion())));
        return form;
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
        requireRequesterVisibility(catalogItemId);
        RequesterCatalogForm form = requesterForm(catalogItemId);
        requireFormVersion(catalogItemId, formVersion);
        boolean assigned = form.fields().stream().anyMatch(field -> field.code().equals(fieldCode)
            && dictionaryCode.equals(field.dictionaryCode()));
        if (!assigned) throw new CatalogValidationException();
        DictionaryDefinition dictionary = repository.findDictionary(dictionaryCode)
            .filter(value -> value.publicationStatus() == cn.servicehub.catalog.domain.CatalogPublicationStatus.PUBLISHED)
            .orElseThrow(CatalogValidationException::new);
        audit(actor, "SERVICE_DICTIONARY_READ", dictionaryCode, Map.of("catalogItemId", form.item().id(), "fieldCode", fieldCode));
        return dictionary;
    }

    /** Called by ticket creation after authentication; this is the authoritative form/schema gate. */
    public ServiceCatalogItem validateTicketInput(TicketCreateCommand command) {
        // Authoritative create-time recheck: URL selection, an earlier form read or a mapping
        // is not an authorization grant, and omitting systemCode must not bypass this gate.
        requireRequesterVisibility(command.serviceCatalogItemId());
        ServiceCatalogItem item = publishedItem(command.serviceCatalogItemId());
        requireFormVersion(command.serviceCatalogItemId(), command.serviceCatalogFormVersion());
        if (!item.supportedTicketTypes().contains(command.type())) {
            throw new CatalogValidationException();
        }
        ManagedFormConfiguration config = publishedConfiguration(command.serviceCatalogItemId());
        if (config == null) validateFields(item, command.structuredFields());
        else validateConfiguredFields(config, command);
        validateTags(command.tags());
        return item;
    }

    /** Applies only server-validated defaults from the frozen published version before persistence. */
    public Map<String, Object> normalizeStructuredFields(TicketCreateCommand command) {
        ManagedFormConfiguration configuration = publishedConfiguration(command.serviceCatalogItemId());
        if (configuration == null) return command.structuredFields();
        validateConfiguredFields(configuration, command);
        Map<String, Object> normalized = new HashMap<>(command.structuredFields());
        for (ConfiguredFormField field : configuration.fields()) {
            if (field.type() == ConfigurableFormFieldType.RICH_TEXT || field.type() == ConfigurableFormFieldType.TAGS || normalized.containsKey(field.code()) || field.defaultValue() == null || !conditionsMatch(field.visibleWhen(), normalized)) continue;
            Object value = defaultValue(field);
            validateConfiguredValue(field, value);
            normalized.put(field.code(), value);
        }
        return Map.copyOf(normalized);
    }

    /** CI form values become the ticket's controlled CMDB association; clients cannot create a second unvalidated path. */
    public List<String> configurationItemReferences(TicketCreateCommand command, Map<String, Object> normalizedFields) {
        var references = new java.util.LinkedHashSet<String>(command.relatedConfigurationItemIds());
        ManagedFormConfiguration configuration = publishedConfiguration(command.serviceCatalogItemId());
        if (configuration != null) for (ConfiguredFormField field : configuration.fields()) {
            if (field.type() == ConfigurableFormFieldType.CI_REFERENCE && normalizedFields.get(field.code()) instanceof String value && !value.isBlank()) references.add(value.trim());
        }
        if (references.size() > 20) throw new CatalogValidationException();
        return List.copyOf(references);
    }

    public CaseMatchResult match(CaseMatchCommand command) {
        return match(CURRENT_FORM_VERSION, command);
    }

    public CaseMatchResult match(int formVersion, CaseMatchCommand command) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        requireRequesterVisibility(command.serviceCatalogItemId());
        ServiceCatalogItem item = publishedItem(command.serviceCatalogItemId());
        requireFormVersion(command.serviceCatalogItemId(), formVersion);
        ManagedFormConfiguration config = publishedConfiguration(command.serviceCatalogItemId());
        if (config == null) validateFields(item, command.structuredFields());
        else validateConfiguredFields(config, new TicketCreateCommand(command.serviceCatalogItemId(), formVersion, item.supportedTicketTypes().iterator().next(), "match", command.keywords(), cn.servicehub.ticket.domain.TicketDescriptionFormat.PLAIN_TEXT, null, command.structuredFields(), command.tags(), command.relatedConfigurationItemIds()));
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
    public int currentFormVersion(String catalogItemId) { RequesterCatalogForm form = requesterForm(catalogItemId); return form.formVersion(); }
    /** Knowledge scope validation needs a side-effect-free published catalog check. */
    public boolean isPublishedItem(String id) { return id != null && (publishedConfiguration(id) != null || repository.findById(id).filter(ServiceCatalogItem::isPublished).isPresent()); }

    /** Hash is an output cache validator, never a value trusted back from browser clients. */
    public String formSchemaHash(ServiceCatalogItem item) {
        ManagedFormConfiguration configuration = publishedConfiguration(item.id());
        if (configuration != null) return configuration.schemaHash();
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
        ManagedFormConfiguration config = formConfigurations.findById(id).orElse(null);
        if (config != null) {
            if (config.status() != FormConfigurationStatus.PUBLISHED) throw new CatalogValidationException();
            return asCatalogItem(config);
        }
        return repository.findById(id).filter(ServiceCatalogItem::isPublished).orElseThrow(CatalogValidationException::new);
    }

    private boolean requesterVisible(ManagedFormConfiguration configuration, String organizationId) {
        return configuration.status() == FormConfigurationStatus.PUBLISHED && organizationId != null
            && !organizationId.isBlank() && configuration.applicableOrganizationIds().contains(organizationId);
    }

    private String currentRequesterOrganization() {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        return iamUsers.findActiveByIamUserId(actor.iamUserId()).map(value -> value.organization())
            .map(value -> value.iamOrganizationId())
            .filter(value -> value != null && !value.isBlank())
            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Active IAM organization is required"));
    }

    private void requireRequesterVisibility(String id) {
        String organizationId = currentRequesterOrganization();
        ManagedFormConfiguration config = formConfigurations.findById(id).orElse(null);
        // Legacy seeds without a managed replacement retain their compatible requester path.
        // A managed record always wins; empty organizations and non-published records fail closed.
        if (config != null && !requesterVisible(config, organizationId)) throw new CatalogValidationException();
    }

    private void requireCurrentFormVersion(int formVersion) {
        if (formVersion != CURRENT_FORM_VERSION) throw new CatalogValidationException();
    }

    private void requireFormVersion(String catalogItemId, int formVersion) {
        if (formVersion != currentFormVersion(catalogItemId)) throw new CatalogValidationException();
    }

    private ManagedFormConfiguration publishedConfiguration(String id) {
        return formConfigurations.findById(id).filter(value -> value.status() == FormConfigurationStatus.PUBLISHED).orElse(null);
    }

    private RequesterCatalogForm requesterForm(String id) {
        ManagedFormConfiguration config = formConfigurations.findById(id).orElse(null);
        if (config != null) {
            if (config.status() != FormConfigurationStatus.PUBLISHED) throw new CatalogValidationException();
            return new RequesterCatalogForm(asCatalogItem(config), config.formVersion(), config.schemaHash(), config.fields(), config.tagPolicy());
        }
        ServiceCatalogItem item = repository.findById(id).filter(ServiceCatalogItem::isPublished).orElseThrow(CatalogValidationException::new);
        return new RequesterCatalogForm(item, CURRENT_FORM_VERSION, formSchemaHash(item), item.fields().stream()
            .map(field -> new ConfiguredFormField(field.code(), field.label(), legacyType(field.type()), field.required(), null, null, field.maxLength(), field.dictionaryCode(), field.sortOrder() + 1, List.of(), List.of())).toList(), new cn.servicehub.catalog.config.TagPolicy(true, true, 20, List.of()));
    }

    private ServiceCatalogItem asCatalogItem(ManagedFormConfiguration config) {
        return new ServiceCatalogItem(config.id(), config.name(), config.summary() == null ? "" : config.summary(), CatalogPublicationStatus.PUBLISHED,
            Set.of(config.ticketType()), config.fields().stream().filter(field -> field.type() != ConfigurableFormFieldType.RICH_TEXT && field.type() != ConfigurableFormFieldType.TAGS)
                .map(field -> new FormFieldDefinition(field.code(), field.label(), legacyFieldType(field.type()), field.required(), field.maxLength(), field.dictionaryCode(), field.displayOrder() - 1)).toList());
    }

    private ConfigurableFormFieldType legacyType(FormFieldType type) { return switch(type) { case TEXT -> ConfigurableFormFieldType.TEXT; case SINGLE_SELECT -> ConfigurableFormFieldType.SINGLE_SELECT; case MULTI_SELECT -> ConfigurableFormFieldType.MULTI_SELECT; case CI_ID -> ConfigurableFormFieldType.CI_REFERENCE; }; }
    private FormFieldType legacyFieldType(ConfigurableFormFieldType type) { return switch(type) { case SINGLE_SELECT -> FormFieldType.SINGLE_SELECT; case MULTI_SELECT -> FormFieldType.MULTI_SELECT; case CI_REFERENCE -> FormFieldType.CI_ID; default -> FormFieldType.TEXT; }; }

    /** Validates exactly the frozen published schema. No browser supplied field definition or condition is evaluated. */
    private void validateConfiguredFields(ManagedFormConfiguration configuration, TicketCreateCommand command) {
        Map<String, Object> values = command.structuredFields();
        Set<String> accepted = configuration.fields().stream()
            .filter(field -> field.type() != ConfigurableFormFieldType.RICH_TEXT && field.type() != ConfigurableFormFieldType.TAGS)
            .map(ConfiguredFormField::code).collect(java.util.stream.Collectors.toSet());
        if (!accepted.containsAll(values.keySet())) throw new CatalogValidationException();
        for (ConfiguredFormField field : configuration.fields()) {
            if (field.type() == ConfigurableFormFieldType.RICH_TEXT) {
                if ((field.required() || conditionsMatch(field.requiredWhen(), values)) && command.description().isBlank()) throw new CatalogValidationException();
                continue;
            }
            if (field.type() == ConfigurableFormFieldType.TAGS) {
                if ((field.required() || conditionsMatch(field.requiredWhen(), values)) && command.tags().isEmpty()) throw new CatalogValidationException();
                continue;
            }
            boolean visible = conditionsMatch(field.visibleWhen(), values);
            Object value = values.get(field.code());
            if (!visible) { if (value != null) throw new CatalogValidationException(); continue; }
            boolean required = field.required() || conditionsMatch(field.requiredWhen(), values);
            if (value == null) { if (required && (field.defaultValue() == null || field.defaultValue().isBlank())) throw new CatalogValidationException(); continue; }
            validateConfiguredValue(field, value);
        }
    }

    private boolean conditionsMatch(List<FormCondition> conditions, Map<String, Object> values) {
        if (conditions == null || conditions.isEmpty()) return true;
        return conditions.stream().allMatch(condition -> {
            Object actual = values.get(condition.fieldCode());
            boolean present = actual != null && (!(actual instanceof String text) || !text.trim().isEmpty()) && (!(actual instanceof Collection<?> items) || !items.isEmpty());
            return switch (condition.operator()) {
                case HAS_VALUE -> present;
                case NO_VALUE -> !present;
                case EQUALS -> condition.values().stream().anyMatch(expected -> valueMatches(actual, expected));
                case NOT_EQUALS -> condition.values().stream().noneMatch(expected -> valueMatches(actual, expected));
                case IN -> condition.values().stream().anyMatch(expected -> valueMatches(actual, expected));
                case NOT_IN -> condition.values().stream().noneMatch(expected -> valueMatches(actual, expected));
            };
        });
    }

    private void validateConfiguredValue(ConfiguredFormField field, Object value) {
        switch (field.type()) {
            case TEXT, LONG_TEXT, CI_REFERENCE -> {
                String text = stringValue(value);
                if (text.isBlank() || (field.maxLength() != null && text.length() > field.maxLength())) throw new CatalogValidationException();
            }
            case SINGLE_SELECT -> {
                String option = stringValue(value);
                if (field.dictionaryCode() == null || !dictionary(field.dictionaryCode()).permits(option)) throw new CatalogValidationException();
            }
            case MULTI_SELECT -> {
                if (!(value instanceof List<?> values) || values.isEmpty() || values.size() > 50) throw new CatalogValidationException();
                for (Object option : values) if (field.dictionaryCode() == null || !dictionary(field.dictionaryCode()).permits(stringValue(option))) throw new CatalogValidationException();
            }
            case DATETIME -> { try { if (!(value instanceof String text) || text.length() > 64) throw new IllegalArgumentException(); OffsetDateTime.parse(text); } catch (RuntimeException ignored) { throw new CatalogValidationException(); } }
            case BOOLEAN -> { if (!(value instanceof Boolean)) throw new CatalogValidationException(); }
            case RICH_TEXT, TAGS -> throw new CatalogValidationException();
        }
    }

    private Object defaultValue(ConfiguredFormField field) {
        return switch (field.type()) {
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(field.defaultValue()) && !"false".equalsIgnoreCase(field.defaultValue())) throw new CatalogValidationException();
                yield Boolean.parseBoolean(field.defaultValue());
            }
            case MULTI_SELECT -> List.of(field.defaultValue().split(",", -1));
            case RICH_TEXT, TAGS -> throw new CatalogValidationException();
            default -> field.defaultValue();
        };
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
