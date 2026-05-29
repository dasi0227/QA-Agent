package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.service.shared.EventPublisher;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 阶段总结器，在每个 Agent 调用成功后生成可读的阶段性进度消息并推送 SSE。
 */
@Slf4j
@Getter
public class GenerateSupervisor {

    private final String taskId;
    private final IPromptUtil promptUtil;
    private final ChatModel chatModel;
    private final EventPublisher eventPublisher;
    private final AtomicInteger totalTokens;

    public GenerateSupervisor(String taskId,
                              IPromptUtil promptUtil,
                              ChatModel chatModel,
                              EventPublisher eventPublisher,
                              AtomicInteger totalTokens) {
        this.taskId = taskId;
        this.promptUtil = promptUtil;
        this.chatModel = chatModel;
        this.eventPublisher = eventPublisher;
        this.totalTokens = totalTokens;
    }

    /**
     * 调用 Supervisor LLM 生成阶段总结，写 DB message 并推 SSE。
     */
    public void doSupervise(GeneratePhase phase, String reference) {
        String message;
        try {
            String systemPrompt = promptUtil.loadSupervisorPrompt();
            String userPrompt = "阶段：" + phase.getGenerateStage() + "\n产出：" + reference;
            ChatResponse response = chatModel.chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );
            message = response.aiMessage().text();
            if (message == null || message.isBlank()) {
                message = phase.getGenerateStage() + " 阶段完成";
            }
        } catch (Exception e) {
            message = phase.getGenerateStage() + " 阶段完成";
        }
        log.info("【生成问答集】Agent调用成功: agent={}, taskId={}, message={}", phase.getAgentName(), taskId, message);
        eventPublisher.publishEvent(phase, GenerateStatus.PROCESSING, message);
    }

}
