# AGENTS.md · 智修云物业报修系统（毕业设计）

## 仓库构成
- `物业报修系统-开发文档.md`：**唯一契约来源**（数据库 DDL、88 个接口、配置、后端/前端规范）。
- `数据库ER关系图.html`：15 张表 ER 关系。
- `ZhiXiu-Vue3/`：前端（Vue 3 + Vite + Element Plus），阶段一已完成并验收。
- `ZhiXiu-Cloud/`：后端（Spring Boot 3.5 + MyBatis-Plus + Redis/Redisson + RabbitMQ + MinIO + Spring AI）。

## 通用约定
- 一切实现以开发文档为准；不得新增文档未定义的接口、数据库表、字段。
- 未经明确要求不要改动 `ZhiXiu-Vue3/`；不要 `git commit`、不要切分支。
- 分阶段交付，每阶段结束停下来汇报验收。
- 回复使用中文。

## 基础设施（本机 Docker，复用已有容器，不要新建）
- MySQL `mysql8`：`localhost:3307`（root 密码见 `application-local.yml`）。
- Redis `redis-stack-roaring`：`localhost:6379`（Redis Stack，含 RediSearch）。
- MinIO `minio`：API `9000` / 控制台 `9090`。
- RabbitMQ `rabbitmq`：AMQP `5672` / 管理 `15672`，4.0.9，延迟插件 v4.0.7；先用 PowerShell 7 执行 `ZhiXiu-Cloud/docs/rabbitmq-init.ps1` 安装插件及七条死信 policy。
- 旧 RocketMQ 容器、数据、包与验收记录保留用于排空/回滚；新旧 SLA 消费者不得同时运行。正式切换见 `ZhiXiu-Cloud/docs/RABBITMQ-MIGRATION.md`。
- 使用 `docker.exe` 全路径 `C:\Program Files\Docker\Docker\resources\bin\docker.exe`。

## 后端
细节见 `ZhiXiu-Cloud/docs/BACKEND-CONVENTIONS.md`。要点：
- 全部 `jakarta.*`，禁止 `javax.*`；禁止硬编码密钥；禁止旧版本依赖。
- 业务失败 HTTP 200 + 业务码；仅 401/403 用真实 HTTP 状态码。
- 工单状态只能经状态机变更，禁止直接更新 `work_order.status`。
- 禁止简化分布式锁与事务逻辑；SSE 接口必须保持流式。
- 写文件统一 UTF-8 无 BOM、CRLF；改动后端代码后跑 `mvn -B -q compile` 验证。
- MQ 使用真实链路验收，不新增单元测试。阶段二实现/基础设施验证结束后停下来汇报，阶段三另行执行停写、排空、正式 SLA 恢复及恢复写入。
