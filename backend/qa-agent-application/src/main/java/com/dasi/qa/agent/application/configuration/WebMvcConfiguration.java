package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.interfaces.interceptor.JwtInterceptor;
import com.dasi.qa.agent.interfaces.interceptor.RequestLoggerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    private final RequestLoggerInterceptor requestLoggerInterceptor;

    public WebMvcConfiguration(JwtInterceptor jwtInterceptor, RequestLoggerInterceptor requestLoggerInterceptor) {
        this.requestLoggerInterceptor = requestLoggerInterceptor;
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
            } else if (converter instanceof AbstractHttpMessageConverter<?> abstractConverter) {
                abstractConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/register",
                "/auth/login",
                "/auth/refresh",
                "/auth/send-verify-code",
                "/actuator/health",
                "/error"
            );

        registry.addInterceptor(requestLoggerInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
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
