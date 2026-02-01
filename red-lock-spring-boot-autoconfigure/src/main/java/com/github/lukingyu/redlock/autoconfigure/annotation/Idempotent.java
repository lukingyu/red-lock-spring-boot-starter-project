package com.github.lukingyu.redlock.autoconfigure.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等Key的前缀，区分业务
     */
    String prefix() default "idempotent:";

    /**
     * 支持 SpEL 表达式
     * 例如："#order.id" 或 "#userId"
     * 如果为空，且在Web环境下，则自动计算MD5
     */
    String spEL() default "";


    /**
     * 过期时间（默认5秒内不允许重复提交）
     */
    long timeout() default 5;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 提示信息
     */
    String message() default "操作太快，请稍后再试";
}
