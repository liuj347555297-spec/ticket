package cn.servicehub.ticket.web;

import cn.servicehub.ticket.application.TicketCreateCommand;
import cn.servicehub.ticket.application.TicketDescription;
import cn.servicehub.ticket.application.TicketDescriptionSanitizer;
import cn.servicehub.ticket.application.TicketService;
import cn.servicehub.ticket.application.TicketRelationService;
import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketQueue;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService ticketService;
    private final TicketRelationService ticketRelationService;
    private final TicketDescriptionSanitizer descriptionSanitizer;

    public TicketController(TicketService ticketService, TicketRelationService ticketRelationService, TicketDescriptionSanitizer descriptionSanitizer) {
        this.ticketService = ticketService;
        this.ticketRelationService = ticketRelationService;
        this.descriptionSanitizer = descriptionSanitizer;
    }

    @GetMapping
    TicketPageResponse list(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) TicketType type,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(name = "serviceCatalog", required = false) @Size(min = 1, max = 100) String serviceCatalog,
        @RequestParam(name = "requesterOrganization", required = false) @Size(min = 1, max = 100) String requesterOrganization,
        @RequestParam(required = false) java.time.LocalDate createdFrom,
        @RequestParam(required = false) java.time.LocalDate createdTo,
        @RequestParam(required = false) TicketQueue queue,
        @RequestParam(required=false)@Pattern(regexp="^[A-Z][A-Z0-9_-]{1,63}$")String teamQueueCode,
        @RequestParam(required = false) @Size(max = 4096) String cursor,
        @RequestParam(name = "q", required = false) @Size(min = 1, max = 100) String keyword) {
        return TicketPageResponse.from(ticketService.list(page, pageSize, status, type, priority, serviceCatalog,
            requesterOrganization, createdFrom, createdTo, keyword, queue,teamQueueCode, cursor));
    }

    @PostMapping
    ResponseEntity<TicketResponse> create(
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String idempotencyKey,
        @Valid @RequestBody TicketCreateRequest request) {
        TicketDescription description = descriptionSanitizer.sanitize(request.description(), request.descriptionFormat());
        CreateTicketResult result = ticketService.create(new TicketCreateCommand(request.serviceCatalogItemId(), request.serviceCatalogFormVersion(), request.type(),
            normalize(request.title()), description.plainText(), description.format(), description.sanitizedHtml(), request.structuredFields(), toTags(request.tags()),
            request.relatedConfigurationItemIds(), request.serviceSystemCode(), request.serviceSystemModuleCode()), idempotencyKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/api/v1/tickets/" + result.ticket().id()));
        if (result.replayed()) {
            headers.set("Idempotent-Replay", "true");
        }
        return new ResponseEntity<>(TicketResponse.from(result.ticket()), headers, HttpStatus.CREATED);
    }

    @GetMapping("/{ticketId}")
    TicketResponse get(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) {
        return TicketResponse.from(ticketService.get(ticketId));
    }

    @PatchMapping("/{ticketId}/description")
    TicketResponse updateDescription(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp = "^\"?[0-9]+\"?$") String ifMatch,
        @Valid @RequestBody TicketDescriptionUpdateRequest request) {
        return TicketResponse.from(ticketService.updateDescription(ticketId, Long.parseLong(ifMatch.replace("\"", "")), request.description(), request.descriptionFormat()));
    }

    @GetMapping("/{ticketId}/relations")
    List<TicketRelationResponse> relations(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) {
        return ticketRelationService.list(ticketId).stream().map(TicketRelationResponse::from).toList();
    }

    @PostMapping("/{ticketId}/relations")
    ResponseEntity<TicketRelationResponse> relate(
        @PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @Valid @RequestBody TicketRelationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TicketRelationResponse.from(ticketRelationService.create(ticketId, request.targetTicketId(), request.relationType())));
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("[\\t\\r\\n ]+", " ");
    }

    private static List<cn.servicehub.ticket.domain.TicketTag> toTags(List<TicketCreateRequest.TagRequest> tags) {
        return tags == null ? List.of() : tags.stream()
            .map(tag -> new cn.servicehub.ticket.domain.TicketTag(tag.name().trim(), tag.kind())).toList();
    }
}
