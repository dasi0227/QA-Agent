package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DecideResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * DecideAgent：先判断用户这次请求是不是“生成问答集”这条链路应该处理的问题。
 * - 输入：用户本次的原始要求。
 * - 输出：是否允许进入后续生成流程，以及对应的判定原因。
 */
public interface DecideAgent {

    @SystemMessage(fromResource = "prompt/generate/generate-decide.txt")
    @UserMessage("""
            用户要求：{{userPrompt}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含 valid 和 reason 两个字段。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "DECIDE", description = "判断生成请求是否可以进入问答集生成 DAG")
    DecideResult decide(@V("taskId") String taskId,
                        @V("userPrompt") String userPrompt,
                        @V("retryHint") String retryHint);
}
