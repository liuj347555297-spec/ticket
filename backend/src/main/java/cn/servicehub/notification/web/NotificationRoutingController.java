package cn.servicehub.notification.web;

import cn.servicehub.notification.application.NotificationRoutingPreview;
import cn.servicehub.notification.application.NotificationRoutingService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/notification-routing-rules")
public class NotificationRoutingController {
    private final NotificationRoutingService routing;
    public NotificationRoutingController(NotificationRoutingService routing) { this.routing = routing; }

    @GetMapping("/preview")
    NotificationRoutingPreview preview(
        @RequestParam @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String organizationIamOrganizationId,
        @RequestParam @Pattern(regexp = "^(TICKET_CREATED|TICKET_ASSIGNED|TICKET_STATUS_CHANGED|WORKFLOW_TASK_CREATED|WORKFLOW_TASK_REMINDER|SLA_BREACH_RISK|SLA_BREACHED|SYSTEM_ANNOUNCEMENT|INTEGRATION_ALERT)$") String event) {
        return routing.previewForCurrentUser(organizationIamOrganizationId, event);
    }
}
