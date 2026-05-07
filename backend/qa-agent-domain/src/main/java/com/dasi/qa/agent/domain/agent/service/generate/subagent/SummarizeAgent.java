package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.model.enumuration.Difficulty;
import com.dasi.qa.agent.domain.agent.model.PlanResult;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SummarizeAgent {

    private final IAgentRepository agentRepository;

    public SummarizeAgent(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public SummaryResult summarize(String taskId, String userId, CreateTaskRequest request,
                                   PlanResult planResult, List<DraftItem> draftItems, int rejectedCount,
                                   List<String> failedModules, int totalTokens) {
        String qaSetId = agentRepository.saveGeneratedQaSet(taskId, userId, request, planResult, draftItems);
        Map<String, Long> moduleCounts = draftItems.stream()
                .collect(Collectors.groupingBy(item -> safe(item.moduleTag(), "General"),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> difficultyCounts = Arrays.stream(Difficulty.values())
                .collect(Collectors.toMap(Enum::name,
                        difficulty -> draftItems.stream()
                                .filter(item -> item.difficulty() == difficulty)
                                .count(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        int plannedCount = plannedCount(planResult, request);
        String message = "问答集已生成，共 " + draftItems.size() + " 题（计划 " + plannedCount
                + " 题，未通过或丢弃 " + rejectedCount + " 题）。模块分布：" + moduleCounts
                + "。难度分布：" + difficultyCounts + "。Creator 失败模块 "
                + (failedModules == null ? 0 : failedModules.size()) + " 个，累计消耗 "
                + totalTokens + " tokens。";
        return new SummaryResult(qaSetId, message);
    }

    private int plannedCount(PlanResult planResult, CreateTaskRequest request) {
        if (planResult != null && planResult.planItems() != null && !planResult.planItems().isEmpty()) {
            return planResult.planItems().stream()
                    .mapToInt(item -> Math.max(0, item.questionCount()))
                    .sum();
        }
        return request.getRequestedQuestionCount() == null ? 0 : request.getRequestedQuestionCount();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SummaryResult(String qaSetId, String message) {
    }
}
