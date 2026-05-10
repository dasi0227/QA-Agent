package com.dasi.qa.agent.domain.agent.service.generate.tool;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.InterviewInsights;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

/**
 * 联网搜索工具，供 Agent 搜索目标公司岗位的真实面试经验与面经。
 */
public class WebSearchTool {

    private final ChatModel webSearchModel;
    private final IPromptUtil promptUtil;

    public WebSearchTool(ChatModel webSearchModel, IPromptUtil promptUtil) {
        this.webSearchModel = webSearchModel;
        this.promptUtil = promptUtil;
    }

    @Tool("搜索目标公司岗位的真实面试经验和面经")
    public InterviewInsights search(@P("目标公司") String company,
                                    @P("目标岗位") String role,
                                    @P("技术模块") String module) {
        String query = String.format("搜索 %s %s %s 面试面经", company, role, module);
        ChatResponse response = webSearchModel.chat(SystemMessage.from(promptUtil.loadWebSearchPrompt()), UserMessage.from(query));
        try {
            return JSON.parseObject(response.aiMessage().text(), InterviewInsights.class);
        } catch (Exception ignored) {
            return new InterviewInsights(company, role, module, List.of(), List.of(), "", "web-search parse failed");
        }
    }
}
