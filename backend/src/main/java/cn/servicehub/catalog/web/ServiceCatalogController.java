package cn.servicehub.catalog.web;

import cn.servicehub.catalog.application.CaseMatchCommand;
import cn.servicehub.catalog.application.CaseMatchResult;
import cn.servicehub.catalog.application.ServiceCatalogService;
import cn.servicehub.ticket.domain.TicketTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/service-catalog")
public class ServiceCatalogController {
    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) { this.service = service; }

    @GetMapping("/items")
    ServiceCatalogPageResponse items(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) @Size(min = 1, max = 100) String q,
        @RequestParam(required = false) cn.servicehub.ticket.domain.TicketType ticketType,
        @RequestParam(required = false) @Size(max = 64) String categoryCode) {
        // categoryCode becomes a server-filtered catalog attribute when catalog administration is delivered;
        // until then all seeded/public catalog entries are in GENERAL.
        List<cn.servicehub.catalog.domain.ServiceCatalogItem> visible = service.listPublishedItems().stream()
            .filter(item -> ticketType == null || item.supportedTicketTypes().contains(ticketType))
            .filter(item -> q == null || (item.name() + " " + item.description()).toLowerCase(java.util.Locale.ROOT)
                .contains(q.trim().toLowerCase(java.util.Locale.ROOT)))
            .filter(item -> categoryCode == null || "GENERAL".equalsIgnoreCase(categoryCode))
            .toList();
        int from = Math.min((page - 1) * pageSize, visible.size());
        int to = Math.min(from + pageSize, visible.size());
        List<cn.servicehub.catalog.domain.StandardTag> tags = service.standardTags();
        return new ServiceCatalogPageResponse(visible.subList(from, to).stream()
            .map(item -> ServiceCatalogPageResponse.item(item, service, tags)).toList(), page, pageSize, visible.size());
    }

    /** Legacy compact representation retained for early prototype callers. */
    @GetMapping("/items/{id}")
    ServiceCatalogItemResponse item(@PathVariable @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$") String id) {
        return ServiceCatalogItemResponse.from(service.getRequesterItem(id));
    }

    @GetMapping("/items/{serviceCatalogItemId}/form")
    ServiceCatalogPageResponse.Form form(@PathVariable @Pattern(regexp = "^SC-[A-Za-z0-9_-]{3,60}$") String serviceCatalogItemId) {
        var form = service.getRequesterForm(serviceCatalogItemId);
        return ServiceCatalogPageResponse.form(form, service, service.standardTags());
    }

    @GetMapping("/dictionaries/{code}")
    DictionaryResponse dictionary(@PathVariable @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{0,63}$") String code) {
        return DictionaryResponse.from(service.getPublishedDictionary(code));
    }

    @GetMapping("/dictionaries/{dictionaryCode}/entries")
    DictionaryEntryPageResponse dictionaryEntries(
        @PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,62}$") String dictionaryCode,
        @RequestParam @Pattern(regexp = "^SC-[A-Za-z0-9_-]{3,60}$") String serviceCatalogItemId,
        @RequestParam @Min(1) int formVersion,
        @RequestParam @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String fieldCode,
        @RequestParam(required = false) @Size(max = 2000) String dependsOn) {
        // The current minimal schema contains no declarative dependencies. The value is length-limited and never interpreted.
        return DictionaryEntryPageResponse.from(service.getPublishedDictionaryForField(serviceCatalogItemId, formVersion, fieldCode, dictionaryCode), formVersion);
    }

    @GetMapping("/standard-tags")
    List<StandardTagResponse> standardTags() {
        // Tags are a deliberately small centrally governed vocabulary; free #tags still use the request grammar.
        return service.standardTags().stream().map(StandardTagResponse::from).toList();
    }

    @PostMapping("/case-matches")
    CaseMatchResponse match(@Valid @RequestBody CaseMatchRequest request) {
        CaseMatchResult result = service.match(new CaseMatchCommand(request.serviceCatalogItemId(), request.structuredFields(),
            request.tags() == null ? List.of() : request.tags().stream().map(tag -> new TicketTag(tag.name().trim(), tag.kind())).toList(),
            request.relatedConfigurationItemIds(), request.keywords()));
        return CaseMatchResponse.from(result);
    }

    @PostMapping("/rule-matches")
    RuleMatchResponse ruleMatch(
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String ignoredIdempotencyKey,
        @Valid @RequestBody RuleMatchRequest request) {
        String keywords = String.join(" ", request.title() == null ? "" : request.title(), request.description() == null ? "" : request.description());
        CaseMatchResult result = service.match(request.formVersion(), new CaseMatchCommand(request.serviceCatalogItemId(), request.structuredFields(),
            request.tags() == null ? List.of() : request.tags().stream().map(tag -> new TicketTag(tag.name().trim(), tag.kind())).toList(),
            request.relatedConfigurationItemIds(), keywords));
        return RuleMatchResponse.from(result);
    }
}
