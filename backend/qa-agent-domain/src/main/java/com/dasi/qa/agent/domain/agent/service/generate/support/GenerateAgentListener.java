package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStage;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStatus;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateAbortedException;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class GenerateAgentListener implements AgentListener {

    private final String taskId;
    private final EventPublisher publisher;
    private final AtomicInteger totalTokens;
    private final ChatModel supervisorChatModel;
    private final AtomicInteger lastPublishedTokens = new AtomicInteger(0);

    public GenerateAgentListener(String taskId,
                                 EventPublisher publisher,
                                 AtomicInteger totalTokens,
                                 ChatModel supervisorChatModel) {
        this.taskId = taskId;
        this.publisher = publisher;
        this.totalTokens = totalTokens;
        this.supervisorChatModel = supervisorChatModel;
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        totalTokens.addAndGet(tokens(response.chatResponse()));
        GenerationStage stage = stageFromAgentName(response.agentName());
        String summary = summarizeStage(stage, response.output());
        int total = totalTokens.get();
        int current = total - lastPublishedTokens.getAndSet(total);
        publisher.publishEvent(stage, GenerationStatus.PROCESSING, summary, current);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        if (error.error() != null && isAborted(error.error())) {
            return;
        }
        GenerationStage stage = stageFromAgentName(error.agentName());
        String message = error.error() == null ? "Agent 调用失败" : error.error().getMessage();
        log.warn("Agent invocation failed: taskId={}, agent={}, message={}", taskId, error.agentName(), message);
        publisher.publishEvent(stage, GenerationStatus.PROCESSING, stage.name() + " 阶段出现可恢复错误：" + safe(message), 0);
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private String summarizeStage(GenerationStage stage, Object output) {
        try {
            ChatResponse response = chat(supervisorChatModel,
                    loadPrompt("prompt/supervisor-summary.txt"),
                    "阶段：" + stage.name() + "\n产出：" + JSON.toJSONString(output));
            return response.aiMessage().text();
        } catch (Exception e) {
            return stage.name() + " 阶段完成";
        }
    }

    private ChatResponse chat(ChatModel model, String systemPrompt, String userPrompt) {
        ChatResponse response = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt));
        totalTokens.addAndGet(tokens(response));
        return response;
    }

    private int tokens(ChatResponse response) {
        if (response == null) {
            return 0;
        }
        TokenUsage usage = response.tokenUsage();
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }

    private String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isAborted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GenerateAbortedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private GenerationStage stageFromAgentName(String agentName) {
        if ("DECIDE".equals(agentName) || "DECIDE_GATE".equals(agentName)) {
            return GenerationStage.DECIDING;
        }
        if ("PLANNER".equals(agentName)) {
            return GenerationStage.PLANNING;
        }
        if ("DRAFTER".equals(agentName)) {
            return GenerationStage.CREATING;
        }
        if ("EVALUATOR".equals(agentName) || "AMENDER".equals(agentName) || "VALIDATOR".equals(agentName)) {
            return GenerationStage.VALIDATING;
        }
        if ("SUMMARIZER".equals(agentName)) {
            return GenerationStage.SUMMARIZING;
        }
        return GenerationStage.CREATING;
    }
}
