package com.dasi.qa.agent.domain.agent.service.assist.model.context;

import com.dasi.qa.agent.domain.agent.model.vo.ChunkVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistContext {

    private String qaItemId;
    private String question;
    private String standardAnswer;
    private String knowledgeNote;
    private String moduleTag;
    private String difficulty;
    private Boolean sourceReliable;
    private String answerStyle;
    private List<ChunkVO> sourceChunks;
}
