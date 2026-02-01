<div align="center">
    <img src="https://socialify.git.ci/lukingyu/red-lock-spring-boot-starter-project/image?description=1&font=Inter&language=1&name=1&owner=1&pattern=Circuit%20Board&theme=Light" alt="Red-Lock Banner" width="100%"/>

# 🔒 Red-Lock Spring Boot Starter

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)]()
[![Redis](https://img.shields.io/badge/Redis-Lua-red.svg)]()

**一款基于 Redis + Lua 脚本的高性能、轻量级分布式幂等/防重组件。**
<br/>
专为 Spring Boot 3.x 设计，开箱即用。

[快速开始](#-快速开始-quick-start) • [功能特性](#-功能特性-features) • [进阶使用](#-进阶使用-advanced-usage) • [项目结构](#-项目结构-architecture)

</div>

---

## 📖 简介

在微服务架构或高并发 Web 场景中，**接口重复提交**（用户手抖、网络重试）是常见痛点。虽然可以使用数据库唯一索引兜底，但会对数据库造成不必要的压力。

**Red-Lock** 提供了一个优雅的解决方案：
通过注解 `@Idempotent`，利用 **Redis 原子性 Lua 脚本**，在业务执行前建立“锁屏障”。支持 **SpEL 表达式** 动态定义锁粒度，同时兼容 **Web** 与 **非Web** 环境。

## ✨ 功能特性

*   ⚡ **极致轻量**：核心代码仅依赖 `spring-data-redis`，无其他重型依赖。
*   🚀 **原子性保障**：底层采用 Lua 脚本执行 `SETNX + EXPIRE`，彻底避免并发竞态条件 (Race Condition)。
*   🔧 **SpEL 表达式支持**：支持 `#order.id`、`#user.name` 等 SpEL 语法，灵活定义锁的 Key。
*   🌐 **全场景兼容**：
    *   **Web 环境**：自动解析 Token、URL、参数生成指纹 Key。
    *   **非 Web 环境**（MQ/定时任务）：强制要求自定义 Key，防止报错。
*   📦 **现代化架构**：基于 Maven 多模块扁平化设计，符合 Spring Boot Starter 官方标准规范。

---

## 🚀 快速开始

### 1. 引入依赖
在你的 Spring Boot 项目 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.github.lukingyu</groupId>
    <artifactId>red-lock-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```
### 2. 配置 Redis

保持自动配置的默认配置 或者 在 application.yml 中配置了 Redis（如果你已经配了，可跳过）：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```      
### 3. 使用注解
**web场景**
在 Controller 接口上添加 @Idempotent 注解即可：

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping("/create")
    // 默认策略：5秒内，同一用户、同一接口、同一参数只能调用一次
    @Idempotent(timeout = 5, message = "您的手速太快了，请休息一下") 
    public Result createOrder(@RequestBody OrderDTO order) {
        return orderService.create(order);
    }
}
```
## 🛠️ 进阶使用
### 1. 自定义业务 Key (SpEL 表达式)

如果你想根据业务 ID（如订单号）进行防重，而不是根据 URL，可以使用 SpEL 表达式：

```java
// 锁的粒度仅限于 orderId，不同订单号互不影响
@Idempotent(key = "#order.orderId", timeout = 10)
public Result submit(@RequestBody OrderDTO order) {
    // ...
}
```
### 2. 多级 Key 组合

支持复杂的 SpEL 组合：

```java
@Idempotent(key = "#user.id + '_' + #type")
public void logic(User user, String type) {
    // ...
}
```
### 3. 非 Web 环境支持

在 MQ 消费者或定时任务中使用时，必须指定 key 属性，否则会抛出异常（设计上的安全保护）：

```java
@Component
public class OrderConsumer {
    
    @Idempotent(key = "#message.id") // 必须指定 Key
    @RabbitListener(queues = "order.queue")
    public void handle(OrderMessage message) {
        // 消费逻辑...
    }
}
```
## ⚙️ 配置参数

虽然默认配置已足够使用，但你也可以在 application.yml 中微调：

|         属性	          |       默认值	       |      说明       |
|:--------------------:|:----------------:|:-------------:|
| @Idempotent.prefix	  |   idempotent:	   | Redis Key 的前缀 |
| @Idempotent.timeout	 |        5	        |   锁过期时间（秒）    |
| @Idempotent.timtUnit | TimeUnit.SECONDS |     时间单位      |
| @Idempotent.message	 |   操作太快，请稍后再试	    | 触发限流时的异常提示信息  |
## 🏛️ 项目结构

本项目采用标准的 Maven 多模块架构，符合开源规范：

```text
red-lock-spring-boot-starter-project (Root)
├── red-lock-spring-boot-starter       # [启动器] 用户依赖的入口，只包含 pom
├── red-lock-spring-boot-autoconfigure # [核心] 包含切面、自动配置、注解逻辑
└── red-lock-sample                    # [示例] 用于演示和集成的 Web Demo 项目
```
分离设计：Starter 与 Autoconfigure 分离，遵循 Spring 官方最佳实践。

依赖管理：通过 Optional 依赖 Web 模块，实现对 Web/Non-Web 环境的智能兼容。

## 📝 环境要求

JDK: 17 +

Spring Boot: 3.0 +

Redis: 5.0 +

## 🤝 贡献与交流

欢迎提交 Issue 或 Pull Request。如果你觉得这个项目对你有帮助，请给一个 ⭐️ Star！

Copyright © 2026 YourName. Released under the Apache 2.0 License.


---

