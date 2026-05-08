package com.github.lukingyu.redlock.autoconfigure.config;

import com.github.lukingyu.redlock.autoconfigure.aspect.IdempotentAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({StringRedisTemplate.class, Aspect.class})
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = RedLockProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedLockProperties.class)
public class RedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate, RedLockProperties redLockProperties) {
        return new IdempotentAspect(redisTemplate, redLockProperties);
    }
}
