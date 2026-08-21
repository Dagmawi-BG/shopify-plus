package com.shopifyplus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Simple in-memory per-IP fixed-window rate limiter (the Node express-rate-limit
// equivalent). Runs before security. For multi-instance deployments this would be
// backed by Redis (Bucket4j + Spring Data Redis) — the same scale-out story as Node.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int limit;
    private final long windowMs;
    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.enabled:true}") boolean enabled,
            @Value("${app.ratelimit.limit:100}") int limit,
            @Value("${app.ratelimit.window-ms:900000}") long windowMs) {
        this.enabled = enabled;
        this.limit = limit;
        this.windowMs = windowMs;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        if (!enabled || !request.getRequestURI().startsWith("/api")) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        Window window = buckets.compute(request.getRemoteAddr(), (k, v) -> {
            if (v == null || now - v.start >= windowMs) {
                return new Window(now, 1);
            }
            v.count++;
            return v;
        });

        if (window.count > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        long start;
        int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
