package com.dasi.qa.agent.domain.agent.service.generate.model.result;

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
public class DraftResult {

    @Description("面试场景的问题表述，口语化提问方式")
    private String question;

    @Description("标准面试回答，逻辑清晰、有分层结构")
    private String answer;

    @Description("知识笔记，供学习回顾用，包含关键概念和记忆要点")
    private String knowledgeNote;

    @Description("题目分类标签，从候选标签池选取 1-2 个，逗号分隔")
    private String tag;

    @Description("题目难度，必须是 EASY / MEDIUM / HARD 之一")
    private String difficulty;

    @Description("无冲突时为从证据块引用的原文句子，有冲突时写明具体问题")
    private String tip;

    @Description("来源切片 ID 列表")
    private List<String> sourceChunkIds;

}
