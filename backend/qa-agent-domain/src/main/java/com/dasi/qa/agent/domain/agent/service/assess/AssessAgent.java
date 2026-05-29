package com.dasi.qa.agent.domain.agent.service.assess;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AdviseContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItemDetail;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItemBrief;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessStats;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.DiagnoseContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.SessionContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviseAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnoseAgent;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessAgentFactory;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessResultCleaner;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessSaver;
import com.dasi.qa.agent.domain.agent.service.assess.support.AssessStatCalculator;
import com.dasi.qa.agent.domain.util.IModelUtil;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.dto.request.practice.AssessRequest;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
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
    private final IModelUtil modelUtil;
    private final AssessStatCalculator assessStatCalculator;
    private final AssessResultCleaner assessResultCleaner;
    private final AssessSaver assessSaver;
    private final IMqUtil mqUtil;

    public AssessAgent(IContextUtil contextUtil,
                       IJsonUtil jsonUtil,
                       IAgentRepository agentRepository,
                       AssessAgentFactory assessAgentFactory,
                       IModelUtil modelUtil,
                       AssessStatCalculator assessStatCalculator,
                       AssessResultCleaner assessResultCleaner,
                       AssessSaver assessSaver,
                       IMqUtil mqUtil) {
        this.contextUtil = contextUtil;
        this.jsonUtil = jsonUtil;
        this.agentRepository = agentRepository;
        this.assessAgentFactory = assessAgentFactory;
        this.modelUtil = modelUtil;
        this.assessStatCalculator = assessStatCalculator;
        this.assessResultCleaner = assessResultCleaner;
        this.assessSaver = assessSaver;
        this.mqUtil = mqUtil;
    }

    @Override
    public AssessResponse execute(AssessRequest request) {
        // 1. 构建用户模型
        String userId = contextUtil.getUserId();
        ChatModel userModel = modelUtil.getAgentModel(userId);

        // 2. 读取 DB 数据快照
        SessionContext sessionContext = agentRepository.getAssessContext(request.getSessionId(), userId);
        AssessStats stats = assessStatCalculator.calculate(sessionContext);
        sessionContext.setStats(stats);
        String qaSetTitle = sessionContext.getQaSetTitle();
        String statsJson = jsonUtil.toJsonString(sessionContext.getStats());
        String itemsJson = jsonUtil.toJsonString(sessionContext.getItems());
        String itemBriefsJson = jsonUtil.toJsonString(itemBriefs(sessionContext.getItems()));

        // 3. 预构建阶段上下文
        DiagnoseContext diagnoseContext = DiagnoseContext.builder()
                .sessionId(sessionContext.getSessionId())
                .qaSetTitle(qaSetTitle)
                .statsJson(statsJson)
                .itemsJson(itemsJson)
                .build();
        AdviseContext adviseContext = AdviseContext.builder()
                .sessionId(sessionContext.getSessionId())
                .qaSetTitle(qaSetTitle)
                .statsJson(statsJson)
                .itemBriefsJson(itemBriefsJson)
                .stats(sessionContext.getStats())
                .build();

        // 4. 构建 DAG 运行上下文
        AssessContext assessContext = AssessContext.builder()
                .userModel(userModel)
                .diagnoseStep((scope, agent) -> doDiagnose(scope, agent, diagnoseContext))
                .adviseStep((scope, agent) -> doAdvise(scope, agent, adviseContext))
                .build();

        // 5. 构建并执行智能体
        UntypedAgent assessAgent = assessAgentFactory.build(assessContext);
        ResultWithAgenticScope<String> result = assessAgent.invokeWithAgenticScope(Map.of());

        // 6. 保存结果
        String sessionId = sessionContext.getSessionId();
        log.info("【整轮评估】DAG 执行完成: sessionId={}", sessionId);
        AssessResponse assessResponse = assessSaver.save(result.agenticScope(), sessionContext, userId);

        // 7. 触发异步记忆沉淀
        mqUtil.sendMemoryMessage(sessionId, Map.of("sessionId", sessionId, "userId", userId));
        return assessResponse;
    }

    /**
     * DiagnoseAgent 负责识别本轮优势和薄弱点。
     */
    private void doDiagnose(AgenticScope scope, DiagnoseAgent diagnoseAgent, DiagnoseContext diagnoseContext) {
        DiagnoseResult result = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = diagnoseAgent.diagnose(
                        diagnoseContext.getQaSetTitle(),
                        diagnoseContext.getStatsJson(),
                        diagnoseContext.getItemsJson(),
                        retryHint
                );
                DiagnoseResult diagnoseResult = jsonUtil.parseJsonObject(response, DiagnoseResult.class);
                result = assessResultCleaner.cleanDiagnosis(diagnoseResult);
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【整轮评估】DiagnoseAgent 最终失败: maxRetries={}, sessionId={}", MAX_RETRY, diagnoseContext.getSessionId(), exception);
                } else {
                    log.warn("【整轮评估】DiagnoseAgent 调用失败，重试: attempt={}, sessionId={}", attempt + 1, diagnoseContext.getSessionId(), exception);
                }
            }
        }
        writeDiagnosisResult(scope, result);
    }

    /**
     * AdviseAgent 负责基于诊断结果生成整体点评和复习指导。
     */
    private void doAdvise(AgenticScope scope, AdviseAgent adviseAgent, AdviseContext adviseContext) {
        DiagnoseResult diagnoseResult = (DiagnoseResult) scope.readState(AssessPhase.DIAGNOSE.getScopeKey());
        AdviseResult adviseResult = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = adviseAgent.advise(
                        adviseContext.getQaSetTitle(),
                        adviseContext.getStatsJson(),
                        jsonUtil.toJsonString(diagnoseResult),
                        adviseContext.getItemBriefsJson(),
                        retryHint
                );
                adviseResult = assessResultCleaner.cleanAdvise(jsonUtil.parseJsonObject(response, AdviseResult.class));
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【整轮评估】AdviseAgent 最终失败: maxRetries={}, sessionId={}", MAX_RETRY, adviseContext.getSessionId(), exception);
                } else {
                    log.warn("【整轮评估】AdviseAgent 调用失败，重试: attempt={}, sessionId={}", attempt + 1, adviseContext.getSessionId(), exception);
                }
            }
        }
        writeAdviceResult(scope, adviseResult, adviseContext.getStats());
    }

    private List<AssessItemBrief> itemBriefs(List<AssessItemDetail> items) {
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

    private void writeDiagnosisResult(AgenticScope scope, DiagnoseResult result) {
        if (result == null) {
            log.warn("【整轮评估】DiagnoseAgent LLM 输出无效，启用兜底");
        }
        scope.writeState(AssessPhase.DIAGNOSE.getScopeKey(), result != null ? result : fallbackDiagnosis());
    }

    private void writeAdviceResult(AgenticScope scope, AdviseResult result, AssessStats stats) {
        if (result == null) {
            log.warn("【整轮评估】AdviseAgent LLM 输出无效，启用兜底");
        }
        scope.writeState(AssessPhase.ADVISE.getScopeKey(), result != null ? result : fallbackAdvice(stats));
    }

    private DiagnoseResult fallbackDiagnosis() {
        return DiagnoseResult.builder()
                .strengths(List.of())
                .weaknesses(List.of())
                .build();
    }

    private AdviseResult fallbackAdvice(AssessStats stats) {
        int score = stats == null || stats.getScore() == null ? 0 : stats.getScore();
        String accuracy = stats == null || stats.getAccuracy() == null ? "0.00" : stats.getAccuracy().toPlainString();
        return AdviseResult.builder()
                .overallComment("本轮练习已完成，系统根据单题结果计算出总分 " + score + "，达标率 " + accuracy + "%。")
                .reviewGuidance("下一轮建议先复盘 WRONG 和 UNKNOWN 题，再回到 DEFICIENT 题补充关键点和表达结构，最后用 PERFECT 和 CORRECT 题保持熟练度。")
                .build();
    }

}
