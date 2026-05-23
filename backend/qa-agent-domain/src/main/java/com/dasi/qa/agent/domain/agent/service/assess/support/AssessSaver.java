package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assess.model.command.AssessSaveCommand;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessStats;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.SessionContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult.DiagnoseItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.MemoryClueResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.types.dto.response.practice.AssessDetail;
import com.dasi.qa.agent.types.dto.response.practice.AssessPoint;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 负责从 scope 读取整轮评估结果并落库，返回 AssessResponse。
 */
@Component
@Slf4j
public class AssessSaver {

    private final IAgentRepository agentRepository;

    public AssessSaver(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public AssessResponse save(AgenticScope scope, SessionContext context, String userId) {
        // 1. 拿到智能体结果
        DiagnoseResult diagnoseResult = (DiagnoseResult) scope.readState(AssessPhase.DIAGNOSE.getScopeKey());
        AdviseResult adviseResult = (AdviseResult) scope.readState(AssessPhase.ADVISE.getScopeKey());
        RecordResult recordResult = (RecordResult) scope.readState(AssessPhase.RECORD.getScopeKey());

        // 2. 组装上下文
        AssessDetail assessDetail = AssessDetail.builder()
                .overallComment(adviseResult == null || !StringUtils.hasText(adviseResult.getOverallComment()) ? "" : adviseResult.getOverallComment().trim())
                .reviewGuidance(adviseResult == null || !StringUtils.hasText(adviseResult.getReviewGuidance()) ? "" : adviseResult.getReviewGuidance().trim())
                .strengths(toAssessmentPoints(diagnoseResult == null ? null : diagnoseResult.getStrengths()))
                .weaknesses(toAssessmentPoints(diagnoseResult == null ? null : diagnoseResult.getWeaknesses()))
                .build();
        AssessStats stats = context.getStats();
        List<MemoryClueResult> memoryClues = recordResult == null || recordResult.getClues() == null ? List.of() : recordResult.getClues();
        AssessSaveCommand command = AssessSaveCommand.builder()
                .score(stats.getScore())
                .accuracy(stats.getAccuracy())
                .perfectCount(stats.getPerfectCount())
                .correctCount(stats.getCorrectCount())
                .deficientCount(stats.getDeficientCount())
                .wrongCount(stats.getWrongCount())
                .unknownCount(stats.getUnknownCount())
                .assessDetail(assessDetail)
                .memoryClues(memoryClues)
                .build();

        // 3. 落库
        AssessResponse assessResponse = agentRepository.saveAssessResult(context.getSessionId(), userId, command);
        log.info("【整轮评估】保存完成: sessionId={}, score={}, accuracy={}", context.getSessionId(), assessResponse.getScore(), assessResponse.getAccuracy());
        return assessResponse;
    }

    private List<AssessPoint> toAssessmentPoints(List<DiagnoseItem> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> AssessPoint.builder()
                        .title(StringUtils.hasText(value.getTitle()) ? value.getTitle().trim() : "")
                        .analysis(StringUtils.hasText(value.getAnalysis()) ? value.getAnalysis().trim() : "")
                        .build())
                .toList();
    }
}
