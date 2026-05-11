package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.InterviewInsights;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanItem;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网面经预搜器，在 DraftAgent 调用前一次性完成 Web 检索，不再作为 LLM tool 由 Agent 按需调用。
 */
@Component
public class WebEvidenceProvider {

    private final ChatModel webSearchModel;
    private final IPromptUtil promptUtil;

    public WebEvidenceProvider(@Qualifier("webSearchModel") ChatModel webSearchModel, IPromptUtil promptUtil) {
        this.webSearchModel = webSearchModel;
        this.promptUtil = promptUtil;
    }

    public List<InterviewInsights> search(String company, String role, PlanItem planItem) {
        String focusTopics = planItem.getFocusTopics();
        List<String> topics = (focusTopics == null || focusTopics.isBlank())
                ? List.of(planItem.getModuleTag())
                : List.of(focusTopics.split(","));
        List<InterviewInsights> results = new ArrayList<>();
        for (String topic : topics) {
            topic = topic.trim();
            if (topic.isEmpty()) {
                continue;
            }
            String query = String.format("搜索 %s %s %s 面试面经", company, role, topic);
            ChatResponse response = webSearchModel.chat(
                    SystemMessage.from(promptUtil.loadWebSearchPrompt()),
                    UserMessage.from(query));
            try {
                results.add(JSON.parseObject(response.aiMessage().text(), InterviewInsights.class));
            } catch (Exception ignored) {
            }
        }
        return results;
    }
}
