package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftItem {

        @Description("面试场景的问题表述，口语化提问方式")
        private String question;

        @Description("标准面试回答，逻辑清晰、有分层结构")
        private String answer;

        @Description("知识笔记，供学习回顾用，包含关键概念和记忆要点")
        private String knowledgeNote;

        @Description("题目分类标签，从候选标签池选取")
        private String tag;

        @Description("题目难度，必须是 EASY / MEDIUM / HARD 之一")
        private String difficulty;

        @Description("资料冲突或内容不完整的提示，无则留空")
        private String conflictTip;

        @Description("从证据块中引用的原文句子")
        private String evidence;

}
