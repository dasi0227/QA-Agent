package com.dasi.qa.agent.domain.agent.model;

public record RevisionItem(
        int itemIndex,
        DraftItem draftItem,
        String reason,
        String revisionSuggestion
) {
}
