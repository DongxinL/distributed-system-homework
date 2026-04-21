package com.example.gateway.config;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class GatewayConfig {

    @Bean
    @RefreshScope
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String headerIp = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (headerIp != null && !headerIp.isBlank()) {
            return headerIp.split(",")[0].trim();
        }
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown");
    }
}