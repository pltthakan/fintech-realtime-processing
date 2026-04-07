package com.fintech.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Order(-1)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = determineStatus(ex);
        String path = exchange.getRequest().getPath().value();

        log.error("Gateway hatası - Path: {}, Error: {}", path, ex.getMessage());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorBody = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", getReadableMessage(ex),
                "path", path
        );

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            return exchange.getResponse().writeWith(
                    Mono.just(bufferFactory.wrap(bytes))
            );
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }

    private HttpStatus determineStatus(Throwable ex) {
        String exName = ex.getClass().getSimpleName();
        return switch (exName) {
            case "ResponseStatusException" -> HttpStatus.NOT_FOUND;
            case "ServiceUnavailableException" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "TimeoutException" -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String getReadableMessage(Throwable ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Connection refused")) {
            return "İlgili servis şu an erişilebilir değil. Lütfen tekrar deneyin.";
        }
        if (ex.getMessage() != null && ex.getMessage().contains("No instances available")) {
            return "İstenen servis bulunamadı. Servis kayıtlı değil.";
        }
        return ex.getMessage() != null ? ex.getMessage() : "Bilinmeyen bir hata oluştu.";
    }
}
