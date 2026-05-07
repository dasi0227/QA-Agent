package com.dasi.qa.agent.domain.agent.shared;

public record RevisionItem(
        int itemIndex,
        DraftItem draftItem,
        String reason,
        String revisionSuggestion
) {
}
