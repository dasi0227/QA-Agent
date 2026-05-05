package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.types.enums.AgentType;
import com.dasi.qa.agent.types.model.response.document.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvidenceClipper {

    private static final int TOP_GENERATION = 10;
    private static final int TOP_FEEDBACK = 3;
    private static final int TOP_SCORING = 5;
    private static final int TOP_DEFAULT = 10;

    public List<SearchResult> clip(List<SearchResult> results, AgentType agentType) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        int limit;
        if (agentType == null) {
            limit = TOP_DEFAULT;
        } else {
            limit = switch (agentType) {
                case GENERATION -> TOP_GENERATION;
                case FEEDBACK -> TOP_FEEDBACK;
                case SCORING -> TOP_SCORING;
            };
        }
        return results.size() <= limit ? results : results.subList(0, limit);
    }
}
