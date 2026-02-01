package com.github.lukingyu.redlock.autoconfigure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@Data
@ConfigurationProperties(prefix = "red-lock") // 对应 application.yml 中的 red-lock
public class RedLockProperties {

    private String prefix = "idempotent:";

    private Long timeout = 5L;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private String message = "操作太快，请稍后再试";
}