package com.dasi.qa.agent.domain.agent.service.generate.model.result;

public record RevisionItem(
        int itemIndex,
        DraftItem draftItem,
        String reason,
        String revisionSuggestion
) {
}
