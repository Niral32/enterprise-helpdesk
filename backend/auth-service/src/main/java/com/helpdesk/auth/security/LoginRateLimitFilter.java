package com.helpdesk.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limits POST /api/auth/login per source IP to mitigate brute-force
 * password-guessing attacks.
 *
 * Implementation: token-bucket via Bucket4j.
 *   - Each IP gets its own bucket holding {@value #CAPACITY} tokens.
 *   - One full refill every minute.
 *   - Each login attempt consumes one token.
 *   - 0 tokens → respond 429 Too Many Requests with a Retry-After header.
 *
 * Why per-IP and not per-email:
 *   - An attacker can rotate emails endlessly, but rotating source IPs is
 *     comparatively expensive (and visible at the load-balancer layer).
 *   - We don't want a single attacker to lock a legitimate user out by
 *     spamming their email — which a per-email bucket would allow.
 *
 * Limitations (and what I'd add next in a real prod system):
 *   - In-memory bucket — multiple auth-service instances would each have
 *     their own bucket. For a single-pod deployment that's fine; for
 *     horizontal scaling I'd back this with Redis.
 *   - Buckets aren't evicted. For very long uptimes the map could grow.
 *     A Caffeine cache with TTL eviction would solve that.
 */
@Component
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 5;            // attempts per window
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String LOGIN_PATH = "/api/auth/login";

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Bypass switch for tests — `helpdesk.security.rate-limit.enabled=false`. */
    @Value("${helpdesk.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled || !isLogin(request)) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for login from {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write(
                "{\"error\":\"Too Many Requests\","
                    + "\"message\":\"Too many login attempts. Try again in a minute.\"}");
        }
    }

    private static boolean isLogin(HttpServletRequest req) {
        return "POST".equalsIgnoreCase(req.getMethod()) && LOGIN_PATH.equals(req.getRequestURI());
    }

    /**
     * Best-effort client identifier. Prefers X-Forwarded-For (set by the
     * gateway/nginx) and falls back to the raw remote address. Note: a
     * malicious client behind a hostile proxy could forge X-Forwarded-For;
     * in real prod we'd trust only the leftmost entry from a known list
     * of proxies. For an internal helpdesk that's overkill.
     */
    private static String clientKey(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr();
    }

    private static Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.intervally(CAPACITY, WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
