package com.dasi.qa.agent.domain.agent.service.memory.subagent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MergeAgent {

    @SystemMessage(fromResource = "prompt/memory/memory-merge.txt")
    @UserMessage("""
            已有画像内容：{{existingContent}}

            新候选画像内容：{{candidateContent}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 只允许输出 content 字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    String merge(@V("existingContent") String existingContent,
                 @V("candidateContent") String candidateContent,
                 @V("retryHint") String retryHint);
}
