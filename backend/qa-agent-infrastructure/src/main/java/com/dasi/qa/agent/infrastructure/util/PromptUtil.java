package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.constant.DefaultConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class PromptUtil implements IPromptUtil {

    private static final String SUPERVISOR_PROMPT_FALLBACK =
            "你是生成链路监督助手。请根据给定阶段与产出，输出一句简洁中文进度总结，不要编造不存在的信息。";

    private static final String WEB_SEARCH_PROMPT_FALLBACK =
            "你是面试信息检索助手。请基于用户查询返回结构化、可验证的面试经验要点，不要输出与查询无关内容。";

    @Override
    public String loadSupervisorPrompt() {
        try {
            return loadPrompt(DefaultConstant.SUPERVISOR_PROMPT_PATH);
        } catch (IOException e) {
            log.error("load supervisor prompt failed, fallback used: path={}", DefaultConstant.SUPERVISOR_PROMPT_PATH, e);
            return SUPERVISOR_PROMPT_FALLBACK;
        }
    }

    @Override
    public String loadWebSearchPrompt() {
        try {
            return loadPrompt(DefaultConstant.WEBSEARCH_PROMPT_PATH);
        } catch (IOException e) {
            log.error("load web-search prompt failed, fallback used: path={}", DefaultConstant.WEBSEARCH_PROMPT_PATH, e);
            return WEB_SEARCH_PROMPT_FALLBACK;
        }
    }

    @Override
    public String loadPrompt(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
