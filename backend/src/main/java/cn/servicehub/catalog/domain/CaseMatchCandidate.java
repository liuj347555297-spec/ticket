package cn.servicehub.catalog.domain;

import java.util.List;

public record CaseMatchCandidate(String caseId, String title, String resolutionSummary, int score,
                                 List<String> matchedBy) {
    public CaseMatchCandidate {
        matchedBy = matchedBy == null ? List.of() : List.copyOf(matchedBy);
    }
}
