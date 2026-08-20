package cn.servicehub.catalog.web;

import cn.servicehub.catalog.application.CaseMatchResult;
import cn.servicehub.catalog.domain.CaseMatchCandidate;
import java.util.List;

public record CaseMatchResponse(String matchRecordId, List<Candidate> candidates) {
    static CaseMatchResponse from(CaseMatchResult result) {
        return new CaseMatchResponse(result.matchRecordId(), result.candidates().stream().map(Candidate::from).toList());
    }
    public record Candidate(String caseId, String title, String resolutionSummary, int score, List<String> matchedBy) {
        static Candidate from(CaseMatchCandidate value) {
            return new Candidate(value.caseId(), value.title(), value.resolutionSummary(), value.score(), value.matchedBy());
        }
    }
}
