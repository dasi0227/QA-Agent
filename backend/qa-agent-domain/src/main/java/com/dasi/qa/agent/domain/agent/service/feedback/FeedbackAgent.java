package com.dasi.qa.agent.domain.agent.service.feedback;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.feedback.model.FeedbackSaveCommand;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.HintContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.context.JudgeContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import com.dasi.qa.agent.domain.agent.service.feedback.model.exception.FeedbackException;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.HintResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.JudgeResult;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.HintAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.JudgeAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackAgentFactory;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackLlmModelProvider;
import com.dasi.qa.agent.domain.agent.service.feedback.support.FeedbackScorePolicy;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.request.practice.FeedbackRequest;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackDetailPayload;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;
import com.dasi.qa.agent.types.dto.response.practice.HintFeedbackDetail;
import com.dasi.qa.agent.types.dto.response.practice.JudgeFeedbackDetail;
import com.dasi.qa.agent.types.enumeration.FeedbackDetailType;
import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
/**
 * FeedbackAgent 负责同步生成单题反馈，并根据用户是否会做分流到 Judge 或 Hint 链路。
 */
public class FeedbackAgent implements IFeedbackAgent {

    private static final int MAX_RETRY = 2;
    private static final int LLM_NOT_CONFIGURED_CODE = 40902;
    private static final String UNKNOWN_SUMMARY = "这题已标记为不会。";

    private final IContextUtil contextUtil;
    private final IJsonUtil jsonUtil;
    private final IAgentRepository agentRepository;
    private final FeedbackAgentFactory feedbackAgentFactory;
    private final FeedbackLlmModelProvider feedbackLlmModelProvider;
    private final FeedbackScorePolicy feedbackScorePolicy;

    public FeedbackAgent(IContextUtil contextUtil,
                         IJsonUtil jsonUtil,
                         IAgentRepository agentRepository,
                         FeedbackAgentFactory feedbackAgentFactory,
                         FeedbackLlmModelProvider feedbackLlmModelProvider,
                         FeedbackScorePolicy feedbackScorePolicy) {
        this.contextUtil = contextUtil;
        this.jsonUtil = jsonUtil;
        this.agentRepository = agentRepository;
        this.feedbackAgentFactory = feedbackAgentFactory;
        this.feedbackLlmModelProvider = feedbackLlmModelProvider;
        this.feedbackScorePolicy = feedbackScorePolicy;
    }

    @Override
    public FeedbackResponse execute(FeedbackRequest request) {
        // 1. 校验请求和当前用户
        String userId = currentUserId();
        validateRequest(request);
        try {
            // 2. 构建用户模型和反馈 DAG 上下文
            ChatModel userModel = feedbackLlmModelProvider.getUserLlmModel(userId);
            FeedbackWorkflowContext workflowContext = FeedbackWorkflowContext.builder()
                    .userModel(userModel)
                    .prepareStep(scope -> doPrepare(scope, userId, request))
                    .hintStep(this::doHint)
                    .judgeStep(this::doJudge)
                    .saveStep(scope -> doSave(scope, userId))
                    .build();
            // 3. 启动 DAG 并读取保存后的响应
            UntypedAgent feedbackAgent = feedbackAgentFactory.build(workflowContext);
            ResultWithAgenticScope<String> result = feedbackAgent.invokeWithAgenticScope(Map.of(
                    "sessionItemId", request.getSessionItemId()
            ));
            return readFeedbackResponse(result.agenticScope());
        } catch (FeedbackException exception) {
            throw toApiException(exception);
        }
    }

    private void validateRequest(FeedbackRequest request) {
        if (request == null || request.getSessionItemId() == null || request.getSessionItemId().isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }

    /**
     * PREPARE 阶段负责读取题目上下文，并写入是否进入 Hint 分支的路由标记。
     */
    private void doPrepare(AgenticScope scope, String userId, FeedbackRequest request) {
        // 1. 读取单题反馈上下文
        FeedbackContext context = agentRepository.getFeedbackContext(request.getSessionItemId(), userId);
        // 2. 规范化作答内容和 UNKNOWN 状态
        context.setUserAnswer(normalizeAnswer(request));
        context.setUnknown(isUnknown(request));
        // 3. 写入 Scope 供后续分支读取
        writeFeedbackContext(scope, context);
        scope.writeState(FeedbackPhase.ROUTE.getScopeKey(), Boolean.TRUE.equals(context.getUnknown()));
        log.info("【单题反馈】准备完成: sessionItemId={}, unknown={}", context.getSessionItemId(), context.getUnknown());
    }

    /**
     * HintAgent 负责在用户不会时生成记忆技巧和情绪支持。
     */
    private void doHint(AgenticScope scope, HintAgent hintAgent) {
        // 1. 从 Scope 读取题目上下文
        FeedbackContext context = readFeedbackContext(scope);
        // 2. 组装 HintAgent 输入
        HintContext hintContext = HintContext.builder()
                .question(value(context.getQuestion()))
                .standardAnswer(value(context.getStandardAnswer()))
                .knowledgeNote(value(context.getKnowledgeNote()))
                .tip(value(context.getTip()))
                .answerStyle(value(context.getAnswerStyle()))
                .feedbackStyle(value(context.getFeedbackStyle()))
                .build();
        HintResult result = null;
        String retryHint = "";
        // 3. 调用 HintAgent，失败时携带错误信息重试
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
                result = jsonUtil.parseJsonObject(response, HintResult.class);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【单题反馈】HintAgent 最终失败，使用兜底提示: sessionItemId={}", context.getSessionItemId(), exception);
                } else {
                    log.warn("【单题反馈】HintAgent 失败，重试: attempt={}, sessionItemId={}", attempt + 1, context.getSessionItemId(), exception);
                }
            }
        }
        // 4. 写入 Hint 结果，失败时使用兜底提示
        writeHintResult(scope, result != null ? result : fallbackHint());
    }

    /**
     * JudgeAgent 负责对有效用户回答进行判定、打分和改进建议生成。
     */
    private void doJudge(AgenticScope scope, JudgeAgent judgeAgent) {
        // 1. 从 Scope 读取题目上下文
        FeedbackContext context = readFeedbackContext(scope);
        // 2. 组装 JudgeAgent 输入
        JudgeContext judgeContext = JudgeContext.builder()
                .question(value(context.getQuestion()))
                .standardAnswer(value(context.getStandardAnswer()))
                .knowledgeNote(value(context.getKnowledgeNote()))
                .tip(value(context.getTip()))
                .userAnswer(value(context.getUserAnswer()))
                .answerStyle(value(context.getAnswerStyle()))
                .feedbackStyle(value(context.getFeedbackStyle()))
                .build();
        JudgeResult result = null;
        String retryHint = "";
        // 3. 调用 JudgeAgent，失败时携带错误信息重试
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
                result = jsonUtil.parseJsonObject(response, JudgeResult.class);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【单题反馈】JudgeAgent 最终失败: sessionItemId={}", context.getSessionItemId(), exception);
                } else {
                    log.warn("【单题反馈】JudgeAgent 失败，重试: attempt={}, sessionItemId={}", attempt + 1, context.getSessionItemId(), exception);
                }
            }
        }
        if (result == null) {
            throw new FeedbackException(ErrorType.INVALID_RESPONSE, "JudgeAgent 调用失败，已重试 " + MAX_RETRY + " 次");
        }
        // 4. 校准判定结果和离散分数
        FeedbackResultType resultType = feedbackScorePolicy.normalizeResult(result.getResult());
        if (!feedbackScorePolicy.allowedForJudge(resultType)) {
            throw new FeedbackException(ErrorType.INVALID_RESPONSE, "JudgeAgent 不能输出 UNKNOWN 判定");
        }
        result.setResult(resultType.name());
        result.setScore(feedbackScorePolicy.normalizeScore(resultType, result.getScore()));
        writeJudgeResult(scope, result);
    }

    /**
     * SAVE 阶段负责将 Hint 或 Judge 结果统一转为保存命令并落库。
     */
    private void doSave(AgenticScope scope, String userId) {
        // 1. 读取上下文并根据 UNKNOWN 状态选择保存结构
        FeedbackContext context = readFeedbackContext(scope);
        FeedbackSaveCommand command = Boolean.TRUE.equals(context.getUnknown())
                ? hintSaveCommand(scope)
                : judgeSaveCommand(scope, context);
        // 2. 保存反馈结果并组装接口响应
        LocalDateTime answeredAt = agentRepository.saveFeedbackResult(context.getSessionItemId(), userId, command);
        FeedbackResponse response = FeedbackResponse.builder()
                .sessionItemId(context.getSessionItemId())
                .qaItemId(context.getQaItemId())
                .result(command.getResult())
                .score(command.getScore())
                .feedbackSummary(command.getFeedbackSummary())
                .judgeDetail(command.getDetailPayload().getJudgeDetail())
                .hintDetail(command.getDetailPayload().getHintDetail())
                .sourceChunks(context.getSourceChunks())
                .answeredAt(answeredAt)
                .build();
        writeFeedbackResponse(scope, response);
        log.info("【单题反馈】反馈完成: sessionItemId={}, result={}, score={}", context.getSessionItemId(), response.getResult(), response.getScore());
    }

    private FeedbackSaveCommand hintSaveCommand(AgenticScope scope) {
        HintResult result = readHintResult(scope);
        HintFeedbackDetail detail = HintFeedbackDetail.builder()
                .memoryTip(value(result.getMemoryTip()))
                .encouragement(value(result.getEncouragement()))
                .build();
        return FeedbackSaveCommand.builder()
                .userAnswer("")
                .result(FeedbackResultType.UNKNOWN)
                .score(0)
                .feedbackSummary(UNKNOWN_SUMMARY)
                .detailPayload(FeedbackDetailPayload.builder()
                        .type(FeedbackDetailType.HINT)
                        .hintDetail(detail)
                        .build())
                .build();
    }

    private FeedbackSaveCommand judgeSaveCommand(AgenticScope scope, FeedbackContext context) {
        JudgeResult result = readJudgeResult(scope);
        FeedbackResultType resultType = FeedbackResultType.valueOf(result.getResult());
        JudgeFeedbackDetail detail = JudgeFeedbackDetail.builder()
                .missingPoints(safeList(result.getMissingPoints()))
                .wrongPoints(safeList(result.getWrongPoints()))
                .improvementAdvice(value(result.getImprovementAdvice()))
                .betterAnswer(value(result.getBetterAnswer()))
                .build();
        return FeedbackSaveCommand.builder()
                .userAnswer(value(context.getUserAnswer()))
                .result(resultType)
                .score(result.getScore())
                .feedbackSummary(value(result.getFeedbackSummary()))
                .detailPayload(FeedbackDetailPayload.builder()
                        .type(FeedbackDetailType.JUDGE)
                        .judgeDetail(detail)
                        .build())
                .build();
    }

    private boolean isUnknown(FeedbackRequest request) {
        return Boolean.TRUE.equals(request.getUnknown())
                || request.getUserAnswer() == null
                || request.getUserAnswer().trim().isBlank();
    }

    private String normalizeAnswer(FeedbackRequest request) {
        if (isUnknown(request)) {
            return "";
        }
        return request.getUserAnswer().trim();
    }

    private HintResult fallbackHint() {
        return HintResult.builder()
                .memoryTip("先把题目里的核心名词和标准答案第一层结构对应起来，下一轮复习时会更容易抓住主线。")
                .encouragement("暂时不会并不代表没有进展，能把卡住的题标出来，本身就是一次有效练习。")
                .build();
    }

    private FeedbackContext readFeedbackContext(AgenticScope scope) {
        return (FeedbackContext) scope.readState(FeedbackPhase.PREPARE.getScopeKey());
    }

    private HintResult readHintResult(AgenticScope scope) {
        return (HintResult) scope.readState(FeedbackPhase.HINT.getScopeKey());
    }

    private JudgeResult readJudgeResult(AgenticScope scope) {
        return (JudgeResult) scope.readState(FeedbackPhase.JUDGE.getScopeKey());
    }

    private FeedbackResponse readFeedbackResponse(AgenticScope scope) {
        return (FeedbackResponse) scope.readState(FeedbackPhase.SAVE.getScopeKey());
    }

    private void writeFeedbackContext(AgenticScope scope, FeedbackContext context) {
        scope.writeState(FeedbackPhase.PREPARE.getScopeKey(), context);
    }

    private void writeHintResult(AgenticScope scope, HintResult result) {
        scope.writeState(FeedbackPhase.HINT.getScopeKey(), result);
    }

    private void writeJudgeResult(AgenticScope scope, JudgeResult result) {
        scope.writeState(FeedbackPhase.JUDGE.getScopeKey(), result);
    }

    private void writeFeedbackResponse(AgenticScope scope, FeedbackResponse response) {
        scope.writeState(FeedbackPhase.SAVE.getScopeKey(), response);
    }

    private String currentUserId() {
        String userId = contextUtil.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private ApiException toApiException(FeedbackException exception) {
        if (exception.getErrorType() == ErrorType.LLM_NOT_CONFIGURED) {
            return new ApiException(LLM_NOT_CONFIGURED_CODE, exception.getMessage());
        }
        return new ApiException(ResultCode.INTERNAL_ERROR.getCode(), exception.getMessage());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
