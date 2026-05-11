package com.mythosforge.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Worker → Java 内部回调：校验 {@code X-Mythosforge-Internal-Token}。
 */
@Component
public class InternalApiTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Mythosforge-Internal-Token";

    @Value("${mythosforge.internal.api-token:}")
    private String expectedToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (expectedToken == null || expectedToken.isBlank()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return false;
        }
        String given = request.getHeader(HEADER);
        if (given == null || !expectedToken.equals(given)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        return true;
    }
}
