package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.InterviewInsights;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult.PlanItem;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

    public List<InterviewInsights> search(String company, String role, PlanItem planItem) {
        List<String> topics = planItem.getRetrievalQueries() == null ? List.of() : planItem.getRetrievalQueries().stream()
                .filter(StringUtils::hasText)
                .toList();
        if (topics.isEmpty()) {
            topics = List.of(planItem.getModule());
        }
        List<InterviewInsights> results = new ArrayList<>();
        for (String topic : topics) {
            if (!StringUtils.hasText(topic)) {
                continue;
            }
            String query = String.format("搜索 %s %s %s %s 面试面经", company, role, planItem.getModule(), topic.trim());
            ChatResponse response = webSearchModel.chat(
                    SystemMessage.from(promptUtil.loadWebSearchPrompt()),
                    UserMessage.from(query));
            try {
                results.add(jsonUtil.parseJsonObject(response.aiMessage().text(), InterviewInsights.class));
            } catch (Exception ignored) {
            }
        }
        return results;
    }
}
