package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * GenerateAgent 的运行监听器，负责在每个 Agent 调用后做统一的后置处理
 */
@Slf4j
public class GenerateAgentListener implements AgentListener {

    private final String taskId;
    private final IPromptUtil promptUtil;
    private final EventPublisher eventPublisher;
    private final AtomicInteger totalTokens;
    private final ChatModel supervisorChatModel;
    private final AtomicInteger lastPublishedTokens = new AtomicInteger(0);

    public GenerateAgentListener(String taskId,
                                 IPromptUtil promptUtil,
                                 EventPublisher eventPublisher,
                                 AtomicInteger totalTokens,
                                 ChatModel supervisorChatModel) {
        this.taskId = taskId;
        this.promptUtil = promptUtil;
        this.eventPublisher = eventPublisher;
        this.totalTokens = totalTokens;
        this.supervisorChatModel = supervisorChatModel;
    }

    /**
     * 拿到 Agent 的回复做后置处理
     */
    @Override
    public void afterAgentInvocation(AgentResponse response) {
        // 累加 Token 并计算当前消耗了多少
        totalTokens.addAndGet(getTokens(response.chatResponse()));
        int total = totalTokens.get();
        int current = total - lastPublishedTokens.getAndSet(total);

        // 获取当前执行阶段
        GeneratePhase phase = GeneratePhase.fromAgentName(response.agentName());

        // 调用 Supervisor 获取阶段性总结文本
        String summary = summarizeStage(phase, response.output());

        // 发送总结性消息
        eventPublisher.publishEvent(phase, GenerateStatus.PROCESSING, summary, current);
    }

    /**
     * 拿到 Agent 的错误做后置处理
     */
    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        // 获取当前执行阶段
        GeneratePhase phase = GeneratePhase.fromAgentName(error.agentName());

        // 拿到错误信息
        String message = error.error() == null ? "Agent 调用失败" : error.error().getMessage();

        // 输出日志并发送错误事件
        log.error("Agent invocation failed: taskId={}, agent={}, message={}", taskId, error.agentName(), message);
        eventPublisher.publishEvent(phase, GenerateStatus.PROCESSING, phase.getGenerateStage() + " 阶段出现可恢复错误：" + message, 0);
    }

    private String summarizeStage(GeneratePhase phase, Object output) {
        try {
            ChatResponse response = chat(supervisorChatModel,
                    promptUtil.loadSupervisorPrompt(),
                    "阶段：" + phase.getGenerateStage() + "\n产出：" + JSON.toJSONString(output));
            return response.aiMessage().text();
        } catch (Exception e) {
            return phase.getGenerateStage() + " 阶段完成";
        }
    }

    private ChatResponse chat(ChatModel model, String systemPrompt, String userPrompt) {
        ChatResponse response = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt));
        totalTokens.addAndGet(getTokens(response));
        return response;
    }

    /**
     * 从智能体回复中的元信息拿到 Token 用量
     */
    private int getTokens(ChatResponse response) {
        if (response == null) return 0;
        TokenUsage usage = response.tokenUsage();
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }

}
