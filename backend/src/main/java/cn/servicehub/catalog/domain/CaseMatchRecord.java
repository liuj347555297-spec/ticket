package cn.servicehub.catalog.domain;

import java.time.Instant;
import java.util.List;

public record CaseMatchRecord(String id, String actorIamUserId, String catalogItemId, String criteriaHash,
                              List<String> matchedCaseIds, Instant matchedAt) {
    public CaseMatchRecord {
        matchedCaseIds = matchedCaseIds == null ? List.of() : List.copyOf(matchedCaseIds);
    }
}
