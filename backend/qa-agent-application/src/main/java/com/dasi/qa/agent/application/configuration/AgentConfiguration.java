package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.domain.agent.service.generate.agentic.IQaGenerationDagFactory;
import com.dasi.qa.agent.domain.agent.service.generate.agentic.QaGenerationDagContext;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DrafterAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlannerAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.ValidatorAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfiguration {

    @Bean
    public ChatMemoryProvider qaGenerationChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(20);
    }

    @Bean
    public IQaGenerationDagFactory qaGenerationDagFactory() {
        return context -> {
            PlannerAgent plannerAgent = plannerAgent(context);
            DrafterAgent drafterAgent = drafterAgent(context);
            ValidatorAgent validatorAgent = validatorAgent(context);

            return AgenticServices.sequenceBuilder()
                    .name("QA_GENERATION_DAG")
                    .description("Planner -> Creator -> Validator -> Summarizer")
                    .subAgents(
                            AgenticServices.agentAction(scope -> context.plannerStep().run(scope, plannerAgent)),
                            AgenticServices.agentAction(scope -> context.creatorStep().run(scope, drafterAgent, context.creatorExecutor())),
                            AgenticServices.agentAction(scope -> context.validatorStep().run(scope, drafterAgent, validatorAgent)),
                            AgenticServices.agentAction(scope -> context.summarizerStep().run(scope))
                    )
                    .output(scope -> scope.readState("qaSetId"))
                    .build();
        };
    }

    private PlannerAgent plannerAgent(QaGenerationDagContext context) {
        AgentBuilder<PlannerAgent, ?> builder = AgenticServices.agentBuilder(PlannerAgent.class)
                .name("PLANNER")
                .description("分析资料结构并规划问答集模块")
                .chatModel(context.userModel())
                .chatMemoryProvider(context.chatMemoryProvider())
                .listener(context.listener());
        addTools(builder, context.tools());
        return builder.build();
    }

    private DrafterAgent drafterAgent(QaGenerationDagContext context) {
        AgentBuilder<DrafterAgent, ?> builder = AgenticServices.agentBuilder(DrafterAgent.class)
                .name("DRAFTER")
                .description("根据资料证据起草问答题目")
                .chatModel(context.userModel())
                .chatMemoryProvider(context.chatMemoryProvider())
                .listener(context.listener());
        addTools(builder, context.tools());
        return builder.build();
    }

    private ValidatorAgent validatorAgent(QaGenerationDagContext context) {
        return AgenticServices.agentBuilder(ValidatorAgent.class)
                .name("VALIDATOR")
                .description("校验题目事实准确性和证据边界")
                .chatModel(context.userModel())
                .chatMemoryProvider(context.chatMemoryProvider())
                .listener(context.listener())
                .build();
    }

    private void addTools(AgentBuilder<?, ?> builder, List<Object> tools) {
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools.toArray());
        }
    }

}
