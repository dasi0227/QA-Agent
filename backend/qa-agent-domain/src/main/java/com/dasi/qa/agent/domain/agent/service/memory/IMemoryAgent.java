package com.dasi.qa.agent.domain.agent.service.memory;

import com.dasi.qa.agent.domain.agent.service.memory.model.context.MemoryContext;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.MemoryCandidateResult;

import java.util.List;

public interface IMemoryAgent {

    List<MemoryCandidateResult> extract(MemoryContext context, String userId);
}
