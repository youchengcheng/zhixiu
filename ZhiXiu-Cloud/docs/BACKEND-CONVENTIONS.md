# 后端统一规范（ZhiXiu-Cloud）

> 契约唯一来源：仓库根目录 `物业报修系统-开发文档.md`（数据库 DDL、98 个接口、枚举字典、Redis/MQ/AI 规范）。
> 本文件只规定「怎么写」，不定义「写什么」。两者冲突时以开发文档为准。

## 一、分层与职责

| 层 | 允许 | 禁止 |
|---|---|---|
| `controller` | `@Valid` 校验、`@PreAuthorize` 权限、调用 Service、返回 `Result` | 业务逻辑、直接调用 Mapper |
| `service` / `service.impl` | 业务规则、事务、状态机、发 MQ、调用 Mapper | 出现 `HttpServletRequest` / `HttpServletResponse` |
| `mapper` | 继承 `BaseMapper`，复杂 SQL 写 `resources/mapper/*.xml` | 业务判断 |
| `entity` | 与 16 张表一一对应 | 直接作为接口返回值 |
| `request` / `response` | `XxxRequestVO` / `XxxResponseVO`，一个接口一组 | 复用实体当出入参 |
| `config` / `security` | 基础设施配置 | 业务代码 |

包内文件顺序固定：类注释 → 注解 → 字段 → 构造/静态方法 → public 方法 → private 方法。

## 二、命名

| 类型 | 规则 | 示例 |
|---|---|---|
| 实体 | 表名驼峰 | `WorkOrder`、`AiChatMessage` |
| 接口入参 / 出参 | `XxxRequestVO` / `XxxResponseVO` | `OwnerRegisterRequestVO` |
| 服务 | `XxxService` / `XxxServiceImpl` | `PropertyServiceImpl` |
| 枚举 | `XxxEnum`，放 `common/enums` | `OrderStatusEnum` |
| REST 路径 | 小写 + 中划线，前缀 `/api` | `/api/owner/order/{id}/confirm` |
| 常量 | 全大写下划线，Redis Key 必须走 `RedisKeys` | `RedisKeys.TOKEN_BLACKLIST` |

## 三、接口契约

- 统一响应体恒为 `{ code, message, data }`（`Result`），不新增字段（`isSuccess()` 已 `@JsonIgnore`）。
- **业务失败返回 HTTP 200 + 业务码**；仅 401（未认证）与 403（越权）返回真实 HTTP 状态码，由 `AuthenticationEntryPointImpl` / `AccessDeniedHandlerImpl` 输出。
- 分页：请求 `pageNum`（默认 1）/ `pageSize`（默认 10），响应 `PageResult` 的 `total / pages / current / size / records`，单页上限 200。
- 时间统一 `yyyy-MM-dd HH:mm:ss`（GMT+8，见 `JacksonConfig`）。
- **包装类型 `Long`（ID）序列化为字符串**，基本类型 `long`（计数、耗时、`expiresIn`）保持数字。新增出参字段时按此选择类型。
- 所有 @Tag / @Operation 写中文，@Schema 标注字段含义。@Tag 名称会直接显示在 Knife4j 文档页上。

## 四、异常与错误码

- 可预期业务失败一律 `throw BizException.of(ResultCode.XXX)`，不自造错误码；错误码取值见 `ResultCode`（与开发文档 1.3 一一对应）。
- 兜底异常由 `GlobalExceptionHandler` 处理，Controller 不写 `try-catch`。
- 日志脱敏：手机号、身份证、密码不打印明文；认证接口不加 `@ApiLog`。

## 五、事务、并发与状态机

- 写操作加 `@Transactional(rollbackFor = Exception.class)`；跨表写入（如师傅注册、设默认房产）必须在同一事务内。
- 工单状态**只能经 `WorkOrderStateMachine` 变更**；禁止任何 `updateById` / `lambdaUpdate` 直接改 `work_order.status`。
- 乐观锁：`work_order.version` 用 `@Version`；并发改状态失败即重试或报 409，不做「后写覆盖」。
- 分布式锁：抢单/派单用 Redisson `tryLock(0, 10, TimeUnit.SECONDS)`，`finally` 内先 `isHeld()` 再 `unlock()`；锁 Key 取 `RedisKeys.GRAB_LOCK` / `RedisKeys.ASSIGN_LOCK`。
- 状态流转、流转日志落库、MQ 事件投递在同一事务内，禁止「先发消息后落库」。

## 六、缓存与消息

- Redis Key 全部由 `RedisKeys` 产出，禁止在业务代码写字符串字面量；TTL 与开发文档「五、Redis 使用清单」一致。
- Token 双写：JWT 给前端、`auth:token:{token}` 给服务端校验；登出写 `auth:blacklist:{token}` 并删除登记。
- MQ Exchange/Routing Key/Queue 常量集中放 `common/constant/MqConstants`，保留原 Topic/Tag/消费组名称；SLA 用延迟插件，不降级为轮询。

### 6.1 RabbitMQ 4.0 与发送/消费

- 主应用仅 `spring-boot-starter-amqp`，由 Spring Boot 3.5.16 BOM 管理；RocketMQ 双客户端只允许存在独立恢复 CLI。
- 五类 DTO 不变，`MqMessageCodec` 使用原 ObjectMapper 编解码 UTF-8 JSON byte[]；消息设置 application/json、UTF-8、PERSISTENT、逻辑 messageId。
- 普通模板 mandatory=true，SLA 模板 mandatory=false；独立 CorrelationData，等待 confirm 最多 3 秒。普通 ACK 且无 Return 才成功；发送失败保持原事务回滚、捕获告警或 afterCommit 处理。
- 全部 MANUAL ACK、并发 1、prefetch 1，工单额外 SAC。统一处理器在事务外同次投递最多处理三次，失败间隔 1 秒、2 秒；业务及事务提交后 ACK，耗尽 reject(false) 进独立 DLQ 后继续。
- ACK/Reject 通道 I/O 异常关闭通道，交 Broker 重投。SITE 维持 eventId 唯一约束幂等，日志可重复，SMS/PUSH/SUMMARIZE 维持占位。
- SLA 按数据库原 deadline；insert 后在原事务回读秒精度 DATETIME。Asia/Shanghai 转换，每段 x-delay=max(1,min(deadline-now,86400000))；旧期限/终态/达标忽略，未到期保留原载荷与 ID 续投 confirm 后 ACK，到期调用原 mark 方法。

### 6.2 本地 RabbitMQ 前置与启动门卫

- 复用 rabbitmq 4.0.9 / Erlang 27.3.4.19，先用 PowerShell 7 执行 `pwsh -File docs/rabbitmq-init.ps1` 安装启用 v4.0.7 延迟插件及七条精确死信 policy。
- 6 Exchanges、14 quorum Queues、25 Bindings；业务 delivery-limit=-1，工单 SAC；每组独立 DLQ，policy 设置 DLX、group routing key、at-least-once、reject-publish，不匹配 DLQ。
- `RabbitStartupGate` 按 Exchange → Queue → Binding → 管理 API 核验实际有效 policy → 发送就绪 → 启动监听器；插件缺失、声明冲突、管理 API 不可用或 policy 不符即启动失败，禁止自动删除重建。
- 管理地址 `RABBITMQ_MANAGEMENT_URL` 默认 http://localhost:15672，复用 RabbitMQ 用户密码。节点身份记录与插件包、data 和容器配置共同备份；恢复脚本保留原节点身份并重新安装/启用插件。
- 存量切换/回滚见 `docs/RABBITMQ-MIGRATION.md`。普通六组与 retry 排空后切换，SLA 按原数据库期限恢复；阶段二结束停下汇报，阶段三再正式停写切换。

## 七、日志

- `logback-spring.xml`：控制台 + `logs/info/` + `logs/error/`，按天 + 50MB 滚动，保留 30 天 / 3GB。
- 接口日志用 `@ApiLog(module = "工单", action = "提交报修")`，只标 Controller 方法；SSE 接口出参自动跳过。
- 关键业务动作（注册、登录、派单、状态流转、超时升级）打 INFO，含业务主键，便于排查。

## 八、配置与密钥

- 禁止硬编码任何密钥 / api-key / 密码；一律 `${ENV_VAR}`，本机明文只放 项目根目录 `application-local.yml`（已 gitignore）。
- 本机容器映射（Docker）：MySQL `localhost:3307`（库名 `zhixiu`，已导入 `src/main/resources/db/zhixiu.sql`）、Redis `localhost:6379`、MinIO `9000/9090`、RabbitMQ `5672/15672`；旧 RocketMQ 仅用于存量排空及回滚。
- 环境变量清单：`DB_NAME`（默认 `zhixiu`）、`DB_PASSWORD`、`JWT_SECRET`（≥32 字符，缺失即启动失败）、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`AI_API_KEY`、`CORS_ALLOWED_ORIGINS`、`SPRING_PROFILES_ACTIVE`、`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`、`RABBITMQ_VHOST`、`RABBITMQ_MANAGEMENT_URL`。

## 九、工程约束

- JDK 17 + Maven 3.9；依赖版本集中在 `pom.xml` 的 `<properties>`，禁止散落版本号，禁止使用旧版本依赖。
- 全部使用 `jakarta.*`，禁止 `javax.*`。
- 文件 UTF-8（无 BOM）、换行 CRLF；中文注释使用中文全角标点，不用破折号装饰。
- 不新增开发文档未定义的接口、数据库表、字段；确有必要时先与业主确认并同步更新开发文档。
- 不写单元测试（项目无测试基础设施），验收依赖 compile、clean package -DskipTests、真实接口与 MQ 故障链路走查，恢复工具独立构建。

## 十、本地运行

```powershell
# 1. 依赖服务复用已有 Docker 容器：MySQL(mysql8:3307)、Redis(redis-stack-roaring:6379)、MinIO(9000/9090)、RabbitMQ(5672/15672)
# 2. 首次安装插件及 policy（在 ZhiXiu-Cloud 目录，PowerShell 7）
pwsh -File docs/rabbitmq-init.ps1
# 3. 设置 DB_PASSWORD、JWT_SECRET、RABBITMQ_PASSWORD 或提供已忽略 application-local.yml
#    JVM 使用 Asia/Shanghai；数据库默认 zhixiu，可用 DB_NAME 覆盖
mvn -B -q compile
mvn -B clean package -DskipTests
java -jar target/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar
# 4. 启动门卫通过后才允许发送/消费；冲突停止并报告，不删除队列
# 5. 接口文档 http://localhost:8080/doc.html，元数据 /v3/api-docs，备用 /swagger-ui/index.html
```

普通存量切换、SLA manifest 与回滚只能按 `docs/RABBITMQ-MIGRATION.md` 分阶段执行，旧 `rocketmq-init.ps1` 仅保留为旧包回滚资料。
