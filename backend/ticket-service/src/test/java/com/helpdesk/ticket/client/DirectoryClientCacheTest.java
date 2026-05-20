package com.helpdesk.ticket.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that @Cacheable on DirectoryClient actually short-circuits the
 * underlying RestTemplate call on the second invocation.
 *
 * Why an integration test (not a unit test): @Cacheable is a Spring AOP
 * proxy concern, so it only fires when the bean is created by the Spring
 * container. A plain `new DirectoryClient(...)` skips the proxy entirely
 * and the cache annotations become no-ops.
 *
 * Why we swap in a ConcurrentMapCacheManager: we don't want every test
 * run to require a live Redis container — that's CI hostility. The cache
 * abstraction is the same; only the backing store changes.
 */
@SpringBootTest(
    classes = {
        DirectoryClient.class,
        DirectoryClientCacheTest.TestCacheConfig.class
    },
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "helpdesk.user-service.base-url=http://user-service:8002",
        "helpdesk.asset-service.base-url=http://asset-service:8004",
        "helpdesk.internal-api.secret=test-secret"
    }
)
@TestPropertySource(properties = "spring.main.web-application-type=none")
class DirectoryClientCacheTest {

    @MockBean private RestTemplate restTemplate;
    @Autowired private DirectoryClient directoryClient;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void resetCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var c = cacheManager.getCache(name);
            if (c != null) c.clear();
        });
    }

    @Test
    @DisplayName("getUser: second call with same id is served from cache (no extra REST call)")
    void getUser_secondCallHitsCache() {
        DirectoryClient.UserJson payload = new DirectoryClient.UserJson();
        payload.setId(42L);
        payload.setEmail("u@x.com");
        payload.setFirstName("Foo");
        payload.setLastName("Bar");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class)))
            .thenReturn(ResponseEntity.ok(payload));

        Optional<DirectoryClient.UserJson> first = directoryClient.getUser(42L);
        Optional<DirectoryClient.UserJson> second = directoryClient.getUser(42L);
        Optional<DirectoryClient.UserJson> third = directoryClient.getUser(42L);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(third).isPresent();
        assertThat(second.get().getEmail()).isEqualTo("u@x.com");
        // Only the first call hits the underlying RestTemplate.
        verify(restTemplate, times(1))
            .exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class));
    }

    @Test
    @DisplayName("getUser: distinct ids cause distinct upstream calls")
    void getUser_distinctIdsAreCachedSeparately() {
        DirectoryClient.UserJson u1 = new DirectoryClient.UserJson();
        u1.setId(1L);
        DirectoryClient.UserJson u2 = new DirectoryClient.UserJson();
        u2.setId(2L);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class)))
            .thenReturn(ResponseEntity.ok(u1), ResponseEntity.ok(u2));

        directoryClient.getUser(1L);
        directoryClient.getUser(2L);
        directoryClient.getUser(1L); // cache hit
        directoryClient.getUser(2L); // cache hit

        verify(restTemplate, times(2))
            .exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class));
    }

    @Test
    @DisplayName("getUser: an empty/failed lookup is NOT cached (so transient failures don't stick)")
    void getUser_emptyResultsNotCached() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class)))
            .thenReturn(ResponseEntity.ok(null));

        directoryClient.getUser(99L);
        directoryClient.getUser(99L);
        directoryClient.getUser(99L);

        // 3 calls because empty results bypass the cache via `unless = ...`.
        verify(restTemplate, times(3))
            .exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(DirectoryClient.UserJson.class));
    }

    @Test
    @DisplayName("getAsset: second call with same id is served from cache")
    void getAsset_secondCallHitsCache() {
        DirectoryClient.AssetJson a = new DirectoryClient.AssetJson();
        a.setId(7L);
        a.setName("Dell Latitude");
        when(restTemplate.getForObject(any(String.class), eq(DirectoryClient.AssetJson.class)))
            .thenReturn(a);

        directoryClient.getAsset(7L);
        directoryClient.getAsset(7L);

        verify(restTemplate, times(1))
            .getForObject(any(String.class), eq(DirectoryClient.AssetJson.class));
    }

    @org.springframework.context.annotation.Configuration
    @EnableCaching
    static class TestCacheConfig {
        @Bean
        @Primary
        CacheManager testCacheManager() {
            // ConcurrentMap is the same cache abstraction Spring uses for
            // Redis — just backed by a HashMap instead. Lets the cache
            // semantics be tested without a Redis dependency in CI.
            return new ConcurrentMapCacheManager("directoryUsers", "directoryAssets");
        }
    }
}
