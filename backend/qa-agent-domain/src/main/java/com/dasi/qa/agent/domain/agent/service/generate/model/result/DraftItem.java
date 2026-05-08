package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import com.dasi.qa.agent.domain.agent.shared.enumeration.Difficulty;
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
public class DraftItem {

        @Description("面试场景的问题表述，口语化提问方式")
        private String question;

        @Description("知识笔记，供学习回顾用，包含关键概念和记忆要点")
        private String knowledgeNote;

        @Description("标准面试回答，逻辑清晰、有分层结构")
        private String answer;

        @Description("所属模块标签")
        private String moduleTag;

        @Description("题目难度等级")
        private Difficulty difficulty;

        @Description("资料冲突或内容不完整的提示，无则留空")
        private String conflictTip;

        @Description("引用的证据块 chunk_id 列表")
        private List<String> sourceChunkIds;
}
