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
            证据块：{{evidenceChunks}}
            目标岗位：{{targetRole}}
            目标公司：{{targetCompany}}
            回答风格：{{answerStyle}}
            难度分布：{{difficultyDistribution}}
            本批题数：{{questionCount}}
            已有题目：{{previousQuestions}}
            用户备注：{{note}}

            请返回 DraftItem JSON 数组。
            """)
    @Agent(name = "DRAFTER", description = "根据资料证据起草问答题目", outputKey = "draftItems")
    String draft(@MemoryId @V("taskId") String taskId,
                 @V("moduleTag") String moduleTag,
                 @V("evidenceChunks") String evidenceChunks,
                 @V("targetRole") String targetRole,
                 @V("targetCompany") String targetCompany,
                 @V("answerStyle") String answerStyle,
                 @V("difficultyDistribution") String difficultyDistribution,
                 @V("questionCount") int questionCount,
                 @V("previousQuestions") String previousQuestions,
                 @V("note") String note);
}
