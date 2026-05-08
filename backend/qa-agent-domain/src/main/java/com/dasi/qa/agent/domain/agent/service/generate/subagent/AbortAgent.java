package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AbortAgent {

    @SystemMessage(fromResource = "prompt/generation-abort.txt")
    @UserMessage("""
            用户要求：
            {{userPrompt}}

            判定原因：
            {{reason}}

            请直接输出最终失败说明文本。
            """)
    @Agent(name = "ABORT", description = "根据判定原因生成终止问答集生成的失败说明")
    String abort(@MemoryId @V("taskId") String taskId,
                 @V("userPrompt") String userPrompt,
                 @V("reason") String reason);
}
