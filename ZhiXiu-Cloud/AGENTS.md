# AGENTS.md · 后端（ZhiXiu-Cloud）

详细规范见 `docs/BACKEND-CONVENTIONS.md`，本文件只列红线与验证方式。

## 红线（业主明确要求）
1. 禁止自行新增开发文档未定义的接口、数据库表、字段。
2. 禁止 `javax`，全部使用 `jakarta`。
3. 禁止硬编码密钥、api-key、密码（统一走环境变量或 `application-local.yml`）。
4. 禁止绕过工单状态机直接更新 `work_order.status`。
5. 禁止简化分布式锁、事务逻辑。
6. 禁止把 SSE 接口改成一次性返回。
7. 禁止使用旧版本依赖（版本集中在 `pom.xml` properties 中维护）。

## 验证
- 编译：`mvn -B -q compile`
- 打包：`mvn -B clean package -DskipTests`（项目不写单元测试）
- 冒烟：mvn spring-boot:run 后访问 Knife4j 文档页 http://localhost:8080/doc.html（元数据 /v3/api-docs）
- 本机跑通需在 项目根目录 `application-local.yml` 提供 `DB_PASSWORD`、`JWT_SECRET`、`RABBITMQ_PASSWORD`（该文件已 gitignore）；数据库默认连本机 Docker 的 `zhixiu` 库（3307 端口）。

## 约定速查
- 响应体恒为 `{code, message, data}`；分页 `total/pages/current/size/records`。
- 包装 `Long`（ID）序列化为字符串，基本 `long` 保持数字。
- Controller 只做校验、鉴权、调 Service；Mapper 不写业务。
- Redis Key 走 `RedisKeys` 常量；时间格式 `yyyy-MM-dd HH:mm:ss`（GMT+8）。

## RabbitMQ 与迁移
- 详细操作见 `docs/RABBITMQ-MIGRATION.md`；复用现有 rabbitmq，不创建/重建容器，不自动删除冲突队列。
- 先用 PowerShell 7 执行 `pwsh -File docs/rabbitmq-init.ps1` 安装延迟插件 v4.0.7 和七条精确死信 policy。
- 应用依次声明 Exchange、Queue、Binding，再用管理 API 核验有效 policy，之后开启发送门卫与监听器；插件、拓扑或 policy 异常必须启动失败。
- 使用 `RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST` 和 `RABBITMQ_MANAGEMENT_URL`，密码不硬编码，JVM 统一 Asia/Shanghai。
- 所有队列 quorum，工单 SAC，首版全部并发 1、prefetch 1；MANUAL ACK，业务事务外同次投递重试三次后 reject 进入独立 DLQ。
- 保留事务边界、状态机、分布式锁和 SSE，真实链路验收，不新增单元测试。阶段二结束停下汇报，阶段三才执行正式存量切换，新旧 SLA 消费者不得同时运行。
- 独立 `tools/mq-recovery` 仅 SELECT 原数据库期限，默认 preview，显式 publish 才补投；主应用禁止恢复 RocketMQ 双客户端依赖。
