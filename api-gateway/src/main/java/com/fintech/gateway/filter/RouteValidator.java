package com.fintech.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    // Authentication gerektirmeyen açık endpoint'ler
    public static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/forgot-password",
            "/eureka",
            "/actuator/health",
            "/actuator/info"
    );

    public boolean isOpenEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return OPEN_ENDPOINTS.stream()
                .anyMatch(openPath -> path.startsWith(openPath));
    }
}
