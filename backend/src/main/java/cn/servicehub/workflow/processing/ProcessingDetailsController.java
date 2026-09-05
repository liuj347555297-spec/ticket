package cn.servicehub.workflow.processing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/tickets/{ticketId}/processing-details")
public class ProcessingDetailsController {
    private final ProcessingDetailsService service;
    public ProcessingDetailsController(ProcessingDetailsService service) { this.service = service; }

    @GetMapping
    ProcessingDetailsResponse get(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) {
        return service.get(ticketId);
    }

    @PutMapping
    ProcessingDetailsResponse save(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
                                   @RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,
                                   @Valid @RequestBody Request request) {
        return service.save(ticketId, Long.parseLong(ifMatch.replace("\"", "")), new ProcessingDetailsCommand(
            request.eventSource(), request.proposingOrganization(), request.onSiteSupportRequired(), request.causeCategory(),
            request.processingDescription(), request.resolutionDescription(), request.thirdPartyHandled(), request.currentProgress()));
    }

    public record Request(
        @Pattern(regexp="^(PHONE|EMAIL|MONITORING_ALERT|ON_SITE_FEEDBACK|OTHER)$") String eventSource,
        @Size(max=160) String proposingOrganization,
        Boolean onSiteSupportRequired,
        @Pattern(regexp="^(HARDWARE|SOFTWARE_DEFECT|CONFIGURATION|NETWORK|ACCESS_CONTROL|DATA|USER_OPERATION|EXTERNAL_DEPENDENCY|UNDER_INVESTIGATION)$") String causeCategory,
        @Size(max=4000) String processingDescription,
        @Size(max=4000) String resolutionDescription,
        Boolean thirdPartyHandled,
        @Size(max=1000) String currentProgress) { }
}
