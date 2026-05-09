package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * EvaluateAgent：审校题目是否准确、是否被资料支撑、是否适合作为最终问答集内容。
 * - 输入：一批待审校的草稿题目。
 * - 输出：逐题审校结果，包括通过、拒绝或要求修订的判定及原因。
 */
public interface EvaluateAgent {

    @SystemMessage(fromResource = "prompt/generation-evaluate.txt")
    @UserMessage("""
            待校验题目：
            {{draftItemsJson}}

            请返回 EvaluateResult JSON 数组。
            """)
    @Agent(name = "EVALUATOR", description = "审校题目事实准确性和证据边界", outputKey = "lastValidationResults")
    String evaluate(@MemoryId @V("taskId") String taskId,
                    @V("draftItemsJson") String draftItemsJson);
}
