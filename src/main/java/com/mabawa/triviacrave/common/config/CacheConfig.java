package com.mabawa.triviacrave.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default TTL of 1 hour
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(Object.class)))
                .disableCachingNullValues();

        // Specific cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Categories cache - 6 hours (rarely change)
        cacheConfigurations.put("categories", defaultCacheConfig.entryTtl(Duration.ofHours(6)));
        
        // Questions cache - 2 hours
        cacheConfigurations.put("questions", defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        // User stats cache - 30 minutes
        cacheConfigurations.put("userStats", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        
        // User profiles cache - 1 hour
        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        
        // Game history cache - 15 minutes
        cacheConfigurations.put("gameHistory", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Active games cache - 5 minutes
        cacheConfigurations.put("activeGames", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Leaderboards cache - 10 minutes
        cacheConfigurations.put("leaderboards", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}