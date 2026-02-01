package com.github.lukingyu.redlock.autoconfigure.aspect;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.autoconfigure.exception.IdempotentException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;

@Aspect
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;

    // LUA脚本：判断key是否存在，不存在则设置并设置过期时间
    private static final String LUA_SCRIPT_TEXT = 
        "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
        "   redis.call('expire', KEYS[1], ARGV[2]) " +
        "   return 1 " +
        "else " +
        "   return 0 " +
        "end";
    
    private final RedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT_TEXT, Long.class);

    @Before("@annotation(idempotent)")
    public void before(JoinPoint joinPoint, Idempotent idempotent) {
        // 1. 获取请求参数，生成 Key
        String key = generateKey(joinPoint, idempotent);
        
        // 2. 执行 Lua 脚本
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), "1", String.valueOf(idempotent.timeUnit().toSeconds(idempotent.timeout())));

        // 3. 判断结果：0表示锁已存在
        if (result == 0) {
            throw new IdempotentException(idempotent.message());
        }
    }

    /**
     * Key生成策略：前缀 + MD5(Token + 请求参数 + 请求路径)
     * 确保同一个用户，对同一个接口，用同样的参数，在短时间内只能调一次
     */
    private String generateKey(JoinPoint joinPoint, Idempotent idempotent) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        // 实际场景建议取 Authorization Header 或 UserID
        String token = request.getHeader("Authorization"); 
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String args = java.util.Arrays.toString(joinPoint.getArgs());
        
        // 组合成字符串
        String rawKey = token + ":" + method + ":" + uri + ":" + args;
        // MD5加密缩短Key长度
        return idempotent.prefix() + DigestUtils.md5DigestAsHex(rawKey.getBytes());
    }
}