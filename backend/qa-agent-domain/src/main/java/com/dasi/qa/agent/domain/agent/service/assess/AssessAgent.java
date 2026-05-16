package com.dasi.qa.agent.domain.agent.service.assess;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assess.model.AssessSaveCommand;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItemBrief;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.model.exception.AssessException;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviceResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnosisResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviceAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnosisAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.RecordAgent;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessAgentFactory;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessLlmModelProvider;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessResultSanitizer;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessmentMetricCalculator;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.request.practice.AssessRequest;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentDetail;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AssessAgent implements IAssessAgent {

    private static final int MAX_RETRY = 2;
    private static final int LLM_NOT_CONFIGURED_CODE = 40902;

    private final IContextUtil contextUtil;
    private final IJsonUtil jsonUtil;
    private final IAgentRepository agentRepository;
    private final AssessAgentFactory assessAgentFactory;
    private final AssessLlmModelProvider assessLlmModelProvider;
    private final AssessmentMetricCalculator assessmentMetricCalculator;
    private final AssessResultSanitizer assessResultSanitizer;

    public AssessAgent(IContextUtil contextUtil,
                       IJsonUtil jsonUtil,
                       IAgentRepository agentRepository,
                       AssessAgentFactory assessAgentFactory,
                       AssessLlmModelProvider assessLlmModelProvider,
                       AssessmentMetricCalculator assessmentMetricCalculator,
                       AssessResultSanitizer assessResultSanitizer) {
        this.contextUtil = contextUtil;
        this.jsonUtil = jsonUtil;
        this.agentRepository = agentRepository;
        this.assessAgentFactory = assessAgentFactory;
        this.assessLlmModelProvider = assessLlmModelProvider;
        this.assessmentMetricCalculator = assessmentMetricCalculator;
        this.assessResultSanitizer = assessResultSanitizer;
    }

    @Override
    public AssessResponse execute(AssessRequest request) {
        String userId = currentUserId();
        validateRequest(request);
        try {
            ChatModel userModel = assessLlmModelProvider.getUserLlmModel(userId);
            AssessWorkflowContext workflowContext = AssessWorkflowContext.builder()
                    .userModel(userModel)
                    .prepareStep(scope -> doPrepare(scope, userId, request))
                    .diagnosisStep(this::doDiagnosis)
                    .adviceStep(this::doAdvice)
                    .recordStep(this::doRecord)
                    .saveStep(scope -> doSave(scope, userId))
                    .build();
            UntypedAgent assessAgent = assessAgentFactory.build(workflowContext);
            ResultWithAgenticScope<String> result = assessAgent.invokeWithAgenticScope(Map.of());
            return readAssessResponse(result.agenticScope());
        } catch (AssessException exception) {
            throw toApiException(exception);
        }
    }

    private void validateRequest(AssessRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }

    private void doPrepare(AgenticScope scope, String userId, AssessRequest request) {
        AssessContext context = agentRepository.getAssessContext(request.getSessionId(), userId);
        AssessMetrics metrics = assessmentMetricCalculator.calculate(context);
        context.setMetrics(metrics);
        writeAssessContext(scope, context);
        log.info("【整轮评估】准备完成: sessionId={}, score={}, accuracy={}", context.getSessionId(), metrics.getScore(), metrics.getAccuracy());
    }

    private void doDiagnosis(AgenticScope scope, DiagnosisAgent diagnosisAgent) {
        AssessContext context = readAssessContext(scope);
        DiagnosisResult result = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = diagnosisAgent.diagnose(
                        value(context.getQaSetTitle()),
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
        writeDiagnosisResult(scope, result != null ? result : assessResultSanitizer.fallbackDiagnosis());
    }

    private void doAdvice(AgenticScope scope, AdviceAgent adviceAgent) {
        AssessContext context = readAssessContext(scope);
        DiagnosisResult diagnosis = readDiagnosisResult(scope);
        AdviceResult result = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = adviceAgent.advise(
                        value(context.getQaSetTitle()),
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
        writeAdviceResult(scope, result != null ? result : assessResultSanitizer.fallbackAdvice(context.getMetrics()));
    }

    private void doRecord(AgenticScope scope, RecordAgent recordAgent) {
        AssessContext context = readAssessContext(scope);
        RecordResult result = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = recordAgent.record(
                        value(context.getQaSetTitle()),
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
        writeRecordResult(scope, result != null ? result : assessResultSanitizer.fallbackRecord());
    }

    private void doSave(AgenticScope scope, String userId) {
        AssessContext context = readAssessContext(scope);
        DiagnosisResult diagnosis = readDiagnosisResult(scope);
        AdviceResult advice = readAdviceResult(scope);
        RecordResult record = readRecordResult(scope);
        AssessmentDetail assessmentDetail = assessResultSanitizer.toAssessmentDetail(advice, diagnosis);
        AssessSaveCommand command = AssessSaveCommand.builder()
                .metrics(context.getMetrics())
                .assessmentDetail(assessmentDetail)
                .recordResult(record)
                .build();
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
                        .question(value(item.getQuestion()))
                        .standardAnswer(value(item.getStandardAnswer()))
                        .userAnswer(value(item.getUserAnswer()))
                        .result(value(item.getResult()))
                        .score(item.getScore())
                        .feedbackSummary(value(item.getFeedbackSummary()))
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
        String userId = contextUtil.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private ApiException toApiException(AssessException exception) {
        if (exception.getErrorType() == ErrorType.LLM_NOT_CONFIGURED) {
            return new ApiException(LLM_NOT_CONFIGURED_CODE, exception.getMessage());
        }
        return new ApiException(ResultCode.INTERNAL_ERROR.getCode(), exception.getMessage());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
