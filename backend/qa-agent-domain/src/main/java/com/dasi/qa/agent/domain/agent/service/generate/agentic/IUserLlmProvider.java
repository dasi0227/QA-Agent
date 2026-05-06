package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.dasi.qa.agent.domain.agent.model.UserLlmConfig;

public interface IUserLlmProvider {

    UserLlmConfig getConfig(String userId);
}
