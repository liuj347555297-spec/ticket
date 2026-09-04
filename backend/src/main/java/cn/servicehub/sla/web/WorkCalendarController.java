package cn.servicehub.sla.web;

import cn.servicehub.sla.application.WorkCalendarService;
import cn.servicehub.sla.domain.WorkCalendarVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sla/calendars")
public class WorkCalendarController {
    private final WorkCalendarService calendars;
    public WorkCalendarController(WorkCalendarService calendars) { this.calendars=calendars; }
    @GetMapping java.util.List<WorkCalendarVersion> list() { return calendars.list(); }
    @PostMapping("/versions") @ResponseStatus(HttpStatus.CREATED) WorkCalendarVersion publish(@Valid @RequestBody CalendarRequest r) { return calendars.publish(new WorkCalendarService.CalendarCommand(r.key(),r.name(),r.timeZone(),r.allDay(),r.workingWeekdays(),r.businessStart(),r.businessEnd(),r.holidays())); }
    record CalendarRequest(@NotBlank @Pattern(regexp="^[A-Za-z0-9_-]{1,64}$") String key,@NotBlank String name,@NotBlank String timeZone,boolean allDay,Set<DayOfWeek> workingWeekdays,LocalTime businessStart,LocalTime businessEnd,Set<LocalDate> holidays) { }
}
