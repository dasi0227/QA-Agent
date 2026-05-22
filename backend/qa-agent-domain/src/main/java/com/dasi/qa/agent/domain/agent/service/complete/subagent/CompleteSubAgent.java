package com.dasi.qa.agent.domain.agent.service.complete.subagent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CompleteSubAgent {

    @SystemMessage(fromResource = "prompt/complete/complete-item.txt")
    @UserMessage("""
            用户问题：{{question}}
            RAG 证据：{{evidence}}
            用户资料：{{userProfile}}
            答案风格：{{answerStyle}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，数组字段无内容时输出 []，字符串字段无内容时输出 ""。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    String complete(@V("question") String question,
                    @V("evidence") String evidence,
                    @V("userProfile") String userProfile,
                    @V("answerStyle") String answerStyle,
                    @V("retryHint") String retryHint);
}
