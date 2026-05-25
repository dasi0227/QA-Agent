package com.dasi.qa.agent.domain.agent.service.memory.subagent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MemorySubAgent {

    @SystemMessage(fromResource = "prompt/memory/memory-extract.txt")
    @UserMessage("""
            题集标题：{{qaSetTitle}}
            本轮统计：{{statsJson}}
            单题作答证据：{{itemsJson}}
            已有 ACTIVE 记忆：{{existingMemoriesJson}}

            输出要求：
            1. 只输出一个合法 JSON 数组，以 [ 开头，以 ] 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 最多输出 5 条候选画像；没有高价值画像时输出 []。
            5. evidenceRefs 只能从输入单题作答证据的 sessionItemId 中选择。
            6. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    String extract(@V("qaSetTitle") String qaSetTitle,
                   @V("statsJson") String statsJson,
                   @V("itemsJson") String itemsJson,
                   @V("existingMemoriesJson") String existingMemoriesJson,
                   @V("retryHint") String retryHint);
}
