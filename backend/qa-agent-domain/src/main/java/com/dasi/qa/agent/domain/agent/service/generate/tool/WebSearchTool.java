package com.dasi.qa.agent.domain.agent.service.generate.tool;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.shared.InterviewInsights;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebSearchTool {

    private final ChatModel webSearchModel;
    private final String systemPrompt;

    public WebSearchTool(ChatModel webSearchModel) {
        this.webSearchModel = webSearchModel;
        this.systemPrompt = loadPrompt("prompt/web-search-system.txt");
    }

    @Tool("搜索目标公司岗位的真实面试经验和面经")
    public InterviewInsights search(@P("目标公司") String company,
                                    @P("目标岗位") String role,
                                    @P("技术模块") String module) {
        String query = String.format("搜索 %s %s %s 面试面经", company, role, module);
        ChatResponse response = webSearchModel.chat(SystemMessage.from(systemPrompt), UserMessage.from(query));
        try {
            return JSON.parseObject(response.aiMessage().text(), InterviewInsights.class);
        } catch (Exception ignored) {
            return new InterviewInsights(company, role, module, List.of(), List.of(), "", "web-search parse failed");
        }
    }

    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "请搜索并结构化输出目标公司岗位面经，返回 JSON。";
        }
    }
}
