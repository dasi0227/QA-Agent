package com.dasi.qa.agent.domain.agent.service.assist.subagent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AssistSubAgent {

    @SystemMessage(fromResource = "prompt/assist/assist-item.txt")
    @UserMessage("""
            题目：{{question}}
            标准答案：{{standardAnswer}}
            复习笔记：{{knowledgeNote}}
            模块：{{moduleTag}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含 keywords 和 hint 两个字段。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    String assist(@V("question") String question,
                  @V("standardAnswer") String standardAnswer,
                  @V("knowledgeNote") String knowledgeNote,
                  @V("moduleTag") String moduleTag,
                  @V("retryHint") String retryHint);
}
