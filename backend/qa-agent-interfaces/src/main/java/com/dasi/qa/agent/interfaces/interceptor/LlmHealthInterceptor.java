package com.dasi.qa.agent.interfaces.interceptor;

import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.LlmConfigException;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

import static com.dasi.qa.agent.types.constant.RedisConstant.LLM_HEALTH_KEY;

@Component
@Slf4j
public class LlmHealthInterceptor implements HandlerInterceptor {

    private static final Duration CACHE_TTL = Duration.ofMinutes(60);

    private final StringRedisTemplate redisTemplate;
    private final UserLlmModelProvider userLlmModelProvider;
    private final IContextUtil contextUtil;

    public LlmHealthInterceptor(StringRedisTemplate redisTemplate,
                                UserLlmModelProvider userLlmModelProvider,
                                IContextUtil contextUtil) {
        this.redisTemplate = redisTemplate;
        this.userLlmModelProvider = userLlmModelProvider;
        this.contextUtil = contextUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String userId = contextUtil.getUserId();
        String cacheKey = LLM_HEALTH_KEY + userId;
        if (redisTemplate.opsForValue().get(cacheKey) != null) {
            return true;
        }
        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
        try {
            String llmResponse = userModel.chat("hi");
            if (!StringUtils.hasText(llmResponse)) {
                throw new LlmConfigException(ResultCode.LLM_NOT_CONFIGURED, "LLM 响应异常，请检查接入配置");
            }
            redisTemplate.opsForValue().set(cacheKey, "1", CACHE_TTL);
            return true;
        } catch (LlmConfigException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("【LLM 健康检查】失败: userId={}", userId, exception);
            throw new LlmConfigException(ResultCode.LLM_NOT_CONFIGURED, "LLM 连接测试失败，请检查接入配置");
        }
    }
}