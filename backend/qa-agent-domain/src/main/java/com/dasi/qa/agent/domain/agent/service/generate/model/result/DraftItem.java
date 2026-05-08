package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import com.dasi.qa.agent.domain.agent.shared.enumeration.Difficulty;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record DraftItem(
        @Description("面试场景的问题表述，口语化提问方式") String question,
        @Description("知识笔记，供学习回顾用，包含关键概念和记忆要点") String knowledgeNote,
        @Description("标准面试回答，逻辑清晰、有分层结构") String answer,
        @Description("所属模块标签") String moduleTag,
        @Description("题目难度等级") Difficulty difficulty,
        @Description("资料冲突或内容不完整的提示，无则留空") String conflictTip,
        @Description("引用的证据块 chunk_id 列表") List<String> sourceChunkIds
) {
}
