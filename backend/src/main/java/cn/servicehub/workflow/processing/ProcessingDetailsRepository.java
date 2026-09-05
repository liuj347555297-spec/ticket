package cn.servicehub.workflow.processing;

import java.util.Optional;

public interface ProcessingDetailsRepository {
    Optional<ProcessingDetails> findByTicketId(String ticketId);
    ProcessingDetails save(ProcessingDetails details, long expectedVersion);
}
