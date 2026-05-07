package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.shared.PlanResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PlanAgent {

    @SystemMessage(fromResource = "prompt/generation-plan.txt")
    @UserMessage("""
            资料摘要：
            {{documents}}

            目标岗位：{{targetRole}}
            目标方向：{{targetDomain}}
            目标公司：{{targetCompany}}
            用户备注：{{note}}
            目标题数：{{questionCount}}

            请返回 PlanResult JSON。
            """)
    @Agent(name = "PLANNER", description = "分析资料结构并规划问答集模块", outputKey = "planResult")
    PlanResult plan(@MemoryId @V("taskId") String taskId,
                    @V("documents") String documentsSummary,
                    @V("targetRole") String targetRole,
                    @V("targetDomain") String targetDomain,
                    @V("targetCompany") String targetCompany,
                    @V("note") String note,
                    @V("questionCount") int questionCount);
}
