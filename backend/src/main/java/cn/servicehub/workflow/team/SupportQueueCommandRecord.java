package cn.servicehub.workflow.team;

import java.time.Instant;

public record SupportQueueCommandRecord(String actorIamUserId,String idempotencyKey,String operation,String resourceType,String resourceCode,String requestFingerprint,String status,Integer responseStatus,String responseSummary,String errorCode,Instant createdAt,Instant completedAt,Instant expiresAt,long version,String leaseOwner,Instant leaseExpiresAt,int attemptCount,String keyVersion,String resultResourceType,String resultResourceId,Instant heartbeatAt) { }
