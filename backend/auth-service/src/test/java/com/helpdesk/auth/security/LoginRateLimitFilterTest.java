package com.helpdesk.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * LoginRateLimitFilter tests.
 *
 * Strategy: pump 5 requests from the same IP and watch them all pass
 * (capacity = 5), then assert the 6th is blocked with 429 and never
 * reaches the downstream filter chain.
 */
class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    @Test
    @DisplayName("non-login paths bypass the filter entirely")
    void nonLoginPathsAreNotRateLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(100)).doFilter(any(), any());
    }

    @Test
    @DisplayName("login: first 5 attempts from one IP go through; 6th is 429")
    void blocksSixthAttempt() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "203.0.113.7";

        // First 5 — should pass through.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = login(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        // 6th — should be blocked.
        MockHttpServletRequest req = login(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getContentAsString()).contains("Too many login attempts");

        // The downstream chain ran 5 times — not 6.
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    @DisplayName("login: separate IPs have separate buckets")
    void perIpBucketsAreIndependent() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Exhaust IP A's quota.
        for (int i = 0; i < 5; i++) {
            filter.doFilter(login("10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blockedA = new MockHttpServletResponse();
        filter.doFilter(login("10.0.0.1"), blockedA, chain);
        assertThat(blockedA.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // IP B still has its full bucket.
        MockHttpServletResponse okB = new MockHttpServletResponse();
        filter.doFilter(login("10.0.0.2"), okB, chain);
        assertThat(okB.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("filter prefers X-Forwarded-For over the raw remote address")
    void respectsXForwardedFor() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Five requests where remoteAddr is the same load balancer but
        // X-Forwarded-For points to two different real clients.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = login("10.0.0.99");
            req.addHeader("X-Forwarded-For", "203.0.113.7");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // Sixth request from the same upstream IP — should be blocked.
        MockHttpServletRequest reqA = login("10.0.0.99");
        reqA.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse resA = new MockHttpServletResponse();
        filter.doFilter(reqA, resA, chain);
        assertThat(resA.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // But a different upstream IP shares neither the bucket nor the limit.
        MockHttpServletRequest reqB = login("10.0.0.99");
        reqB.addHeader("X-Forwarded-For", "203.0.113.8");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(reqB, resB, chain);
        assertThat(resB.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("filter is a no-op when the enabled flag is false")
    void filterCanBeDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 100; i++) {
            filter.doFilter(login("10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        verify(chain, atLeastOnce()).doFilter(any(), any());
        verify(chain, times(100)).doFilter(any(), any());
    }

    private static MockHttpServletRequest login(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }
}
