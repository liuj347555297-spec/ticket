package cn.servicehub.catalog.application;

import cn.servicehub.catalog.domain.CaseMatchCandidate;
import java.util.List;

public record CaseMatchResult(String matchRecordId, List<CaseMatchCandidate> candidates) {
    public CaseMatchResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
