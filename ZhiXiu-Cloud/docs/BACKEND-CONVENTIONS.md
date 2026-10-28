# 后端统一规范（ZhiXiu-Cloud）

> 契约唯一来源：仓库根目录 `物业报修系统-开发文档.md`（数据库 DDL、88 个接口、枚举字典、Redis/MQ/AI 规范）。
> 本文件只规定「怎么写」，不定义「写什么」。两者冲突时以开发文档为准。

## 一、分层与职责

| 层 | 允许 | 禁止 |
|---|---|---|
| `controller` | `@Valid` 校验、`@PreAuthorize` 权限、调用 Service、返回 `Result` | 业务逻辑、直接调用 Mapper |
| `service` / `service.impl` | 业务规则、事务、状态机、发 MQ、调用 Mapper | 出现 `HttpServletRequest` / `HttpServletResponse` |
| `mapper` | 继承 `BaseMapper`，复杂 SQL 写 `resources/mapper/*.xml` | 业务判断 |
| `entity` | 与 15 张表一一对应 | 直接作为接口返回值 |
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
- MQ Topic/Tag 常量集中放 `common/constant/MqConstants`；延迟消息用任意秒延迟，不降级为定时轮询。

### 6.1 RocketMQ 5.x 客户端（v5 gRPC）

- 依赖 `rocketmq-v5-client-spring-boot-starter`，**不要**换成 `rocketmq-spring-boot-starter`（remoting 协议仅支持 18 个固定延迟等级，无法满足 SLA 任意秒延迟）。
- 发送端统一用 `RocketMQClientTemplate`：`syncSendNormalMessage` / `syncSendFifoMessage(dest, payload, orderId)` / `syncSendDelayMessage(dest, payload, Duration)`，destination 形如 `topic:tag`。
- 消息体统一走 `MqMessageCodec` 编成 UTF-8 JSON 字节，**不要**依赖 starter 的消息转换器，避免类型推断差异。
- 消费端实现 `org.apache.rocketmq.client.core.RocketMQListener`（**无泛型**），方法 `ConsumeResult consume(MessageView view)`，用 `MqMessageCodec.decode(view.getBody(), X.class)` 反序列化；异常返回 `FAILURE` 交给 MQ 重试。
- `@RocketMQMessageListener` 的 `accessKey` / `secretKey` / `endpoints` 默认值取自 starter 内置占位符 `\`，因此 `application.yml` 必须显式提供 `rocketmq.push-consumer` 段，否则 endpoints 解析为空串导致消费端启动失败。

### 6.2 本地 Broker 前置条件（必须预先建 Topic）

RocketMQ 5.x 客户端**不会自动创建 Topic**，未建 Topic 时消费端启动即报 `No topic route info in name server`，应用启动失败。本机 Topic 用 `docs/rocketmq-init.ps1` 一次建好，要点：

- 五个业务 Topic：`workorder-event-topic`、`sla-timeout-topic`、`notification-topic`、`operation-log-topic`、`ai-task-topic`。
- `workorder-event-topic` 收发**顺序消息**，必须同时设 `-o true` 与属性 `+message.type=FIFO`，否则发送端报 `Current message type not match with topic accept message types`。
- `sla-timeout-topic` 收发**延迟消息**，必须设属性 `+message.type=DELAY`，且 Broker 需 `timerWheelEnable=true`。
- 属性与顺序开关要**一次命令同时设置**：`mqadmin updateTopic` 每次会重写 TopicConfig，分开执行会互相覆盖。

## 七、日志

- `logback-spring.xml`：控制台 + `logs/info/` + `logs/error/`，按天 + 50MB 滚动，保留 30 天 / 3GB。
- 接口日志用 `@ApiLog(module = "工单", action = "提交报修")`，只标 Controller 方法；SSE 接口出参自动跳过。
- 关键业务动作（注册、登录、派单、状态流转、超时升级）打 INFO，含业务主键，便于排查。

## 八、配置与密钥

- 禁止硬编码任何密钥 / api-key / 密码；一律 `${ENV_VAR}`，本机明文只放 项目根目录 `application-local.yml`（已 gitignore）。
- 本机容器映射（Docker）：MySQL `localhost:3307`（库名 `zhixiu`，已导入 `src/main/resources/db/zhixiu.sql`）、Redis `localhost:6379`、MinIO `9000/9090`、RocketMQ Namesrv `9876` / Proxy `8081`。
- 环境变量清单：`DB_NAME`（默认 `zhixiu`）、`DB_PASSWORD`、`JWT_SECRET`（≥32 字符，缺失即启动失败）、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`AI_API_KEY`、`CORS_ALLOWED_ORIGINS`、`SPRING_PROFILES_ACTIVE`。

## 九、工程约束

- JDK 17 + Maven 3.9；依赖版本集中在 `pom.xml` 的 `<properties>`，禁止散落版本号，禁止使用旧版本依赖。
- 全部使用 `jakarta.*`，禁止 `javax.*`。
- 文件 UTF-8（无 BOM）、换行 CRLF；中文注释使用中文全角标点，不用破折号装饰。
- 不新增开发文档未定义的接口、数据库表、字段；确有必要时先与业主确认并同步更新开发文档。
- 不写单元测试（项目无测试基础设施），验收依赖 `mvn -B clean package` + 逐接口走查。

## 十、本地运行

```bash
# 1. 依赖服务（均已在本机 Docker 中复用）
#    MySQL(mysql8:3307) / Redis(redis-stack:6379) / MinIO(9000) / RocketMQ(9876,8081)
# 2. 数据库 zhixiu（本机已导入 src/main/resources/db/zhixiu.sql，可用 DB_NAME 覆盖）
# 3. 启动
mvn -B clean package -DskipTests
java -jar target/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar    # 或 mvn spring-boot:run
# 4. RocketMQ Topic（首次部署必做，RocketMQ 5.x 客户端不自动建 Topic）
#    powershell -File docs/rocketmq-init.ps1
# 5. 接口文档（Knife4j）：http://localhost:8080/doc.html
#    接口元数据：http://localhost:8080/v3/api-docs    备用 Swagger UI：http://localhost:8080/swagger-ui/index.html
```
