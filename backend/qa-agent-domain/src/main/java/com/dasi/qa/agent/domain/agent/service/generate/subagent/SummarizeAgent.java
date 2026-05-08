package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummarizeAgent {

    @SystemMessage(fromResource = "prompt/generation-summarize.txt")
    @UserMessage("""
            用户要求：
            {{userPrompt}}

            题集标题：
            {{title}}

            规划结果：
            {{planResult}}

            计划题数：
            {{plannedCount}}

            实际通过题数：
            {{passedCount}}

            未通过题数：
            {{rejectedCount}}

            Creator 失败模块：
            {{failedModules}}

            模块分布：
            {{moduleCounts}}

            难度分布：
            {{difficultyCounts}}

            累计 token：
            {{totalTokens}}

            请直接输出最终完成说明文本。
            """)
    @Agent(name = "SUMMARIZER", description = "根据生成结果和统计信息生成最终完成说明")
    String summarize(@MemoryId @V("taskId") String taskId,
                     @V("userPrompt") String userPrompt,
                     @V("title") String title,
                     @V("planResult") String planResult,
                     @V("plannedCount") int plannedCount,
                     @V("passedCount") int passedCount,
                     @V("rejectedCount") int rejectedCount,
                     @V("failedModules") String failedModules,
                     @V("moduleCounts") String moduleCounts,
                     @V("difficultyCounts") String difficultyCounts,
                     @V("totalTokens") int totalTokens);
}
