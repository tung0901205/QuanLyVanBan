// File: qlda-system/api-gateway/src/main/java/com/ice/apigateway/filter/AuthRateLimitFilter.java
// THAY TOÀN BỘ FILE:

package com.ice.apigateway.filter;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limiter cho endpoint đăng nhập.
 * Dev mode: nâng lên 100 requests/phút để dễ test.
 * Production: đổi lại MAX_REQUESTS = 10.
 */
@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    // ↓ Tăng lên 100 cho môi trường dev (gốc là 10)
    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_MS   = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/auth/login")) {
            return chain.filter(exchange);
        }
        String clientIp = resolveClientIp(exchange);
        if (isRateLimited(clientIp)) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();
        Deque<Long> times = requestTimes.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= MAX_REQUESTS) {
                return true;
            }
            times.addLast(now);
            return false;
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}