package com.dasi.qa.agent.interfaces.interceptor;

import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IJwtUtil;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_BEARER_PREFIX = "Bearer ";

    private final IJwtUtil IJwtUtil;
    private final IContextUtil contextUtil;

    public JwtInterceptor(IJwtUtil IJwtUtil, IContextUtil contextUtil) {
        this.IJwtUtil = IJwtUtil;
        this.contextUtil = contextUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(AUTH_BEARER_PREFIX)) {
            log.error("【鉴权】请求缺少令牌: uri={}", request.getRequestURI());
            throw new ApiException(ResultCode.UNAUTHORIZED, "请先登录后继续操作");
        }
        String token = authorization.substring(7);
        if (!IJwtUtil.isAccessTokenValid(token)) {
            log.error("【鉴权】令牌校验失败: uri={}", request.getRequestURI());
            throw new ApiException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        String userId = IJwtUtil.parseUserId(token);
        if (!StringUtils.hasText(userId)) {
            log.error("【鉴权】令牌缺少用户标识: uri={}", request.getRequestURI());
            throw new ApiException(ResultCode.UNAUTHORIZED, "登录状态异常，请重新登录");
        }
        contextUtil.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        contextUtil.clear();
    }
}
