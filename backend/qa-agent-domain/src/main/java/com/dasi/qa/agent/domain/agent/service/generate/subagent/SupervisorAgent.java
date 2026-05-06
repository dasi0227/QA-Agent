package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SupervisorAgent {

    @SystemMessage(fromResource = "prompt/supervisor-summary.txt")
    @UserMessage("""
            阶段：{{stage}}
            产出：{{output}}
            """)
    @Agent(name = "SUPERVISOR", description = "总结阶段输出", outputKey = "summary")
    String summarize(@MemoryId @V("taskId") String taskId,
                     @V("stage") String stage,
                     @V("output") String output);

    @SystemMessage(fromResource = "prompt/supervisor-classify.txt")
    @UserMessage("""
            用户要求：{{note}}
            """)
    String classify(@MemoryId @V("taskId") String taskId,
                    @V("note") String note);
}
