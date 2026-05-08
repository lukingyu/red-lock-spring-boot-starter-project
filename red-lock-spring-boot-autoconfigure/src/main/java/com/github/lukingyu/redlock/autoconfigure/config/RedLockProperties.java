package com.github.lukingyu.redlock.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = RedLockProperties.PREFIX)
public class RedLockProperties {

    public static final String PREFIX = "red-lock";

    /**
     * Whether the idempotent aspect is enabled.
     */
    private boolean enabled = true;

    /**
     * Redis key prefix.
     */
    private String prefix = "idempotent:";

    /**
     * Default lock expiration.
     */
    private long timeout = 5L;

    /**
     * Default lock expiration unit.
     */
    private TimeUnit timeUnit = TimeUnit.SECONDS;

    /**
     * Default message when a duplicate operation is detected.
     */
    private String message = "操作太快，请稍后再试";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
