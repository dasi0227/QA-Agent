package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * PlanAgent：先读资料结构和用户目标，再决定这套问答集应该覆盖哪些模块、每个模块出多少题。
 * - 输入：资料目录摘要、用户画像、长期记忆画像、用户要求、岗位描述，以及目标题数。
 * - 输出：一份可执行的题集规划结果，包含模块划分和题量分配。
 */
public interface PlanAgent {

    @SystemMessage(fromResource = "prompt/generate/generate-plan.txt")
    @UserMessage("""
            资料目录：{{documents}}
            用户资料：{{userProfile}}
            长期记忆画像：{{memoryProfile}}
            用户备注：{{userPrompt}}
            岗位描述：{{jobDescription}}
            目标题数：{{questionCount}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含 title、description、planItems 三个字段。
            5. planItems 至少 1 个元素。
            6. 所有 planItems.questionCount 之和必须等于 {{questionCount}}。
            7. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent
    PlanResult plan(@V("taskId") String taskId,
                    @V("documents") String documents,
                    @V("userProfile") String userProfile,
                    @V("memoryProfile") String memoryProfile,
                    @V("userPrompt") String userPrompt,
                    @V("jobDescription") String jobDescription,
                    @V("questionCount") int questionCount,
                    @V("retryHint") String retryHint);
}
