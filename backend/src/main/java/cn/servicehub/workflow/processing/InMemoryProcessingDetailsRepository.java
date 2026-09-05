package cn.servicehub.workflow.processing;

import cn.servicehub.workflow.application.WorkflowConflictException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryProcessingDetailsRepository implements ProcessingDetailsRepository {
    private final ConcurrentHashMap<String, ProcessingDetails> rows = new ConcurrentHashMap<>();

    @Override public Optional<ProcessingDetails> findByTicketId(String ticketId) { return Optional.ofNullable(rows.get(ticketId)); }

    @Override public synchronized ProcessingDetails save(ProcessingDetails details, long expectedVersion) {
        ProcessingDetails current = rows.get(details.ticketId());
        if ((current == null ? 0 : current.version()) != expectedVersion) throw new WorkflowConflictException();
        rows.put(details.ticketId(), details);
        return details;
    }
}
