package com.dasi.qa.agent.domain.memory.model.vo;

import com.dasi.qa.agent.domain.memory.model.dto.Memory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestContext {

    private String sessionId;
    private String userId;
    private String qaSetId;
    private String qaSetTitle;
    private Integer totalQuestions;
    private Integer score;
    private String accuracy;
    private Integer perfectCount;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
    private List<IngestItem> items;
    private List<Memory> existingMemories;
}
