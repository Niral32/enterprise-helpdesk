package com.helpdesk.ticket.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration for ticket-service.
 *
 * Two named caches:
 *   - "directoryUsers"  — keyed by userId. TTL 5m. Used by DirectoryClient
 *     when enriching ticket DTOs with creator/assignee display names.
 *   - "directoryAssets" — keyed by assetId. TTL 10m. Used by DirectoryClient
 *     when enriching ticket DTOs with linked-asset labels.
 *
 * Why these TTLs:
 *   - User profile data (name, avatar) changes rarely; 5 minutes of staleness
 *     after a profile edit is acceptable for the labels shown in a ticket
 *     list. The user can still hard-refresh to bust the cache for themselves.
 *   - Assets are even more stable, so 10 minutes is fine.
 *
 * Values are serialised as JSON (not Java native) so a Redis client like
 * redis-cli or RedisInsight can inspect the cache for debugging.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_USERS = "directoryUsers";
    public static final String CACHE_ASSETS = "directoryAssets";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        // Without polymorphic type info, deserialization of UserJson / AssetJson
        // from Redis falls back to LinkedHashMap because Jackson can't tell
        // the original concrete class from the stored JSON alone. Enabling
        // default typing embeds an "@class" field in the JSON so the same
        // POJO comes back on read. PolymorphicTypeValidator restricts what
        // can be deserialized — locked down to our DirectoryClient inner
        // classes plus the standard java.* / java.util.Optional types.
        ObjectMapper cacheMapper = objectMapper.copy();
        cacheMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.helpdesk.ticket.client.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.lang.")
            .build();
        cacheMapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(cacheMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
            .withInitialCacheConfigurations(Map.of(
                CACHE_USERS,  base.entryTtl(Duration.ofMinutes(5)),
                CACHE_ASSETS, base.entryTtl(Duration.ofMinutes(10))
            ))
            .build();
    }
}
