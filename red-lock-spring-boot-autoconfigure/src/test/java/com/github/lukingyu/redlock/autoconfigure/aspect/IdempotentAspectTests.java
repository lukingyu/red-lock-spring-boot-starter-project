package com.github.lukingyu.redlock.autoconfigure.aspect;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.autoconfigure.config.RedLockProperties;
import com.github.lukingyu.redlock.autoconfigure.exception.IdempotentException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentAspectTests {

    @Test
    void usesBusinessKeyExpression() throws NoSuchMethodException {
        RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate(1L);

        IdempotentAspect aspect = new IdempotentAspect(redisTemplate, new RedLockProperties());
        Method method = DemoService.class.getMethod("submit", String.class);

        aspect.before(joinPoint(method, "u-1001"), method.getAnnotation(Idempotent.class));

        assertThat(redisTemplate.keys).containsExactly("idempotent:u-1001");
        assertThat(redisTemplate.args).containsExactly("1", "5000");
    }

    @Test
    void keepsLegacySpelAttributeCompatible() throws NoSuchMethodException {
        RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate(1L);

        IdempotentAspect aspect = new IdempotentAspect(redisTemplate, new RedLockProperties());
        Method method = DemoService.class.getMethod("legacySubmit", String.class);

        aspect.before(joinPoint(method, "u-1002"), method.getAnnotation(Idempotent.class));

        assertThat(redisTemplate.keys).containsExactly("idempotent:u-1002");
        assertThat(redisTemplate.args).containsExactly("1", "2000");
    }

    @Test
    void throwsConfiguredMessageWhenLockAlreadyExists() throws NoSuchMethodException {
        RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate(0L);

        IdempotentAspect aspect = new IdempotentAspect(redisTemplate, new RedLockProperties());
        Method method = DemoService.class.getMethod("legacySubmit", String.class);

        assertThatThrownBy(() -> aspect.before(joinPoint(method, "u-1002"), method.getAnnotation(Idempotent.class)))
                .isInstanceOf(IdempotentException.class)
                .hasMessage("repeat submit");
    }

    @Test
    void requiresExplicitKeyOutsideWebRequest() throws NoSuchMethodException {
        IdempotentAspect aspect = new IdempotentAspect(new RecordingStringRedisTemplate(1L), new RedLockProperties());
        Method method = DemoService.class.getMethod("withoutKey", String.class);

        assertThatThrownBy(() -> aspect.before(joinPoint(method, "u-1003"), method.getAnnotation(Idempotent.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key expression");
    }

    private JoinPoint joinPoint(Method targetMethod, Object... targetArgs) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[] {MethodSignature.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> targetMethod;
                    case "getName" -> targetMethod.getName();
                    case "getParameterTypes" -> targetMethod.getParameterTypes();
                    case "toString" -> targetMethod.toString();
                    default -> null;
                });

        return (JoinPoint) Proxy.newProxyInstance(
                JoinPoint.class.getClassLoader(),
                new Class<?>[] {JoinPoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSignature" -> signature;
                    case "getArgs" -> targetArgs;
                    case "toString" -> targetMethod + " " + Arrays.toString(targetArgs);
                    default -> null;
                });
    }

    static class RecordingStringRedisTemplate extends StringRedisTemplate {

        private final Long result;

        private List<String> keys;

        private Object[] args;

        RecordingStringRedisTemplate(Long result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.keys = keys;
            this.args = args;
            return (T) result;
        }
    }

    static class DemoService {

        @Idempotent(key = "#userId")
        public void submit(String userId) {
        }

        @SuppressWarnings("deprecation")
        @Idempotent(spEL = "#userId", timeout = 2, timeUnit = TimeUnit.SECONDS, message = "repeat submit")
        public void legacySubmit(String userId) {
        }

        @Idempotent
        public void withoutKey(String userId) {
        }
    }
}
