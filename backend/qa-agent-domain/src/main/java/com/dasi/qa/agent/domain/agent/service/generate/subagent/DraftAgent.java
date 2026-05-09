package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DraftAgent {

    @SystemMessage(fromResource = "prompt/generation-draft.txt")
    @UserMessage("""
            模块：{{moduleTag}}
            证据块：{{evidence}}
            用户资料：{{userProfile}}
            本批题数：{{questionCount}}
            已有题目：{{previousQuestions}}
            用户备注：{{userPrompt}}

            请返回 DraftItem JSON 数组。
            """)
    @Agent(name = "DRAFTER", description = "根据检索证据起草结构化问答题目", outputKey = "draftItems")
    String draft(@MemoryId @V("taskId") String taskId,
                 @V("moduleTag") String moduleTag,
                 @V("evidence") String evidence,
                 @V("userProfile") String userProfile,
                 @V("questionCount") int questionCount,
                 @V("previousQuestions") String previousQuestions,
                 @V("userPrompt") String userPrompt);
}
