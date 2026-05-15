package com.dasi.qa.agent.domain.agent.service.feedback.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * JudgeAgent：用户提交有效回答时，对照本题标准答案给出单题反馈。
 */
public interface JudgeAgent {

    @SystemMessage(fromResource = "prompt/feedback-judge.txt")
    @UserMessage("""
            题目：{{question}}
            标准答案：{{standardAnswer}}
            复习笔记：{{knowledgeNote}}
            证据边界提示：{{tip}}
            用户回答：{{userAnswer}}
            答案风格：{{answerStyle}}
            反馈风格：{{feedbackStyle}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，数组字段无内容时输出 []，字符串字段无内容时输出 ""。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "JUDGE", description = "对用户单题作答进行判定并生成反馈")
    String judge(@V("question") String question,
                 @V("standardAnswer") String standardAnswer,
                 @V("knowledgeNote") String knowledgeNote,
                 @V("tip") String tip,
                 @V("userAnswer") String userAnswer,
                 @V("answerStyle") String answerStyle,
                 @V("feedbackStyle") String feedbackStyle,
                 @V("retryHint") String retryHint);
}
