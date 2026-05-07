package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.model.DecideResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DecideAgent {

    @SystemMessage(fromResource = "prompt/generation-decide.txt")
    @UserMessage("""
            用户要求：
            {{userPrompt}}

            请返回 DecideResult JSON。
            """)
    @Agent(name = "DECIDE", description = "判断生成请求是否可以进入问答集生成 DAG", outputKey = "decideResult")
    DecideResult decide(@MemoryId @V("taskId") String taskId,
                        @V("userPrompt") String userPrompt);
}
