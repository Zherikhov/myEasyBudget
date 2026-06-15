package com.myeasybudget.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Best-effort, per-instance fixed-window rate limiter for the public auth endpoints.
 *
 * <p>This guards against casual brute-force / credential-stuffing. It is intentionally
 * in-memory and therefore <strong>per application instance</strong>: behind multiple
 * instances or a load balancer it should be replaced (or backed) by a shared store such
 * as Redis. It is not a substitute for an edge/WAF rate limit.
 */
public class AuthRateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/resend-verification");
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitingFilter(ObjectMapper objectMapper, int maxRequests, long windowSeconds) {
        this.objectMapper = objectMapper;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && LIMITED_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isLimited(clientKey(request))) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLimited(String key) {
        long currentWindow = Instant.now().getEpochSecond() / windowSeconds;

        // Crude unbounded-growth guard for a long-running instance.
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.clear();
        }

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new Window(currentWindow);
            }
            return existing;
        });
        return window.count.incrementAndGet() > maxRequests;
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return request.getRequestURI() + "|" + ip;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later.");
        problem.setTitle("Too many requests");
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private static final class Window {
        private final long window;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long window) {
            this.window = window;
        }
    }
}
