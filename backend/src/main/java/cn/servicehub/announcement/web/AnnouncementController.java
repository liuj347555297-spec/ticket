package cn.servicehub.announcement.web;

import cn.servicehub.announcement.application.AnnouncementService;
import cn.servicehub.announcement.domain.Announcement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService service;
    public AnnouncementController(AnnouncementService service) { this.service = service; }
    @GetMapping public List<AnnouncementResponse> mine(@RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) { return service.myActiveAnnouncements(limit).stream().map(AnnouncementResponse::from).toList(); }
    @PostMapping public AnnouncementResponse create(@Valid @RequestBody CreateAnnouncementRequest request) { return AnnouncementResponse.from(service.create(request.title(), request.body(), request.audienceScope(), request.targetOrganizationIamId(), request.pinned(), request.effectiveFrom(), request.effectiveUntil())); }
    public record CreateAnnouncementRequest(String title, String body, Announcement.AudienceScope audienceScope, String targetOrganizationIamId, boolean pinned, Instant effectiveFrom, Instant effectiveUntil) { }
    public record AnnouncementResponse(String id, String title, String body, Announcement.AudienceScope audienceScope, boolean pinned, Instant effectiveFrom, Instant effectiveUntil) { static AnnouncementResponse from(Announcement value) { return new AnnouncementResponse(value.id(), value.title(), value.body(), value.audienceScope(), value.pinned(), value.effectiveFrom(), value.effectiveUntil()); } }
}
