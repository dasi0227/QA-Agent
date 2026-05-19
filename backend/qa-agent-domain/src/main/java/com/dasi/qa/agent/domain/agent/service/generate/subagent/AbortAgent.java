package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AbortAgent：在请求被判定为不适合生成问答集时，生成一段面向用户的终止说明。
 * - 输入：用户原始要求，以及“不允许进入生成链路”的判定原因。
 * - 输出：可直接用于任务失败消息和 SSE 推送的最终说明文本。
 */
public interface AbortAgent {

    @SystemMessage(fromResource = "prompt/generate/generate-abort.txt")
    @UserMessage("""
            用户要求：
            {{userPrompt}}

            判定原因：
            {{reason}}

            请直接输出最终失败说明文本。
            """)
    @Agent(name = "ABORT", description = "根据判定原因生成终止问答集生成的失败说明")
    String abort(@V("taskId") String taskId,
                 @V("userPrompt") String userPrompt,
                 @V("reason") String reason);
}
