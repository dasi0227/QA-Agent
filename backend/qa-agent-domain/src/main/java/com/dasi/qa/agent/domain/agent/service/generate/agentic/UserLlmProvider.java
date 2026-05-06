package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.dasi.qa.agent.domain.agent.model.UserLlmConfig;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Service;

@Service
public class UserLlmProvider implements IUserLlmProvider {

    private final IAgentRepository agentRepository;

    public UserLlmProvider(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    public UserLlmConfig getConfig(String userId) {
        UserLlmConfig config = agentRepository.getUserLlmConfig(userId);
        if (config == null || isBlank(config.baseUrl())
                || isBlank(config.apiKey()) || isBlank(config.modelName())) {
            throw new ApiException(ResultCode.LLM_NOT_CONFIGURED);
        }
        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
