package com.dasi.qa.agent.domain.agent.service.feedback;

import com.dasi.qa.agent.domain.agent.model.vo.PracticeVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.HintContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.JudgeContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import com.dasi.qa.agent.domain.agent.service.feedback.model.exception.FeedbackException;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.HintResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.JudgeResult;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.HintAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.JudgeAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackAgentFactory;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackSaver;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackScoreCorrector;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.request.practice.FeedbackRequest;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * FeedbackAgent 负责同步生成单题反馈，并根据用户是否会做分流到 Judge 或 Hint 链路。
 */
@Service
@Slf4j
public class FeedbackAgent implements IFeedbackAgent {

    private static final int MAX_RETRY = 2;

    private final IContextUtil contextUtil;
    private final IJsonUtil jsonUtil;
    private final IAgentRepository agentRepository;
    private final FeedbackAgentFactory feedbackAgentFactory;
    private final UserLlmModelProvider userLlmModelProvider;
    private final FeedbackScoreCorrector feedbackScoreCorrector;
    private final FeedbackSaver feedbackSaver;

    public FeedbackAgent(IContextUtil contextUtil,
                         IJsonUtil jsonUtil,
                         IAgentRepository agentRepository,
                         FeedbackAgentFactory feedbackAgentFactory,
                         UserLlmModelProvider userLlmModelProvider,
                         FeedbackScoreCorrector feedbackScoreCorrector,
                         FeedbackSaver feedbackSaver) {
        this.contextUtil = contextUtil;
        this.jsonUtil = jsonUtil;
        this.agentRepository = agentRepository;
        this.feedbackAgentFactory = feedbackAgentFactory;
        this.userLlmModelProvider = userLlmModelProvider;
        this.feedbackScoreCorrector = feedbackScoreCorrector;
        this.feedbackSaver = feedbackSaver;
    }

    @Override
    public FeedbackResponse execute(FeedbackRequest request) {
        // 1. 构建用户模型
        String userId = contextUtil.getUserId();
        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);

        // 2. 读取 DB 数据快照并处理请求输入
        String sessionItemId = request.getSessionItemId();
        PracticeVO practice = agentRepository.getPracticeVO(sessionItemId, userId);
        String userAnswer = request.getUserAnswer() == null ? "" : request.getUserAnswer().trim();
        boolean unknown = Boolean.TRUE.equals(request.getUnknown()) || !StringUtils.hasText(userAnswer);

        // 3. 预构建阶段上下文
        HintContext hintContext = HintContext.builder()
                .sessionItemId(sessionItemId)
                .question(StringUtils.hasText(practice.getQuestion()) ? practice.getQuestion() : "")
                .standardAnswer(StringUtils.hasText(practice.getStandardAnswer()) ? practice.getStandardAnswer() : "")
                .knowledgeNote(StringUtils.hasText(practice.getKnowledgeNote()) ? practice.getKnowledgeNote() : "")
                .tip(StringUtils.hasText(practice.getTip()) ? practice.getTip() : "")
                .answerStyle(StringUtils.hasText(practice.getAnswerStyle()) ? practice.getAnswerStyle() : "")
                .feedbackStyle(StringUtils.hasText(practice.getFeedbackStyle()) ? practice.getFeedbackStyle() : "")
                .build();
        JudgeContext judgeContext = JudgeContext.builder()
                .sessionItemId(sessionItemId)
                .question(StringUtils.hasText(practice.getQuestion()) ? practice.getQuestion() : "")
                .standardAnswer(StringUtils.hasText(practice.getStandardAnswer()) ? practice.getStandardAnswer() : "")
                .knowledgeNote(StringUtils.hasText(practice.getKnowledgeNote()) ? practice.getKnowledgeNote() : "")
                .tip(StringUtils.hasText(practice.getTip()) ? practice.getTip() : "")
                .userAnswer(userAnswer)
                .answerStyle(StringUtils.hasText(practice.getAnswerStyle()) ? practice.getAnswerStyle() : "")
                .feedbackStyle(StringUtils.hasText(practice.getFeedbackStyle()) ? practice.getFeedbackStyle() : "")
                .build();

        // 4. 构建 DAG 运行上下文
        FeedbackContext feedbackContext = FeedbackContext.builder()
                .userModel(userModel)
                .hintStep((scope, hintAgent) -> doHint(scope, hintAgent, hintContext))
                .judgeStep((scope, judgeAgent) -> doJudge(scope, judgeAgent, judgeContext))
                .build();

        // 5. 构建并执行智能体
        UntypedAgent feedbackAgent = feedbackAgentFactory.build(feedbackContext);
        ResultWithAgenticScope<String> result = feedbackAgent.invokeWithAgenticScope(Map.of(
                feedbackContext.getRouteFlagKey(), unknown
        ));

        // 6. 保存并返回结果
        return feedbackSaver.save(result.agenticScope(), practice, unknown, userAnswer, userId);
    }

    /**
     * HintAgent 负责在用户不会时生成记忆技巧和情绪支持。
     */
    private void doHint(AgenticScope scope, HintAgent hintAgent, HintContext hintContext) {
        HintResult hintResult = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = hintAgent.hint(
                        hintContext.getQuestion(),
                        hintContext.getStandardAnswer(),
                        hintContext.getKnowledgeNote(),
                        hintContext.getTip(),
                        hintContext.getAnswerStyle(),
                        hintContext.getFeedbackStyle(),
                        retryHint
                );
                hintResult = jsonUtil.parseJsonObject(response, HintResult.class);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    hintResult = fallbackHint();
                    log.warn("【单题反馈】HintAgent 最终失败，使用兜底提示: sessionItemId={}", hintContext.getSessionItemId(), exception);
                } else {
                    log.warn("【单题反馈】HintAgent 失败，重试: attempt={}, sessionItemId={}", attempt + 1, hintContext.getSessionItemId(), exception);
                }
            }
        }
        scope.writeState(FeedbackPhase.HINT.getScopeKey(), hintResult);
    }

    /**
     * JudgeAgent 负责对有效用户回答进行判定、打分和改进建议生成。
     */
    private void doJudge(AgenticScope scope, JudgeAgent judgeAgent, JudgeContext judgeContext) {
        JudgeResult judgeResult = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = judgeAgent.judge(
                        judgeContext.getQuestion(),
                        judgeContext.getStandardAnswer(),
                        judgeContext.getKnowledgeNote(),
                        judgeContext.getTip(),
                        judgeContext.getUserAnswer(),
                        judgeContext.getAnswerStyle(),
                        judgeContext.getFeedbackStyle(),
                        retryHint
                );
                judgeResult = jsonUtil.parseJsonObject(response, JudgeResult.class);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【单题反馈】JudgeAgent 最终失败: sessionItemId={}", judgeContext.getSessionItemId(), exception);
                    if (judgeResult == null) {
                        throw new FeedbackException(AgentErrorType.INVALID_RESPONSE, "JudgeAgent 调用失败，已重试 " + MAX_RETRY + " 次");
                    }
                } else {
                    log.warn("【单题反馈】JudgeAgent 失败，重试: attempt={}, sessionItemId={}", attempt + 1, judgeContext.getSessionItemId(), exception);
                }
            }
        }
        feedbackScoreCorrector.correct(judgeResult);
        scope.writeState(FeedbackPhase.JUDGE.getScopeKey(), judgeResult);
    }

    private HintResult fallbackHint() {
        return HintResult.builder()
                .memoryTip("先把题目里的核心名词和标准答案第一层结构对应起来，下一轮复习时会更容易抓住主线。")
                .encouragement("暂时不会并不代表没有进展，能把卡住的题标出来，本身就是一次有效练习。")
                .build();
    }

}
