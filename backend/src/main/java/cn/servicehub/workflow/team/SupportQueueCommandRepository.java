package cn.servicehub.workflow.team;

import java.util.Optional;

public interface SupportQueueCommandRepository {
 Optional<SupportQueueCommandRecord> find(String actor,String key);
 boolean reserve(SupportQueueCommandRecord record);
 boolean takeover(String actor,String key,long version,String oldOwner,String newOwner,java.time.Instant leaseExpiresAt);
 boolean completeByReconciliation(String actor,String key,long version,String oldOwner,int responseStatus,String responseSummary,String resultType,String resultId);
 boolean heartbeat(String actor,String key,long version,String owner,java.time.Instant leaseExpiresAt);
 boolean reconciliationRequired(String actor,String key,long version,String owner,String errorCode);
 boolean resolveReconciliation(String actor,String key,long version,String decision,String resultType,String resultId,String approver);
 void complete(String actor,String key,long version,String owner,int responseStatus,String responseSummary,String resultType,String resultId);
 void fail(String actor,String key,long version,boolean retryable,String errorCode);
}
