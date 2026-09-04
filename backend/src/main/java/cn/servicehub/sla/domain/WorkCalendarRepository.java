package cn.servicehub.sla.domain;

import java.util.List;
import java.util.Optional;

public interface WorkCalendarRepository {
    Optional<WorkCalendarVersion> findCurrent(String key);
    Optional<WorkCalendarVersion> findVersion(String key, int version);
    List<WorkCalendarVersion> findCurrentActive();
    WorkCalendarVersion createVersion(WorkCalendarVersion version);
}
