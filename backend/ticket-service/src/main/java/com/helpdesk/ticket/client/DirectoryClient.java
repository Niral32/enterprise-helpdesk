package com.helpdesk.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.helpdesk.ticket.config.CacheConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads minimal user and asset display data for ticket DTO enrichment (internal Docker network).
 */
@Component
@Slf4j
public class DirectoryClient {

    private final RestTemplate restTemplate;

    @Value("${helpdesk.user-service.base-url:http://localhost:8002}")
    private String userServiceBaseUrl;

    @Value("${helpdesk.asset-service.base-url:http://localhost:8004}")
    private String assetServiceBaseUrl;

    /**
     * Same secret as user-service {@code helpdesk.internal-api.secret} — required for
     * {@code GET /api/users/internal/{id}} (browser calls use {@code /api/users/{id}} with JWT headers).
     */
    @Value("${helpdesk.internal-api.secret:}")
    private String internalApiSecret;

    public DirectoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Cached: keyed by user id, lives in the "directoryUsers" Redis cache
     * (TTL 5m, see CacheConfig).
     *
     * Caveats:
     *   - `unless = "#result == null"` keeps transient lookup failures from
     *     being pinned in the cache for 5 minutes.
     *   - Spring Boot's auto-configured ObjectMapper includes the Jackson JDK8
     *     module, so Optional round-trips through the JSON serializer fine.
     *   - Spring caching uses proxy-based AOP, so callers must inject
     *     DirectoryClient as a bean (they all do). A self-invocation inside
     *     this class would bypass the proxy and miss the cache.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_USERS, key = "#id", unless = "#result == null")
    public Optional<UserJson> getUser(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String base = trimSlash(userServiceBaseUrl);
        String url = base + "/api/users/internal/" + id;
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            log.warn("HELPDESK internal secret not set — cannot enrich user id={} (set helpdesk.internal-api.secret)", id);
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalApiSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserJson> resp = restTemplate.exchange(url, HttpMethod.GET, entity, UserJson.class);
            return Optional.ofNullable(resp.getBody());
        } catch (RestClientException ex) {
            log.debug("Directory user lookup failed for id={}: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cached: keyed by asset id, lives in the "directoryAssets" cache (TTL 10m).
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_ASSETS, key = "#id", unless = "#result == null")
    public Optional<AssetJson> getAsset(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String url = trimSlash(assetServiceBaseUrl) + "/api/assets/" + id;
        try {
            AssetJson a = restTemplate.getForObject(url, AssetJson.class);
            return Optional.ofNullable(a);
        } catch (RestClientException ex) {
            log.debug("Directory asset lookup failed for id={}: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Batch user lookup for comment lists: id → "First Last (ROLE)".
     *
     * NOTE: This iterates and calls {@link #getUser(Long)} on `this`, which is
     * a self-invocation that BYPASSES the Spring cache proxy. The current
     * codebase doesn't call this method (the loop lives in TicketService
     * which uses an injected DirectoryClient bean and so hits the cache).
     * Kept here for API completeness; if you start calling it, restructure
     * to inject the proxy or use {@code AopContext.currentProxy()}.
     */
    public Map<Long, UserJson> getUsersBatch(Long... ids) {
        Map<Long, UserJson> out = new HashMap<>();
        if (ids == null) {
            return out;
        }
        for (Long id : ids) {
            if (id == null) continue;
            getUser(id).ifPresent(u -> out.put(id, u));
        }
        return out;
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.replaceAll("/$", "");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserJson {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private String department;
        private String profileImageUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetJson {
        private Long id;
        private String name;
        private String serialNumber;
        private String status;
    }
}
