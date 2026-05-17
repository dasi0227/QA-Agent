package com.dasi.qa.agent.domain.agent.service.assess;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assess.model.AssessSaveCommand;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItemBrief;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviceResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnosisResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviceAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnosisAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.RecordAgent;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessAgentFactory;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessResultSanitizer;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessmentMetricCalculator;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.request.practice.AssessRequest;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentDetail;
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
 * AssessAgent 负责同步生成整轮练习评估，并保存用户可读评估与内部记忆线索。
 */
@Service
@Slf4j
public class AssessAgent implements IAssessAgent {

    private static final int MAX_RETRY = 2;

    private final IContextUtil contextUtil;
    private final IJsonUtil jsonUtil;
    private final IAgentRepository agentRepository;
    private final AssessAgentFactory assessAgentFactory;
    private final UserLlmModelProvider userLlmModelProvider;
    private final AssessmentMetricCalculator assessmentMetricCalculator;
    private final AssessResultSanitizer assessResultSanitizer;

    public AssessAgent(IContextUtil contextUtil,
                       IJsonUtil jsonUtil,
                       IAgentRepository agentRepository,
                       AssessAgentFactory assessAgentFactory,
                       UserLlmModelProvider userLlmModelProvider,
                       AssessmentMetricCalculator assessmentMetricCalculator,
                       AssessResultSanitizer assessResultSanitizer) {
        this.contextUtil = contextUtil;
        this.jsonUtil = jsonUtil;
        this.agentRepository = agentRepository;
        this.assessAgentFactory = assessAgentFactory;
        this.userLlmModelProvider = userLlmModelProvider;
        this.assessmentMetricCalculator = assessmentMetricCalculator;
        this.assessResultSanitizer = assessResultSanitizer;
    }

    @Override
    public AssessResponse execute(AssessRequest request) {
        // 1. 读取当前用户并执行 Java 预处理步骤
        String userId = currentUserId();
        AssessContext context = prepareContext(userId, request);
        doPrepare(context);
        // 2. 构建用户模型和整轮评估 DAG 上下文
        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
        AssessWorkflowContext workflowContext = AssessWorkflowContext.builder()
                .userModel(userModel)
                .diagnosisStep(this::doDiagnosis)
                .adviceStep(this::doAdvice)
                .recordStep(this::doRecord)
                .build();
        // 3. 启动 DAG 并读取保存后的响应
        UntypedAgent assessAgent = assessAgentFactory.build(workflowContext);
        ResultWithAgenticScope<String> result = assessAgent.invokeWithAgenticScope(Map.of(
                AssessPhase.PREPARE.getScopeKey(), context
        ));
        // 4. 执行 Java 保存步骤并返回结果
        doSave(result.agenticScope(), userId);
        return readAssessResponse(result.agenticScope());
    }

    /**
     * 评估前准备完整上下文，并用 Java 规则计算稳定指标。
     */
    private AssessContext prepareContext(String userId, AssessRequest request) {
        // 1. 读取整轮练习上下文
        AssessContext context = agentRepository.getAssessContext(request.getSessionId(), userId);
        // 2. 校验完成状态并计算分数、准确率和分布
        AssessMetrics metrics = assessmentMetricCalculator.calculate(context);
        context.setMetrics(metrics);
        return context;
    }

    /**
     * PREPARE 阶段负责把已校验的上下文写入 Scope。
     */
    private void doPrepare(AssessContext context) {
        log.info("【整轮评估】准备完成: sessionId={}, score={}, accuracy={}",
                context.getSessionId(), context.getMetrics().getScore(), context.getMetrics().getAccuracy());
    }

    /**
     * DiagnosisAgent 负责识别本轮优势和薄弱点。
     */
    private void doDiagnosis(AgenticScope scope, DiagnosisAgent diagnosisAgent) {
        // 1. 读取整轮上下文
        AssessContext context = readAssessContext(scope);
        DiagnosisResult result = null;
        String retryHint = "";
        // 2. 调用 DiagnosisAgent，失败时携带错误信息重试
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = diagnosisAgent.diagnose(
                        StringUtils.hasText(context.getQaSetTitle()) ? context.getQaSetTitle() : "",
                        jsonUtil.toJsonString(context.getMetrics()),
                        jsonUtil.toJsonString(context.getItems()),
                        retryHint
                );
                result = assessResultSanitizer.parseDiagnosis(response);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                log.warn("【整轮评估】DiagnosisAgent 调用失败: attempt={}, sessionId={}", attempt + 1, context.getSessionId(), exception);
            }
        }
        // 3. 写入诊断结果，失败时使用空诊断兜底
        writeDiagnosisResult(scope, result != null ? result : assessResultSanitizer.fallbackDiagnosis());
    }

    /**
     * AdviceAgent 负责基于诊断结果生成整体点评和复习指导。
     */
    private void doAdvice(AgenticScope scope, AdviceAgent adviceAgent) {
        // 1. 读取上下文和诊断结果
        AssessContext context = readAssessContext(scope);
        DiagnosisResult diagnosis = readDiagnosisResult(scope);
        AdviceResult result = null;
        String retryHint = "";
        // 2. 调用 AdviceAgent，失败时携带错误信息重试
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = adviceAgent.advise(
                        StringUtils.hasText(context.getQaSetTitle()) ? context.getQaSetTitle() : "",
                        jsonUtil.toJsonString(context.getMetrics()),
                        jsonUtil.toJsonString(diagnosis),
                        jsonUtil.toJsonString(itemBriefs(context.getItems())),
                        retryHint
                );
                result = assessResultSanitizer.parseAdvice(response, context.getMetrics());
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                log.warn("【整轮评估】AdviceAgent 调用失败: attempt={}, sessionId={}", attempt + 1, context.getSessionId(), exception);
            }
        }
        // 3. 写入建议结果，失败时使用统计摘要兜底
        writeAdviceResult(scope, result != null ? result : assessResultSanitizer.fallbackAdvice(context.getMetrics()));
    }

    /**
     * RecordAgent 负责并发提炼供 V6 Memory 使用的内部线索。
     */
    private void doRecord(AgenticScope scope, RecordAgent recordAgent) {
        // 1. 读取整轮上下文
        AssessContext context = readAssessContext(scope);
        RecordResult result = null;
        String retryHint = "";
        // 2. 调用 RecordAgent，失败时携带错误信息重试
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = recordAgent.record(
                        StringUtils.hasText(context.getQaSetTitle()) ? context.getQaSetTitle() : "",
                        jsonUtil.toJsonString(context.getMetrics()),
                        jsonUtil.toJsonString(context.getItems()),
                        retryHint
                );
                result = assessResultSanitizer.parseRecord(response);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                log.warn("【整轮评估】RecordAgent 调用失败: attempt={}, sessionId={}", attempt + 1, context.getSessionId(), exception);
            }
        }
        // 3. 写入记忆线索，失败时使用空数组兜底
        writeRecordResult(scope, result != null ? result : assessResultSanitizer.fallbackRecord());
    }

    /**
     * SAVE 阶段负责组合评估详情并更新 session 与 qa_set 聚合数据。
     */
    private void doSave(AgenticScope scope, String userId) {
        // 1. 读取并合并两个分支的输出
        AssessContext context = readAssessContext(scope);
        DiagnosisResult diagnosis = readDiagnosisResult(scope);
        AdviceResult advice = readAdviceResult(scope);
        RecordResult record = readRecordResult(scope);
        AssessmentDetail assessmentDetail = assessResultSanitizer.toAssessmentDetail(advice, diagnosis);
        // 2. 组装保存命令
        AssessSaveCommand command = AssessSaveCommand.builder()
                .metrics(context.getMetrics())
                .assessmentDetail(assessmentDetail)
                .recordResult(record)
                .build();
        // 3. 落库并写入接口响应
        AssessResponse response = agentRepository.saveAssessResult(context.getSessionId(), userId, command);
        writeAssessResponse(scope, response);
        log.info("【整轮评估】保存完成: sessionId={}, score={}, accuracy={}", context.getSessionId(), response.getScore(), response.getAccuracy());
    }

    private List<AssessItemBrief> itemBriefs(List<AssessItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> AssessItemBrief.builder()
                        .question(StringUtils.hasText(item.getQuestion()) ? item.getQuestion() : "")
                        .standardAnswer(StringUtils.hasText(item.getStandardAnswer()) ? item.getStandardAnswer() : "")
                        .userAnswer(StringUtils.hasText(item.getUserAnswer()) ? item.getUserAnswer() : "")
                        .result(StringUtils.hasText(item.getResult()) ? item.getResult() : "")
                        .score(item.getScore())
                        .feedbackSummary(StringUtils.hasText(item.getFeedbackSummary()) ? item.getFeedbackSummary() : "")
                        .build())
                .toList();
    }

    private AssessContext readAssessContext(AgenticScope scope) {
        return (AssessContext) scope.readState(AssessPhase.PREPARE.getScopeKey());
    }

    private DiagnosisResult readDiagnosisResult(AgenticScope scope) {
        return (DiagnosisResult) scope.readState(AssessPhase.DIAGNOSIS.getScopeKey());
    }

    private AdviceResult readAdviceResult(AgenticScope scope) {
        return (AdviceResult) scope.readState(AssessPhase.ADVICE.getScopeKey());
    }

    private RecordResult readRecordResult(AgenticScope scope) {
        RecordResult result = (RecordResult) scope.readState(AssessPhase.RECORD.getScopeKey());
        return result == null ? assessResultSanitizer.fallbackRecord() : result;
    }

    private AssessResponse readAssessResponse(AgenticScope scope) {
        return (AssessResponse) scope.readState(AssessPhase.SAVE.getScopeKey());
    }

    private void writeAssessContext(AgenticScope scope, AssessContext context) {
        scope.writeState(AssessPhase.PREPARE.getScopeKey(), context);
    }

    private void writeDiagnosisResult(AgenticScope scope, DiagnosisResult result) {
        scope.writeState(AssessPhase.DIAGNOSIS.getScopeKey(), result);
    }

    private void writeAdviceResult(AgenticScope scope, AdviceResult result) {
        scope.writeState(AssessPhase.ADVICE.getScopeKey(), result);
    }

    private void writeRecordResult(AgenticScope scope, RecordResult result) {
        scope.writeState(AssessPhase.RECORD.getScopeKey(), result);
    }

    private void writeAssessResponse(AgenticScope scope, AssessResponse response) {
        scope.writeState(AssessPhase.SAVE.getScopeKey(), response);
    }

    private String currentUserId() {
        return contextUtil.getUserId();
    }

}
