# Red Lock Spring Boot Starter

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)](https://adoptium.net/)
[![CI](https://github.com/lukingyu/red-lock-spring-boot-starter-project/actions/workflows/ci.yml/badge.svg)](https://github.com/lukingyu/red-lock-spring-boot-starter-project/actions/workflows/ci.yml)

Red Lock Spring Boot Starter 是一个基于 Redis 的接口防重复提交组件。你只需要在方法上添加 `@Idempotent`，组件会在业务执行前尝试写入一个带过期时间的 Redis key；如果 key 已存在，就认为请求在短时间内重复提交，并抛出 `IdempotentException`。

> 说明：本项目解决的是“幂等/防重复提交”场景，并不是 Redis 官方 Redlock 分布式锁算法的完整实现。

## 适用场景

- 表单、下单、支付确认等接口防止用户短时间重复点击。
- 消息消费、定时任务等非 Web 场景按业务 key 做幂等保护。
- 希望通过 Spring Boot Starter 快速接入 Redis 原子写入能力。

## 环境要求

- JDK 17+
- Spring Boot 3.x
- Redis 5.0+
- Maven 3.8+

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.lukingyu</groupId>
    <artifactId>red-lock-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果你还没有发布到 Maven Central，可以先在本地安装：

```bash
mvn -B clean install
```

### 2. 配置 Redis

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```

### 3. 添加注解

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping("/submit")
    @Idempotent(timeout = 5, message = "请勿重复提交订单")
    public String submit(@RequestBody OrderDTO order) {
        return "success";
    }
}
```

上面的示例会在 5 秒内阻止相同请求重复进入业务方法。Web 场景下，如果没有配置 `key`，组件会基于 `Authorization`、HTTP Method、URI 和方法参数生成请求指纹。

## 推荐用法：显式业务 key

生产环境更推荐显式指定业务 key，这样锁粒度更清晰，也能覆盖非 Web 场景。

```java
@PostMapping("/submit")
@Idempotent(key = "#order.userId + ':' + #order.orderNo", timeout = 10, message = "该订单正在处理中")
public String submit(@RequestBody OrderDTO order) {
    return orderService.submit(order);
}
```

`key` 支持 SpEL 表达式，常见写法如下：

```java
@Idempotent(key = "#userId")
@Idempotent(key = "#order.orderNo")
@Idempotent(key = "#user.id + ':' + #request.bizType")
@Idempotent(key = "#p0") // 参数名不可用时可以使用索引
```

早期版本中的 `spEL` 属性仍然兼容，但新代码建议使用 `key`。

## 非 Web 场景

消息消费、定时任务、异步任务没有 HTTP 请求上下文，必须显式指定 `key`：

```java
@Service
public class OrderMessageConsumer {

    @Idempotent(key = "#messageId", timeout = 30)
    public void handle(String messageId) {
        // consume message
    }
}
```

如果非 Web 场景没有配置 `key`，组件会抛出 `IllegalArgumentException`，避免生成不可控的锁 key。

## 配置项

```yaml
red-lock:
  enabled: true
  prefix: "idempotent:"
  timeout: 5
  time-unit: seconds
  message: "操作太快，请稍后再试"
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `red-lock.enabled` | `true` | 是否启用自动配置 |
| `red-lock.prefix` | `idempotent:` | Redis key 前缀 |
| `red-lock.timeout` | `5` | 默认锁过期时间 |
| `red-lock.time-unit` | `seconds` | 默认锁过期时间单位 |
| `red-lock.message` | `操作太快，请稍后再试` | 重复提交时的异常消息 |

注解上的 `prefix`、`timeout`、`timeUnit`、`message` 会覆盖全局配置。

## 示例项目

本仓库包含一个可运行示例：

```bash
mvn -pl red-lock-sample -am spring-boot:run
```

提交请求：

```bash
curl -X POST "http://localhost:8080/redlock/orders/submit-by-business-key" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"O202605080001","userId":"U1001","skuIds":["SKU-1","SKU-2"]}'
```

在锁过期前重复提交相同业务 key，会得到 `IdempotentException` 对应的错误。

## 模块结构

```text
red-lock-spring-boot-starter-project
├── red-lock-spring-boot-starter          # 用户引入的 starter 入口
├── red-lock-spring-boot-autoconfigure    # 自动配置、注解、AOP 切面
└── red-lock-sample                       # 可运行示例项目
```

## 实现原理

组件使用 Redis Lua 脚本执行 `SET key value NX PX ttl`：

- `NX` 保证 key 不存在时才写入。
- `PX` 设置毫秒级过期时间。
- Lua 脚本保证判断和写入在 Redis 侧原子执行。

当写入成功时，业务方法继续执行；当写入失败时，说明锁已存在，组件会抛出 `IdempotentException`。

## 开发与验证

```bash
mvn -B verify
```

本项目使用 GitHub Actions 在 push 和 pull request 时运行 Maven 构建。欢迎提交 Issue 或 Pull Request。

## License

Red Lock Spring Boot Starter is released under the [Apache License 2.0](LICENSE).
