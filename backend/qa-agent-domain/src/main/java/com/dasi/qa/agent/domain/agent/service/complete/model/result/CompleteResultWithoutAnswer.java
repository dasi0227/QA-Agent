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
public class CompleteResultWithoutAnswer {

    @Description("复习知识笔记，必须围绕用户指定标准答案提炼")
    private String knowledgeNote;

    @Description("题目分类标签，从候选标签池选取 1-2 个，逗号分隔")
    private String moduleTag;

    @Description("题目难度，必须是 EASY / MEDIUM / HARD 之一")
    private String difficulty;

    @Description("资料证据是否足以支撑用户指定标准答案")
    private Boolean sourceReliable;

    @Description("来源切片 ID 列表")
    private List<String> sourceChunkIds;
}
