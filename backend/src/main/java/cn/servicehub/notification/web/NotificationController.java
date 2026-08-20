package cn.servicehub.notification.web;

import cn.servicehub.notification.application.NotificationService;
import cn.servicehub.notification.application.NotificationPage;
import cn.servicehub.notification.application.NotificationDeliveryPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping NotificationPageResponse mine(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize, @RequestParam(required = false) @Pattern(regexp = "^(READ|UNREAD)$") String readState, @RequestParam(required = false) @Pattern(regexp = "^(TICKET|WORKFLOW|SLA|SYSTEM|INTEGRATION)$") String category) { NotificationPage result = notifications.myNotifications(page, pageSize, readState, category); return new NotificationPageResponse(result.items().stream().map(NotificationResponse::from).toList(), result.page(), result.pageSize(), result.total()); }
    @PatchMapping("/{notificationId}/read")
    NotificationResponse read(@PathVariable @Pattern(regexp = "^NTF-[A-Za-z0-9_-]{8,64}$") String notificationId, @RequestHeader("Idempotency-Key") @Pattern(regexp = "^[0-9a-fA-F-]{8,64}$") String ignoredIdempotencyKey, @Valid @RequestBody NotificationMarkReadRequest request) { return NotificationResponse.from(notifications.markRead(notificationId, request.version())); }
    @GetMapping("/{notificationId}/deliveries")
    NotificationDeliveryPageResponse deliveries(@PathVariable @Pattern(regexp = "^NTF-[A-Za-z0-9_-]{8,64}$") String notificationId, @RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) { NotificationDeliveryPage result = notifications.myDeliveries(notificationId, page, pageSize); return NotificationDeliveryPageResponse.from(result); }
    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount() { return new UnreadCountResponse(notifications.unreadCount()); }
}
