package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.WorkCalendarRepository;
import cn.servicehub.sla.domain.WorkCalendarVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlWorkCalendarRepository implements WorkCalendarRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlWorkCalendarRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public Optional<WorkCalendarVersion> findCurrent(String key) { return jdbc.query("SELECT c.calendar_key,c.calendar_name,c.current_version,v.* FROM sla_work_calendar c JOIN sla_work_calendar_version v ON v.calendar_key=c.calendar_key AND v.version=c.current_version WHERE c.calendar_key=? AND c.active=TRUE", (rs,n) -> map(rs), key).stream().findFirst(); }
    public Optional<WorkCalendarVersion> findVersion(String key, int version) { return jdbc.query("SELECT c.calendar_key,c.calendar_name,c.current_version,v.* FROM sla_work_calendar c JOIN sla_work_calendar_version v ON v.calendar_key=c.calendar_key WHERE v.calendar_key=? AND v.version=?", (rs,n) -> map(rs), key, version).stream().findFirst(); }
    public List<WorkCalendarVersion> findCurrentActive() { return jdbc.query("SELECT c.calendar_key,c.calendar_name,c.current_version,v.* FROM sla_work_calendar c JOIN sla_work_calendar_version v ON v.calendar_key=c.calendar_key AND v.version=c.current_version WHERE c.active=TRUE ORDER BY c.calendar_key", (rs,n) -> map(rs)); }
    public WorkCalendarVersion createVersion(WorkCalendarVersion v) {
        int updated;
        if (v.version() == 1) updated = jdbc.update("INSERT INTO sla_work_calendar (calendar_key,calendar_name,current_version,active,updated_at) VALUES (?,?,?,TRUE,?)", v.key(), v.name(), v.version(), Timestamp.from(java.time.Instant.now()));
        else updated = jdbc.update("UPDATE sla_work_calendar SET calendar_name=?,current_version=?,updated_at=? WHERE calendar_key=? AND current_version=?", v.name(), v.version(), Timestamp.from(java.time.Instant.now()), v.key(), v.version()-1);
        if (updated != 1) throw new IllegalStateException("Calendar version conflict");
        try { jdbc.update("INSERT INTO sla_work_calendar_version (calendar_key,version,time_zone,all_day,working_weekdays,business_start,business_end,holiday_dates,created_by_iam_user_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", v.key(),v.version(),v.timeZone(),v.allDay(),json.writeValueAsString(v.workingWeekdays().stream().map(Enum::name).toList()),v.businessStart()==null?null:Time.valueOf(v.businessStart()),v.businessEnd()==null?null:Time.valueOf(v.businessEnd()),json.writeValueAsString(v.holidays().stream().map(LocalDate::toString).toList()),"managed-by-service",Timestamp.from(java.time.Instant.now())); } catch (Exception e) { throw new IllegalStateException("Cannot persist calendar snapshot",e); }
        return v;
    }
    private WorkCalendarVersion map(java.sql.ResultSet rs) throws java.sql.SQLException { try { Set<DayOfWeek> weekdays=json.readValue(rs.getString("working_weekdays"),new TypeReference<List<String>>(){}).stream().map(DayOfWeek::valueOf).collect(java.util.stream.Collectors.toSet()); Set<LocalDate> holidays=json.readValue(rs.getString("holiday_dates"),new TypeReference<List<String>>(){}).stream().map(LocalDate::parse).collect(java.util.stream.Collectors.toSet()); Time start=rs.getTime("business_start"), end=rs.getTime("business_end"); return new WorkCalendarVersion(rs.getString("calendar_key"),rs.getInt("version"),rs.getString("calendar_name"),rs.getString("time_zone"),rs.getBoolean("all_day"),weekdays,start==null?null:start.toLocalTime(),end==null?null:end.toLocalTime(),holidays); } catch(Exception e) { throw new java.sql.SQLException("Invalid work calendar snapshot",e); } }
}
