package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * PlanAgent：先读资料结构和用户目标，再决定这套问答集应该覆盖哪些模块、每个模块出多少题。
 * - 输入：资料目录摘要、用户画像、用户要求，以及目标题数。
 * - 输出：一份可执行的题集规划结果，包含模块划分和题量分配。
 */
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
