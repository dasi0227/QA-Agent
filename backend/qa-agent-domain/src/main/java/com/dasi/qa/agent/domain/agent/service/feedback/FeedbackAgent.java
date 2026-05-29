package com.dasi.qa.agent.domain.agent.service.feedback;

import com.dasi.qa.agent.domain.agent.model.vo.PracticeVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.HintContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.JudgeContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.exception.FeedbackException;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.HintResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.JudgeResult;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
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
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
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
        ChatModel userModel = userLlmModelProvider.getUserLlmModel4Agent(userId);

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
                .sourceReliable(practice.getSourceReliable())
                .answerStyle(StringUtils.hasText(practice.getAnswerStyle()) ? practice.getAnswerStyle() : "")
                .feedbackStyle(StringUtils.hasText(practice.getFeedbackStyle()) ? practice.getFeedbackStyle() : "")
                .build();
        JudgeContext judgeContext = JudgeContext.builder()
                .sessionItemId(sessionItemId)
                .question(StringUtils.hasText(practice.getQuestion()) ? practice.getQuestion() : "")
                .standardAnswer(StringUtils.hasText(practice.getStandardAnswer()) ? practice.getStandardAnswer() : "")
                .knowledgeNote(StringUtils.hasText(practice.getKnowledgeNote()) ? practice.getKnowledgeNote() : "")
                .sourceReliable(practice.getSourceReliable())
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
        log.info("【单题反馈】DAG 执行完成: sessionItemId={}, unknown={}", sessionItemId, unknown);
        try {
            return feedbackSaver.save(result.agenticScope(), practice, unknown, userAnswer, userId);
        } catch (Exception exception) {
            log.error("【单题反馈】反馈 DAG 执行或保存失败: sessionItemId={}", sessionItemId, exception);
            throw new FeedbackException(AgentErrorType.UNKNOWN, "反馈生成失败，请稍后重试");
        }
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
                        hintContext.getSourceReliable(),
                        hintContext.getAnswerStyle(),
                        hintContext.getFeedbackStyle(),
                        retryHint
                );
                hintResult = jsonUtil.parseJsonObject(response, HintResult.class);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【单题反馈】HintAgent 最终失败: maxRetries={}, sessionItemId={}", MAX_RETRY, hintContext.getSessionItemId(), exception);
                } else {
                    log.warn("【单题反馈】HintAgent 调用失败，重试: attempt={}, sessionItemId={}", attempt + 1, hintContext.getSessionItemId(), exception);
                }
            }
        }
        writeHintResult(scope, hintResult);
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
                        judgeContext.getSourceReliable(),
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
                    log.warn("【单题反馈】JudgeAgent 最终失败: maxRetries={}, sessionItemId={}", MAX_RETRY, judgeContext.getSessionItemId(), exception);
                } else {
                    log.warn("【单题反馈】JudgeAgent 调用失败，重试: attempt={}, sessionItemId={}", attempt + 1, judgeContext.getSessionItemId(), exception);
                }
            }
        }
        writeJudgeResult(scope, judgeResult);
    }

    private HintResult fallbackHint() {
        return HintResult.builder()
                .memoryTip("先把题目里的核心名词和标准答案第一层结构对应起来，下一轮复习时会更容易抓住主线。")
                .encouragement("暂时不会并不代表没有进展，能把卡住的题标出来，本身就是一次有效练习。")
                .build();
    }

    private JudgeResult fallbackJudge() {
        return JudgeResult.builder()
                .result(FeedbackResult.WRONG.name())
                .score(20)
                .feedbackSummary("本题已完成提交，但系统未能稳定生成判题结果。下一轮建议先按错题处理，优先补齐核心概念和主线表达。")
                .missingPoints(List.of())
                .wrongPoints(List.of())
                .improvementAdvice("")
                .betterAnswer("")
                .build();
    }

    private void writeHintResult(AgenticScope scope, HintResult result) {
        scope.writeState(FeedbackPhase.HINT.getScopeKey(), result != null ? result : fallbackHint());
    }

    private void writeJudgeResult(AgenticScope scope, JudgeResult result) {
        JudgeResult finalResult = result != null ? result : fallbackJudge();
        String rawResult = finalResult.getResult();
        Integer rawScore = finalResult.getScore();
        feedbackScoreCorrector.correct(finalResult);
        if (!rawResult.equals(finalResult.getResult()) || !rawScore.equals(finalResult.getScore())) {
            log.warn("【单题反馈】分数校准: rawResult={}, rawScore={}, correctedResult={}, correctedScore={}", rawResult, rawScore, finalResult.getResult(), finalResult.getScore());
        }
        scope.writeState(FeedbackPhase.JUDGE.getScopeKey(), finalResult);
    }

}
