package cn.servicehub.catalog.web;

import cn.servicehub.catalog.application.CaseMatchResult;
import cn.servicehub.catalog.domain.CaseMatchCandidate;
import java.util.List;

public record RuleMatchResponse(String ruleEngine, List<Match> matches) {
    static RuleMatchResponse from(CaseMatchResult result) {
        return new RuleMatchResponse("DETERMINISTIC_RULES", result.candidates().stream().map(Match::from).toList());
    }
    public record Match(String ruleCode, List<String> matchedFacts, Suggestion suggestion) {
        static Match from(CaseMatchCandidate value) {
            return new Match("RULE-" + value.caseId().replaceAll("[^A-Za-z0-9_-]", "_"), value.matchedBy(),
                new Suggestion("KNOWLEDGE_ARTICLE", value.caseId(), value.title(), value.resolutionSummary(), "READ_AND_TRY"));
        }
    }
    public record Suggestion(String kind, String referenceId, String title, String summary, String action) { }
}
