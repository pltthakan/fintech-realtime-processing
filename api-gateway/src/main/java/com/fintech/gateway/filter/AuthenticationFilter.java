package com.fintech.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;
    private final RouteValidator routeValidator;

    public AuthenticationFilter(JwtUtil jwtUtil, RouteValidator routeValidator) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.routeValidator = routeValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Açık endpoint'leri atla (login, register vs.)
            if (routeValidator.isOpenEndpoint(request)) {
                return chain.filter(exchange);
            }

            // Authorization header kontrolü
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Authorization header eksik", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Geçersiz Authorization format", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            Claims claims;
            try {
                claims = jwtUtil.parseAccessToken(token);
            } catch (JwtException | IllegalArgumentException exception) {
                return onError(exchange, "Token geçersiz veya süresi dolmuş", HttpStatus.UNAUTHORIZED);
            }

            Long userId = jwtUtil.extractUserId(claims);
            String username = jwtUtil.extractUsername(claims);
            String userRole = jwtUtil.extractRole(claims);
            if (userId == null || username == null || username.isBlank() || userRole == null) {
                return onError(exchange, "Token kullanıcı kimliği içermiyor", HttpStatus.UNAUTHORIZED);
            }

            // Rol bazlı erişim kontrolü
            if (config.getRequiredRoles() != null && !config.getRequiredRoles().isEmpty()) {
                if (!config.getRequiredRoles().contains(userRole)) {
                    return onError(exchange, "Bu kaynağa erişim yetkiniz yok", HttpStatus.FORBIDDEN);
                }
            }

            // İstemcinin sahte kimlik header'ı göndermesini engelle ve token bilgisini ilet.
            String clientIp = request.getRemoteAddress() != null
                    && request.getRemoteAddress().getAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : null;
            ServerHttpRequest modifiedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Name");
                        headers.remove("X-User-Role");
                        headers.remove("X-Client-Ip");
                        headers.remove("X-Forwarded-For");
                        headers.set("X-User-Id", String.valueOf(userId));
                        headers.set("X-User-Name", username);
                        headers.set("X-User-Role", userRole);
                        if (clientIp != null) {
                            headers.set("X-Client-Ip", clientIp);
                        }
                    })
                    .build();

            log.info("İstek doğrulandı - User: {}, Role: {}, Path: {}",
                    username,
                    userRole,
                    request.getPath());

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.warn("Auth hatası: {} - Path: {}", message, exchange.getRequest().getPath());
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        private List<String> requiredRoles;

        public List<String> getRequiredRoles() {
            return requiredRoles;
        }

        public void setRequiredRoles(List<String> requiredRoles) {
            this.requiredRoles = requiredRoles;
        }
    }
}
