package com.dasi.qa.agent.domain.agent.service.complete.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteResult {

    @Description("标准面试回答，逻辑清晰、有分层结构")
    private String answer;

    @Description("复习知识笔记，包含概念、机制、关键步骤和容易混淆点")
    private String knowledgeNote;

    @Description("题目分类标签，从候选标签池选取 1-2 个，逗号分隔")
    private String moduleTag;

    @Description("题目难度，必须是 EASY / MEDIUM / HARD 之一")
    private String difficulty;

    @Description("资料证据对当前题目是否可靠")
    private Boolean sourceReliable;

    @Description("来源切片 ID 列表")
    private List<String> sourceChunkIds;
}
