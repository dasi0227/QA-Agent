package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * DraftAgent：围绕单个模块，把检索到的证据整理成首版问答题目。
 * - 输入：当前模块的证据内容、用户画像、题量要求、岗位描述、已有题目和用户补充要求。
 * - 输出：当前模块的草稿题目列表，作为后续审校阶段的输入。
 */
public interface DraftAgent {

    @SystemMessage(fromResource = "prompt/generate/generate-draft.txt")
    @UserMessage("""
            模块：{{module}}
            核心考点：{{keyConcepts}}
            证据块：{{evidence}}
            用户资料：{{userProfile}}
            用户备注：{{userPrompt}}
            岗位描述：{{jobDescription}}
            本批题数：{{questionCount}}
            已有题目（避免重复）：{{previousQuestions}}
            答案风格：{{answerStyle}}

            输出要求：
            1. 只输出一个合法 JSON 数组，以 [ 开头，以 ] 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，缺失字段用 "" 填充。
            5. 数组长度必须等于 {{questionCount}}。
            6. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "DRAFTER", description = "根据检索证据起草结构化问答题目")
    String draft(@V("taskId") String taskId,
                 @V("module") String module,
                 @V("keyConcepts") String keyConcepts,
                 @V("evidence") String evidence,
                 @V("userProfile") String userProfile,
                 @V("userPrompt") String userPrompt,
                 @V("jobDescription") String jobDescription,
                 @V("questionCount") int questionCount,
                 @V("previousQuestions") String previousQuestions,
                 @V("answerStyle") String answerStyle,
                 @V("retryHint") String retryHint);
}
