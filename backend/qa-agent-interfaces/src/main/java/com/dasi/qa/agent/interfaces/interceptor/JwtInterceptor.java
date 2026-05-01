package com.dasi.qa.agent.interfaces.interceptor;

import com.dasi.qa.agent.domain.util.JwtUtil;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserContextUtil userContext;

    public JwtInterceptor(JwtUtil jwtUtil, UserContextUtil userContext) {
        this.jwtUtil = jwtUtil;
        this.userContext = userContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        String token = authorization.substring(7);
        if (!jwtUtil.isAccessTokenValid(token)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        userContext.setUserId(jwtUtil.parseUserId(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        userContext.clear();
    }
}
