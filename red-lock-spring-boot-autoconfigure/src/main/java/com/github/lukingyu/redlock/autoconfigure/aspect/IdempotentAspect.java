package com.github.lukingyu.redlock.autoconfigure.aspect;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.autoconfigure.config.RedLockProperties;
import com.github.lukingyu.redlock.autoconfigure.exception.IdempotentException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Aspect
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final RedLockProperties properties;

    // SpEL 解析器
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    // LUA脚本：判断key是否存在，不存在则设置并设置过期时间
    private static final String LUA_SCRIPT_TEXT =
        """
        if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then
           redis.call('expire', KEYS[1], ARGV[2])
           return 1
        else
           return 0
        end
        """;
    
    private final RedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT_TEXT, Long.class);

    @Before("@annotation(idempotent)")
    public void before(JoinPoint joinPoint, Idempotent idempotent) {
        // 1. 获取请求参数，生成 Key
        String key = generateKey(joinPoint, idempotent);

        long timeOut = idempotent.timeout() != -1 ? idempotent.timeout() : properties.getTimeout();
        TimeUnit timeUnit = idempotent.timeUnit() != TimeUnit.NANOSECONDS ? idempotent.timeUnit() : properties.getTimeUnit();

        // 2. 执行 Lua 脚本
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), "1", String.valueOf(timeUnit.toSeconds(timeOut)));

        // 3. 判断结果：0表示锁已存在
        if (result == 0) {
            String message = StringUtils.hasText(idempotent.message()) ? idempotent.message() : properties.getMessage();
            throw new IdempotentException(message);
        }
    }

    /**
     * Key生成策略：前缀 + MD5(Token + 请求参数 + 请求路径)
     * 确保同一个用户，对同一个接口，用同样的参数，在短时间内只能调一次
     */
    private String generateKey(JoinPoint joinPoint, Idempotent idempotent) {

        final String prefix = StringUtils.hasText(idempotent.prefix()) ? idempotent.prefix() : properties.getPrefix();
        //目前只支持注解传入
        final String spEL = idempotent.spEL();

        // 用户使用了 SpEL 表达式 去获取自定义Key
        if (StringUtils.hasText(spEL)) {
            return prefix + parseSpel(spEL, joinPoint);
        }

        // 用户没配置 Key，尝试自动使用 Web 环境的 URL + Token
        // 先判断是否为 Web 环境，防止非 Web 项目报错
        if (RequestContextHolder.getRequestAttributes() != null) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            // 实际场景建议取 Authorization Header 或 UserID
            String token = request.getHeader("Authorization");
            String method = request.getMethod();
            String uri = request.getRequestURI();
            // 简单的将参数toString
            String args = java.util.Arrays.toString(joinPoint.getArgs());

            String rawKey = token + ":" + method + ":" + uri + ":" + args;
            // MD5加密缩短Key长度
            return prefix + DigestUtils.md5DigestAsHex(rawKey.getBytes());
        }

        // 既没有 SpEL，又不是 Web 环境 -> 抛出异常，提示用户必须配置 key
        throw new IllegalArgumentException("在非Web环境下，@Idempotent 注解必须配置 spEL 属性！");
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpel(String el, JoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Object[] args = joinPoint.getArgs();

        // 创建 SpEL 上下文
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, args, parameterNameDiscoverer);

        // 解析并返回字符串结果
        return expressionParser.parseExpression(el).getValue(context, String.class);
    }
}