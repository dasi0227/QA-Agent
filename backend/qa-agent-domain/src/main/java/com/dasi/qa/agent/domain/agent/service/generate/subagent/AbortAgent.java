package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.shared.DecideResult;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateAbortedException;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import dev.langchain4j.agentic.scope.AgenticScope;

public class AbortAgent {

    public void abort(AgenticScope scope, EventPublisher eventPublisher) {
        DecideResult result = readDecideResult(scope);
        String reason = hasText(result.reason()) ? result.reason() : "用户要求与生成问答集无关";
        eventPublisher.publishFailure(ErrorType.CONTENT_FILTERED, reason);
        throw new GenerateAbortedException(ErrorType.CONTENT_FILTERED, reason);
    }

    private DecideResult readDecideResult(AgenticScope scope) {
        Object value = scope.readState("decideResult");
        return value instanceof DecideResult result
                ? result
                : new DecideResult(false, "用户要求与生成问答集无关");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
