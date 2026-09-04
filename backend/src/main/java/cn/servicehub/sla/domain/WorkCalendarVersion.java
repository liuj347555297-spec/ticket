package cn.servicehub.sla.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** Immutable calendar version. Tickets retain key@version rather than a mutable calendar name. */
public record WorkCalendarVersion(String key, int version, String name, String timeZone, boolean allDay,
                                  Set<DayOfWeek> workingWeekdays, LocalTime businessStart, LocalTime businessEnd,
                                  Set<LocalDate> holidays) {
    public WorkCalendarVersion {
        workingWeekdays = workingWeekdays == null ? Set.of() : Set.copyOf(workingWeekdays);
        holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}") || version < 1) throw new IllegalArgumentException("Invalid work calendar identity");
        if (!allDay && (workingWeekdays.isEmpty() || businessStart == null || businessEnd == null || !businessEnd.isAfter(businessStart))) throw new IllegalArgumentException("A business calendar must have an ordered working period");
    }
    public String snapshotKey() { return key + "@" + version; }
}
