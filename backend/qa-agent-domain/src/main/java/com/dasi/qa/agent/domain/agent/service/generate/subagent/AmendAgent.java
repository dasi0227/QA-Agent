package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AmendAgent {

    @SystemMessage(fromResource = "prompt/generation-amend.txt")
    @UserMessage("""
            待修订题目与审校意见：
            {{amendItemsJson}}

            用户备注：
            {{userPrompt}}

            请返回 DraftItem JSON 数组。
            """)
    @Agent(name = "AMENDER", description = "按审校意见最小修订问答题目", outputKey = "amendedDraftItems")
    String amend(@MemoryId @V("taskId") String taskId,
                 @V("amendItemsJson") String amendItemsJson,
                 @V("userPrompt") String userPrompt);
}
