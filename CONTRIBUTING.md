# Contributing

感谢你愿意改进 Red Lock Spring Boot Starter。

## 开发流程

1. Fork 仓库并基于 `master` 创建分支。
2. 提交前运行：

```bash
mvn -B verify
```

3. 提交 Pull Request 时请说明变更背景、主要修改点和验证方式。

## 代码约定

- 保持 JDK 17 与 Spring Boot 3.x 兼容。
- starter 模块只负责用户入口依赖，自动配置逻辑放在 `red-lock-spring-boot-autoconfigure`。
- 新增功能尽量补充单元测试或 sample 示例。
- 不提交 `target/`、IDE 配置等生成文件。
