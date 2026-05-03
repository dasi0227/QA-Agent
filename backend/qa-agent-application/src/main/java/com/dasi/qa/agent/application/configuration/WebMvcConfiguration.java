package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.interfaces.interceptor.JwtInterceptor;
import com.dasi.qa.agent.interfaces.interceptor.RequestLoggerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    private final RequestLoggerInterceptor requestLoggerInterceptor;

    public WebMvcConfiguration(JwtInterceptor jwtInterceptor, RequestLoggerInterceptor requestLoggerInterceptor) {
        this.requestLoggerInterceptor = requestLoggerInterceptor;
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/qa-agent/api/v1/auth/register",
                "/qa-agent/api/v1/auth/login",
                "/qa-agent/api/v1/auth/refresh",
                "/qa-agent/api/v1/auth/send-verify-code",
                "/actuator/health",
                "/error"
            );

        registry.addInterceptor(requestLoggerInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/qa-agent/api/v1/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:4173",
                        "http://127.0.0.1:4173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.AUTHORIZATION)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
