package com.dasi.qa.agent.interfaces.interceptor;

import static com.dasi.qa.agent.types.constant.SystemConstant.AUTH_BEARER_PREFIX;

import com.dasi.qa.agent.domain.util.IJwtUtil;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

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
            log.error("【鉴权】缺少令牌：uri={}", request.getRequestURI());
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        String token = authorization.substring(7);
        if (!IJwtUtil.isAccessTokenValid(token)) {
            log.error("【鉴权】令牌非法：uri={}", request.getRequestURI());
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        contextUtil.setUserId(IJwtUtil.parseUserId(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        contextUtil.clear();
    }
}
