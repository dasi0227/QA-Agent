package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * EvaluateAgent：审校题目是否准确、是否被资料支撑、是否适合作为最终问答集内容。
 * - 输入：一批待审校的草稿题目、用户补充要求和岗位描述。
 * - 输出：逐题审校结果，包括通过、拒绝或要求修订的判定及原因。
 */
public interface EvaluateAgent {

    @SystemMessage(fromResource = "prompt/generate/generate-evaluate.txt")
    @UserMessage("""
            待校验题目：{{draftItemsJson}}
            用户备注：{{userPrompt}}
            岗位描述：{{jobDescription}}

            输出要求：
            1. 只输出一个合法 JSON 数组，以 [ 开头，以 ] 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 数组长度必须等于输入题目数量，按输入顺序输出。
            5. 每题必须包含 verdict、reason、suggestion 三个字段，缺失字段用 "" 填充。
            6. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent
    String evaluate(@V("taskId") String taskId,
                    @V("draftItemsJson") String draftItemsJson,
                    @V("userPrompt") String userPrompt,
                    @V("jobDescription") String jobDescription,
                    @V("retryHint") String retryHint);
}
