package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AmendAgent：只针对“需要修订”的题目做最小改动，不重新发散生成整批题目。
 * - 输入：待修订题目，以及每道题对应的审校意见和用户补充要求。
 * - 输出：修订后的题目列表，供下一轮审校继续判断是否通过。
 */
public interface AmendAgent {

    @SystemMessage(fromResource = "prompt/generation-amend.txt")
    @UserMessage("""
            待修订题目与审校意见：{{amendItemsJson}}
            用户备注：{{userPrompt}}
            答案风格：{{answerStyle}}

            输出要求：
            1. 只输出一个合法 JSON 数组，以 [ 开头，以 ] 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 输出数组长度必须等于输入 amendItems 数量，顺序严格一致。
            5. 必须包含所有指定字段，缺失字段用 "" 填充。
            6. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "AMENDER", description = "按审校意见最小修订问答题目")
    String amend(@V("taskId") String taskId,
                 @V("amendItemsJson") String amendItemsJson,
                 @V("userPrompt") String userPrompt,
                 @V("answerStyle") String answerStyle,
                 @V("retryHint") String retryHint);
}
