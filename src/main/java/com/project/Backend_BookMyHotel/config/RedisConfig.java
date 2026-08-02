package com.project.Backend_BookMyHotel.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json());

        // 2. Default configuration for all caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(jsonSerializer);

        // 3. Set per-cache TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("availability", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("exchange-rates", defaultConfig.entryTtl(Duration.ofHours(24)));
        // Kept fresh by @CachePut on every review write (create/delete); the TTL here is just a
        // self-healing fallback in case some future write path forgets to invalidate it.
        cacheConfigurations.put("branch-ratings", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 4. Build and return the CacheManager bean
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}