package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionItem {

    private int itemIndex;
    private DraftItem draftItem;
    private String reason;
    private String revisionSuggestion;
}
