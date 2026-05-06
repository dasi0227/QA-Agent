package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import dev.langchain4j.agentic.UntypedAgent;

public interface IQaGenerationDagFactory {

    UntypedAgent build(QaGenerationDagContext context);

}
