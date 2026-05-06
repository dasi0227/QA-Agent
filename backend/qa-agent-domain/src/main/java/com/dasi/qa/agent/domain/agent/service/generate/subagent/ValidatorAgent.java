package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ValidatorAgent {

    @SystemMessage(fromResource = "prompt/generation-validate.txt")
    @UserMessage("""
            待校验题目：
            {{draftItems}}

            证据块：
            {{evidenceChunks}}

            请返回 ValidationResult JSON 数组。
            """)
    @Agent(name = "VALIDATOR", description = "校验题目事实准确性和证据边界", outputKey = "lastValidationResults")
    String validate(@MemoryId @V("taskId") String taskId,
                    @V("draftItems") String draftItemsJson,
                    @V("evidenceChunks") String evidenceChunks);
}
