package cn.servicehub.knowledge.domain;

import java.time.Instant;

/** Metadata-only work item; it must never contain a knowledge or source-ticket body. */
public record KnowledgeReviewCandidate(String documentId, String title, String reasonCode,
                                       Instant reviewDueAt, String reviewOwnerIamUserId,
                                       long helpfulCount, long notHelpfulCount) { }
