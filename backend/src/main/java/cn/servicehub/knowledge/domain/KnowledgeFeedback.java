package cn.servicehub.knowledge.domain;

import java.time.Instant;

/** A single, structured usefulness vote. Free-form comments are deliberately not collected here. */
public record KnowledgeFeedback(String documentId, String versionId, String voterIamUserId,
                                FeedbackValue value, String reasonCode, Instant createdAt) {
    public enum FeedbackValue { HELPFUL, NOT_HELPFUL }
}
