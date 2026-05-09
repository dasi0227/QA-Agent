package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PlanAgent {

    @SystemMessage(fromResource = "prompt/generation-plan.txt")
    @UserMessage("""
            资料目录：
            {{documents}}

            用户资料：{{userProfile}}
            用户备注：{{userPrompt}}
            目标题数：{{questionCount}}

            请返回 PlanResult JSON。
            """)
    @Agent(name = "PLANNER", description = "分析资料目录结构并规划问答集模块", outputKey = "planResult")
    PlanResult plan(@MemoryId @V("taskId") String taskId,
                    @V("documents") String documents,
                    @V("userProfile") String userProfile,
                    @V("userPrompt") String userPrompt,
                    @V("questionCount") int questionCount);
}
