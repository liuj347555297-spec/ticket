package cn.servicehub.sla.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.sla.domain.WorkCalendarRepository;
import cn.servicehub.sla.domain.WorkCalendarVersion;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendar versions are append-only. SLA targets use key@version and never resolve a newer version. */
@Service
public class WorkCalendarService {
    private static final Set<String> MANAGER_ROLES = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_SLA_MANAGER", "ROLE_SERVICE_MANAGER");
    private final WorkCalendarRepository calendars; private final CurrentUserProvider users; private final AuditEventPublisher audit;
    public WorkCalendarService(WorkCalendarRepository calendars, CurrentUserProvider users, AuditEventPublisher audit) { this.calendars=calendars; this.users=users; this.audit=audit; }
    public WorkCalendarVersion current(String key) { return calendars.findCurrent(key).orElseThrow(() -> new IllegalArgumentException("Active SLA calendar not found")); }
    public WorkCalendarVersion snapshot(String snapshotKey) {
        String[] parts=snapshotKey.split("@",-1); if(parts.length!=2) throw new IllegalArgumentException("Invalid SLA calendar snapshot");
        try { return calendars.findVersion(parts[0],Integer.parseInt(parts[1])).orElseThrow(() -> new IllegalStateException("SLA calendar snapshot is unavailable")); } catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid SLA calendar snapshot"); }
    }
    public java.util.List<WorkCalendarVersion> list() { requireManager(); return calendars.findCurrentActive(); }
    @Transactional public WorkCalendarVersion publish(CalendarCommand command) {
        CurrentUser user=requireManager(); WorkCalendarVersion prior=calendars.findCurrent(command.key()).orElse(null);
        WorkCalendarVersion next=new WorkCalendarVersion(clean(command.key(),64),prior==null?1:prior.version()+1,clean(command.name(),120),clean(command.timeZone(),64),command.allDay(),command.weekdays(),command.businessStart(),command.businessEnd(),command.holidays());
        WorkCalendarVersion saved=calendars.createVersion(next);
        audit.publish(new AuditEvent(Instant.now(),"system",user.iamUserId(),"SLA_CALENDAR_VERSION_PUBLISHED","sla-calendar",saved.snapshotKey(),Map.of("previousVersion",String.valueOf(prior==null?0:prior.version()))));
        return saved;
    }
    public Instant addBusinessMinutes(String snapshotKey, Instant start, long minutes) { return addBusinessSeconds(snapshotKey,start,Math.multiplyExact(minutes,60)); }
    public Instant addBusinessSeconds(String snapshotKey, Instant start, long seconds) { return addBusinessSeconds(snapshot(snapshotKey), start, seconds); }
    public long businessSecondsBetween(String snapshotKey, Instant from, Instant to) { if(!to.isAfter(from)) return 0; WorkCalendarVersion c=snapshot(snapshotKey); if(c.allDay()) return Duration.between(from,to).toSeconds(); long total=0; ZonedDateTime cursor=from.atZone(ZoneId.of(c.timeZone())); ZonedDateTime end=to.atZone(ZoneId.of(c.timeZone())); while(cursor.toLocalDate().isBefore(end.toLocalDate())||cursor.toLocalDate().equals(end.toLocalDate())) { LocalDate day=cursor.toLocalDate(); if(isWorking(c,day)) { ZonedDateTime open=ZonedDateTime.of(day,c.businessStart(),cursor.getZone()); ZonedDateTime close=ZonedDateTime.of(day,c.businessEnd(),cursor.getZone()); ZonedDateTime lo=cursor.isAfter(open)?cursor:open, hi=end.isBefore(close)?end:close; if(hi.isAfter(lo)) total+=Duration.between(lo,hi).toSeconds(); } cursor=ZonedDateTime.of(day.plusDays(1),LocalTime.MIDNIGHT,cursor.getZone()); } return total; }
    private Instant addBusinessSeconds(WorkCalendarVersion c, Instant start, long seconds) { if(c.allDay()) return start.plusSeconds(seconds); ZonedDateTime point=start.atZone(ZoneId.of(c.timeZone())); long remaining=seconds; while(remaining>0) { LocalDate day=point.toLocalDate(); if(!isWorking(c,day) || !point.toLocalTime().isBefore(c.businessEnd())) { point=ZonedDateTime.of(day.plusDays(1),LocalTime.MIDNIGHT,point.getZone()); continue; } ZonedDateTime open=ZonedDateTime.of(day,c.businessStart(),point.getZone()); if(point.isBefore(open)) point=open; ZonedDateTime close=ZonedDateTime.of(day,c.businessEnd(),point.getZone()); long available=Duration.between(point,close).toSeconds(); if(remaining<=available) return point.plusSeconds(remaining).toInstant(); remaining-=available; point=ZonedDateTime.of(day.plusDays(1),LocalTime.MIDNIGHT,point.getZone()); } return point.toInstant(); }
    private boolean isWorking(WorkCalendarVersion c, LocalDate day) { return !c.holidays().contains(day) && c.workingWeekdays().contains(day.getDayOfWeek()); }
    private CurrentUser requireManager() { CurrentUser user=users.requireCurrentUser(); if(user.authorities().stream().noneMatch(MANAGER_ROLES::contains)) throw new AccessDeniedException("SLA calendar administration is not authorized"); return user; }
    private static String clean(String value,int max) { if(value==null||value.isBlank()||value.trim().length()>max) throw new IllegalArgumentException("Calendar value is invalid"); return value.trim(); }
    public record CalendarCommand(String key,String name,String timeZone,boolean allDay,Set<DayOfWeek> weekdays,LocalTime businessStart,LocalTime businessEnd,Set<LocalDate> holidays) { }
}
