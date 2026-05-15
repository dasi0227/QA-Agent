package com.dasi.qa.agent.domain.agent.service.feedback.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * HintAgent：用户明确不会时，只提供记忆技巧和情绪支持。
 */
public interface HintAgent {

    @SystemMessage(fromResource = "prompt/feedback/feedback-hint.txt")
    @UserMessage("""
            题目：{{question}}
            标准答案：{{standardAnswer}}
            复习笔记：{{knowledgeNote}}
            证据边界提示：{{tip}}
            答案风格：{{answerStyle}}
            反馈风格：{{feedbackStyle}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含 memoryTip 和 encouragement 两个字段。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "HINT", description = "为不会作答的用户提供记忆技巧和情绪支持")
    String hint(@V("question") String question,
                @V("standardAnswer") String standardAnswer,
                @V("knowledgeNote") String knowledgeNote,
                @V("tip") String tip,
                @V("answerStyle") String answerStyle,
                @V("feedbackStyle") String feedbackStyle,
                @V("retryHint") String retryHint);
}
