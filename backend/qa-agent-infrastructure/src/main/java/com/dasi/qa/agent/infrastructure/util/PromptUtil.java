package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IPromptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class PromptUtil implements IPromptUtil {

    public static final String SUPERVISOR_PROMPT_PATH = "prompt/supervisor-summary.txt";
    public static final String WEBSEARCH_PROMPT_PATH = "prompt/web-search.txt";
    public static final String REWRITTER_PROMPT_PATH = "prompt/query-rewrite.txt";

    private static final String SUPERVISOR_PROMPT_FALLBACK = "你是生成链路监督助手。请根据给定阶段与产出，输出一句简洁中文进度总结，不要编造不存在的信息。";
    private static final String WEB_SEARCH_PROMPT_FALLBACK = "你是面试信息检索助手。请基于用户查询返回结构化、可验证的面试经验要点，不要输出与查询无关内容。";
    private static final String REWRITTER_PROMPT_FALLBACK = "你是一个检索查询优化器，将用户问题改写为更适合向量检索和关键词检索的查询文本，只输出改写文本，不加任何前缀、引号或解释。";

    @Override
    public String loadSupervisorPrompt() {
        try {
            return loadPrompt(SUPERVISOR_PROMPT_PATH);
        } catch (IOException e) {
            return SUPERVISOR_PROMPT_FALLBACK;
        }
    }

    @Override
    public String loadWebSearchPrompt() {
        try {
            return loadPrompt(WEBSEARCH_PROMPT_PATH);
        } catch (IOException e) {
            return WEB_SEARCH_PROMPT_FALLBACK;
        }
    }

    @Override
    public String loadRewritterPrompt() {
        try {
            return loadPrompt(REWRITTER_PROMPT_PATH);
        } catch (IOException e) {
            return REWRITTER_PROMPT_FALLBACK;
        }
    }

    @Override
    public String loadPrompt(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
