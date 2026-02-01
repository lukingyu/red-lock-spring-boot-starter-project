package com.github.lukingyu.redlock.autoconfigure.config;

import com.github.lukingyu.redlock.autoconfigure.aspect.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, DispatcherServletAutoConfiguration.class})
@EnableConfigurationProperties(RedLockProperties.class)
public class RedLockAutoConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate, RedLockProperties redLockProperties) {
        return new IdempotentAspect(redisTemplate, redLockProperties);
    }
}