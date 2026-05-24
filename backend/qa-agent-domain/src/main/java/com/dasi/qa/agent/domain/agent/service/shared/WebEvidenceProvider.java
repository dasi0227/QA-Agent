package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 联网面经预搜器
 */
@Component
public class WebEvidenceProvider {

    private final ChatModel webSearchModel;
    private final IPromptUtil promptUtil;
    private final IJsonUtil jsonUtil;

    public WebEvidenceProvider(@Qualifier("webSearchModel") ChatModel webSearchModel,
                               IPromptUtil promptUtil,
                               IJsonUtil jsonUtil) {
        this.webSearchModel = webSearchModel;
        this.promptUtil = promptUtil;
        this.jsonUtil = jsonUtil;
    }

    public WebEvidenceItem search(String query) {
        ChatResponse response = webSearchModel.chat(
                SystemMessage.from(promptUtil.loadWebSearchPrompt()),
                UserMessage.from(query));
        try {
            return jsonUtil.parseJsonObject(response.aiMessage().text(), WebEvidenceItem.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebEvidenceItem {

        @Description("高频考点")
        private List<String> highFrequencyTopics;

        @Description("典型面试题示例")
        private List<String> typicalQuestions;

        @Description("面试官侧重点")
        private List<String> interviewerFocus;

    }
}
