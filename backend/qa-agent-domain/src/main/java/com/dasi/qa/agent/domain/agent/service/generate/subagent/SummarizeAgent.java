package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummarizeAgent {

    @SystemMessage(fromResource = "prompt/generation-summarize.txt")
    @UserMessage("""
            用户要求：{{userPrompt}}
            用户资料：{{userProfile}}

            题集标题：{{title}}
            题集概述：{{description}}

            请求题数：{{requiredCount}}
            实际通过题数：{{generatedCount}}

            模块名称：{{modules}}
            题目标签：{{tags}}

            累计 token：{{totalTokens}}

            最终题目：
            {{qa}}

            请直接输出最终完成说明文本。
            """)
    @Agent(name = "SUMMARIZER", description = "根据生成结果和统计信息生成最终完成说明")
    String summarize(@MemoryId @V("taskId") String taskId,
                     @V("userPrompt") String userPrompt,
                     @V("userProfile") String userProfile,
                     @V("title") String title,
                     @V("description") String description,
                     @V("requiredCount") int requiredCount,
                     @V("generatedCount") int generatedCount,
                     @V("totalTokens") int totalTokens,
                     @V("modules") String modules,
                     @V("tags") String tags,
                     @V("qa") String qa);
}
