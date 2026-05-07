package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.domain.agent.service.generate.agentic.QaGenerationDagContext;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfigurationTest {

    @Test
    void shouldBuildAndRunDynamicQaGenerationDag() {
        AgentConfiguration configuration = new AgentConfiguration();
        UntypedAgent dag = configuration.qaGenerationDagFactory().build(new QaGenerationDagContext(
                "task-1",
                new FakeChatModel(),
                memoryId -> MessageWindowChatMemory.withMaxMessages(20),
                new AgentListener() {
                },
                List.of(),
                List.of(),
                Runnable::run,
                (scope, plannerAgent) -> scope.writeState("planResult", plannerAgent.plan(
                        "task-1", "documents", "", "", "", "", 1)),
                (scope, drafterAgent, executor) -> scope.writeState("allDrafts", drafterAgent.draft(
                        "task-1", "Redis", "[]", "", "", "", "{}", 1, "[]", "")),
                (scope, evaluatorAgent, amenderAgent) -> scope.writeState("passedDrafts", evaluatorAgent.evaluate(
                        "task-1", "[]", "[]")),
                scope -> scope.writeState("qaSetId", "set-1")
        ));

        Object result = dag.invoke(Map.of("taskId", "task-1"));

        assertEquals("set-1", result);
    }

    private static class FakeChatModel implements ChatModel {

        @Override
        public ChatResponse chat(ChatRequest request) {
            String messageText = request.messages().toString();
            String json;
            if (messageText.contains("PlanResult") || messageText.contains("questionCount")) {
                json = """
                        {
                          "title": "T",
                          "description": "D",
                          "planItems": [
                            {
                              "moduleTag": "Redis",
                              "questionCount": 1,
                              "difficultyDistribution": {"easy": 0, "medium": 1, "hard": 0},
                              "focusTopics": ["expire"],
                              "suggestedQuestionTypes": ["concept"]
                            }
                          ]
                        }
                        """;
            } else if (messageText.contains("ValidationResult")) {
                json = """
                        [
                          {"itemIndex": 0, "verdict": "PASS", "reason": "ok", "revisionSuggestion": ""}
                        ]
                        """;
            } else {
                json = """
                        [
                          {
                            "question": "What is Redis expiration?",
                            "knowledgeNote": "note",
                            "answer": "answer",
                            "moduleTag": "Redis",
                            "difficulty": "MEDIUM",
                            "conflictTip": "",
                            "sourceChunkIds": []
                          }
                        ]
                        """;
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
        }
    }
}
