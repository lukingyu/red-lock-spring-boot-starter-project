package com.github.lukingyu.redlock.autoconfigure.aspect;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.autoconfigure.config.RedLockProperties;
import com.github.lukingyu.redlock.autoconfigure.exception.IdempotentException;
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
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ClassUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Aspect
public class IdempotentAspect {

    private static final String REQUEST_CONTEXT_HOLDER_CLASS =
            "org.springframework.web.context.request.RequestContextHolder";

    private static final String SERVLET_REQUEST_ATTRIBUTES_CLASS =
            "org.springframework.web.context.request.ServletRequestAttributes";

    private static final String LUA_SCRIPT_TEXT =
            """
            local result = redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
            if result then
                return 1
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final RedLockProperties properties;

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();
    private final RedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT_TEXT, Long.class);

    public IdempotentAspect(StringRedisTemplate redisTemplate, RedLockProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Before("@annotation(idempotent)")
    public void before(JoinPoint joinPoint, Idempotent idempotent) {
        String key = generateKey(joinPoint, idempotent);
        long expireMillis = resolveExpireMillis(idempotent);

        Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), "1", String.valueOf(expireMillis));

        if (!Long.valueOf(1L).equals(result)) {
            String message = StringUtils.hasText(idempotent.message()) ? idempotent.message() : properties.getMessage();
            throw new IdempotentException(message);
        }
    }

    private String generateKey(JoinPoint joinPoint, Idempotent idempotent) {
        String prefix = StringUtils.hasText(idempotent.prefix()) ? idempotent.prefix() : properties.getPrefix();
        String expression = resolveKeyExpression(idempotent);

        if (StringUtils.hasText(expression)) {
            return prefix + parseSpel(expression, joinPoint);
        }

        return buildWebRequestFingerprint(joinPoint)
                .map(rawKey -> prefix + DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new IllegalArgumentException(
                        "In a non-web context, @Idempotent must configure a key expression."));
    }

    private long resolveExpireMillis(Idempotent idempotent) {
        long timeout = idempotent.timeout() > 0 ? idempotent.timeout() : properties.getTimeout();
        TimeUnit timeUnit = idempotent.timeout() > 0 ? idempotent.timeUnit() : properties.getTimeUnit();
        long expireMillis = timeUnit.toMillis(timeout);
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("@Idempotent timeout must be greater than 0 milliseconds.");
        }
        return expireMillis;
    }

    @SuppressWarnings("deprecation")
    private String resolveKeyExpression(Idempotent idempotent) {
        boolean hasKey = StringUtils.hasText(idempotent.key());
        boolean hasLegacyKey = StringUtils.hasText(idempotent.spEL());
        if (hasKey && hasLegacyKey && !idempotent.key().equals(idempotent.spEL())) {
            throw new IllegalArgumentException("@Idempotent key and spEL cannot be configured at the same time.");
        }
        return hasKey ? idempotent.key() : idempotent.spEL();
    }

    private String parseSpel(String expression, JoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Object[] args = joinPoint.getArgs();

        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, args, parameterNameDiscoverer);

        Object value = expressionParser.parseExpression(expression).getValue(context);
        String key = Objects.toString(value, "");
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("@Idempotent key expression must not evaluate to empty.");
        }
        return key;
    }

    private Optional<String> buildWebRequestFingerprint(JoinPoint joinPoint) {
        return currentRequest()
                .map(request -> String.join(":",
                        request.authorization(),
                        request.method(),
                        request.uri(),
                        fingerprintArguments(joinPoint.getArgs())));
    }

    private Optional<RequestSnapshot> currentRequest() {
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        if (!ClassUtils.isPresent(REQUEST_CONTEXT_HOLDER_CLASS, classLoader)) {
            return Optional.empty();
        }

        try {
            Class<?> holderClass = ClassUtils.forName(REQUEST_CONTEXT_HOLDER_CLASS, classLoader);
            Object attributes = holderClass.getMethod("getRequestAttributes").invoke(null);
            Class<?> servletRequestAttributesClass = ClassUtils.forName(SERVLET_REQUEST_ATTRIBUTES_CLASS, classLoader);
            if (attributes == null || !servletRequestAttributesClass.isInstance(attributes)) {
                return Optional.empty();
            }

            Object request = servletRequestAttributesClass.getMethod("getRequest").invoke(attributes);
            return Optional.of(new RequestSnapshot(
                    invokeString(request, "getHeader", "Authorization"),
                    invokeString(request, "getMethod"),
                    invokeString(request, "getRequestURI")));
        }
        catch (ReflectiveOperationException | LinkageError ex) {
            return Optional.empty();
        }
    }

    private String invokeString(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return Objects.toString(method.invoke(target, args), "");
    }

    private String fingerprintArguments(Object[] args) {
        List<String> parts = new ArrayList<>(args.length);
        for (Object arg : args) {
            parts.add(fingerprintValue(arg, Collections.newSetFromMap(new IdentityHashMap<>())));
        }
        return "[" + String.join(",", parts) + "]";
    }

    private String fingerprintValue(Object value, Set<Object> visiting) {
        if (value == null) {
            return "null";
        }

        Class<?> type = value.getClass();
        if (isSimpleValueType(type)) {
            return String.valueOf(value);
        }

        if (type.isArray()) {
            int length = Array.getLength(value);
            List<String> parts = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                parts.add(fingerprintValue(Array.get(value, i), visiting));
            }
            return "[" + String.join(",", parts) + "]";
        }

        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object item : iterable) {
                parts.add(fingerprintValue(item, visiting));
            }
            return "[" + String.join(",", parts) + "]";
        }

        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(fingerprintValue(entry.getKey(), visiting) + "=" + fingerprintValue(entry.getValue(), visiting));
            }
            Collections.sort(parts);
            return "{" + String.join(",", parts) + "}";
        }

        if (isFrameworkType(type)) {
            return type.getName();
        }

        if (!visiting.add(value)) {
            return "<cycle>";
        }

        try {
            return fingerprintFields(value, type, visiting);
        }
        finally {
            visiting.remove(value);
        }
    }

    private String fingerprintFields(Object value, Class<?> type, Set<Object> visiting) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!field.isSynthetic() && !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }

        fields.sort(Comparator.comparing(Field::getName));
        List<String> parts = new ArrayList<>(fields.size());
        for (Field field : fields) {
            ReflectionUtils.makeAccessible(field);
            parts.add(field.getName() + "=" + fingerprintValue(ReflectionUtils.getField(field, value), visiting));
        }
        return type.getName() + "{" + String.join(",", parts) + "}";
    }

    private boolean isSimpleValueType(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Date.class.isAssignableFrom(type)
                || TemporalAccessor.class.isAssignableFrom(type)
                || UUID.class == type
                || URI.class == type
                || URL.class == type
                || Enum.class.isAssignableFrom(type);
    }

    private boolean isFrameworkType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("jakarta.servlet.")
                || name.startsWith("javax.servlet.")
                || name.startsWith("org.springframework.");
    }

    private record RequestSnapshot(String authorization, String method, String uri) {
    }
}
