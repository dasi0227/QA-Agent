package com.dasi.qa.agent.domain.agent.service.memory.support;

import com.dasi.qa.agent.domain.agent.service.memory.model.context.MemoryContext;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.MemoryPhase;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.InvestAgent;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.MergeAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class MemoryAgentFactory {

    public UntypedAgent build(MemoryContext context) {
        InvestAgent investAgent = buildInvestAgent(context.getUserModel());
        MergeAgent mergeAgent = buildMergeAgent(context.getUserModel());
        AgenticServices.AgenticScopeAction investAction =
                AgenticServices.agentAction(scope -> context.getInvestStep().run(scope, investAgent));
        AgenticServices.AgenticScopeAction mergeAction =
                AgenticServices.agentAction(scope -> context.getMergeStep().run(scope, mergeAgent));

        return AgenticServices.sequenceBuilder()
                .name(MemoryPhase.MEMORY.getAgentName())
                .description(MemoryPhase.MEMORY.getAgentDesc())
                .subAgents(investAction, mergeAction)
                .build();
    }

    public InvestAgent buildInvestAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(InvestAgent.class)
                .name(MemoryPhase.INVEST.getAgentName())
                .description(MemoryPhase.INVEST.getAgentDesc())
                .chatModel(userModel)
                .build();
    }

    public MergeAgent buildMergeAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(MergeAgent.class)
                .name(MemoryPhase.MERGE.getAgentName())
                .description(MemoryPhase.MERGE.getAgentDesc())
                .chatModel(userModel)
                .build();
    }
}
