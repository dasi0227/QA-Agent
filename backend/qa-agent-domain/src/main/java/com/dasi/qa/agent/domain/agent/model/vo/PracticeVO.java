package com.dasi.qa.agent.domain.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单题反馈 DB 数据快照，用于组装各阶段上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeVO {

    private String sessionItemId;
    private String sessionId;
    private String qaItemId;
    private String question;
    private String standardAnswer;
    private String knowledgeNote;
    private String tip;
    private String answerStyle;
    private String feedbackStyle;
    private List<ChunkVO> sourceChunks;
}
