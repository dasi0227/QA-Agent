package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 负责从 scope 读取生成结果并落库。
 */
@Component
@SuppressWarnings("unchecked")
public class GenerateSaver {

    private final IAgentRepository agentRepository;

    public GenerateSaver(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public void save(AgenticScope scope, String taskId, String userId, CreateQaSetRequest request) {
        PlanResult planResult = (PlanResult) scope.readState(GeneratePhase.PLAN.getScopeKey());
        List<DraftResult> validatedResult = (List<DraftResult>) scope.readState(GeneratePhase.VALIDATE.getScopeKey());
        String qaSetId = agentRepository.saveGeneratedQaSet(taskId, userId, request, planResult, validatedResult);
        agentRepository.markTaskCompleted(taskId, qaSetId);
    }
}
