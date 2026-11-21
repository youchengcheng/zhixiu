# RocketMQ → RabbitMQ 替换与切换手册

> 状态：用户已通过 Review，阶段二实现与基础设施验证已完成，见 [阶段二验收记录](RABBITMQ-PHASE2-ACCEPTANCE.md)。阶段三的停写、存量排空、正式 SLA 恢复和恢复写入尚未执行。
> 业务契约：仓库根目录 `物业报修系统-开发文档.md`。本文件只说明 MQ 实现、部署和迁移操作。

## 1. 迁移范围与交付边界

主应用五类消息全部改用 RabbitMQ，SLA 使用 `rabbitmq_delayed_message_exchange` 插件。保留业务接口、数据库表和字段、五类 DTO、现有事务边界及生产端业务调用关系；前端、状态机、分布式锁、SSE 维持原行为。不提交 Git、不切分支。

工单事件和初始 SLA 发送失败继续向上抛出异常，触发原事务回滚。通知保持捕获告警，日志与 AI 保持原有捕获或 afterCommit 发送。此次不引入数据库与 MQ 的新一致性机制。普通存量排空后切换，有效 SLA 按数据库保存的原 deadline 重建。

分阶段交付：

1. 阶段一：方案 Review，已通过。
2. 阶段二：代码、插件、拓扑、配置、独立恢复工具、运维脚本及文档同步；完成后停下来汇报验收。
3. 阶段三：停写、排空存量、恢复 SLA、新链路验收及恢复写入；结束后汇报验收。

阶段二的实际验证证据和未完成项应记入独立验收记录，不能把以下验收要求视为已经通过。

## 2. 既有容器与插件

| 项目 | 本次固定值 |
|---|---|
| 容器 | `rabbitmq`，复用既有容器 |
| RabbitMQ / Erlang | `4.0.9` / `27.3.4.19` |
| 延迟插件 | `v4.0.7`，支持 RabbitMQ 4.0 系列 |
| AMQP / 管理端口 | `5672` / `15672` |
| vhost / 用户 | `/` / `youcc` |
| 数据挂载 | `E:\docker\rabbitmq\data` → `/var/lib/rabbitmq` |
| 安装包 | `E:\docker\rabbitmq\pulgins\rabbitmq_delayed_message_exchange-v4.0.7.ez` |

安装命令（在 `ZhiXiu-Cloud` 目录，用 PowerShell 7 执行）：

```powershell
pwsh -File docs/rabbitmq-init.ps1
```

脚本检查 RabbitMQ 版本，记录 hostname、nodename 与插件 SHA256，复制安装包至 `/opt/rabbitmq/plugins/`，启用插件，然后设置并核验七条死信 policy。密码不进入脚本、命令行或记录。默认节点身份记录为 `E:\docker\rabbitmq\recovery\node-identity.json`，须与插件包、容器配置和数据备份一起保留。

正常重启保留容器内插件与启用配置。容器重建时，现有 data 挂载不能单独恢复插件文件及 `/etc/rabbitmq/enabled_plugins`；先按原配置恢复容器和原 hostname/nodename、数据挂载，再执行：

```powershell
pwsh -File docs/rabbitmq-restore.ps1
```

恢复脚本只检查并复用既有容器，不创建容器、不禁用插件、不删除数据；节点身份或插件校验值冲突时停止。复原后还需确认插件版本、真实延迟投递及等待消息的恢复结果。

参考：[插件 v4.0.7](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/tag/v4.0.7)、[插件说明及限制](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange)。

## 3. 业务拓扑与死信

原 Topic 名称映射为 Exchange，消费组名称映射为 Queue，Tag 映射为 Routing Key。

| Exchange | 类型 | Routing Key | Queue |
|---|---|---|---|
| `workorder-event-topic` | direct | CREATED、ASSIGNED、ACCEPTED、ARRIVE、PAUSE、RESUME、TRANSFER、SUBMITTED、COMPLETED、CANCELED | `order-event-consumer-group` |
| `sla-timeout-topic` | x-delayed-message | RESPONSE、HANDLE | `sla-consumer-group` |
| `notification-topic` | direct | SITE | `notify-site-consumer-group` |
| `notification-topic` | direct | SMS | `notify-sms-consumer-group` |
| `notification-topic` | direct | PUSH | `notify-push-consumer-group` |
| `operation-log-topic` | direct | ACCESS | `operation-log-consumer-group` |
| `ai-task-topic` | direct | VECTORIZE、SUMMARIZE | `ai-task-consumer-group` |

`sla-timeout-topic` 参数 `x-delayed-type=direct`。共享 direct DLX 为 `zhixiu.dead-letter`；每个业务队列有对应 `<消费组>.dlq`，从 DLX 以消费组名称绑定。合计 **6 个 Exchange、14 个 Queue、25 个 Binding**（18 个业务绑定、7 个 DLQ 绑定）。

全部 Exchange、业务队列和 DLQ 持久化，关闭自动删除。业务队列及 DLQ 全部使用 quorum，不设置 TTL、不自动回灌。七个业务队列显式设置 `x-delivery-limit=-1`，工单队列额外设置 `x-single-active-consumer=true`。

每个业务队列使用 policy `zhixiu-dlx.<消费组>`，pattern 为精确 `^<消费组>$`，`apply-to=quorum_queues`、priority 50，definition 为：

```json
{
  "dead-letter-exchange": "zhixiu.dead-letter",
  "dead-letter-routing-key": "<消费组>",
  "dead-letter-strategy": "at-least-once",
  "overflow": "reject-publish"
}
```

policy 不匹配 DLQ。应用只声明及只读核验，policy 由初始化脚本设置。声明遇到已有参数冲突时启动失败，保留消息并报告；禁止自动删除重建。实际有效 policy 被其他高优先级 policy 覆盖时，启动门卫必须拒绝启动。

## 4. 主应用配置与启动顺序

主应用移除 RocketMQ Starter，使用 `spring-boot-starter-amqp`，版本由 Spring Boot 3.5.16 BOM 管理。RabbitMQ 凭证由环境变量或已忽略的 local 配置提供。

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:youcc}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      type: simple
      simple:
        auto-startup: false
        acknowledge-mode: manual
        concurrency: 1
        max-concurrency: 1
        prefetch: 1
        retry:
          enabled: false
    template:
      retry:
        enabled: false
```

管理 API 地址默认 `http://localhost:15672`，环境变量 `RABBITMQ_MANAGEMENT_URL` 可覆盖，复用 RabbitMQ 用户和密码。启动顺序：**Exchange → Queue → Binding → 管理 API 核对实际有效死信 policy → 开启发送就绪门卫 → 启动监听器**。插件缺失、声明冲突、管理 API 不可用或 policy 不符合要求，均启动失败。

所有消费者首版并发 1、prefetch 1、MANUAL ACK；工单固定这些设置。其他队列的吞吐调优另行验收。JVM、恢复 CLI 和 deadline 转换统一使用 `Asia/Shanghai`。

## 5. 发送确认、消费与重试

发送保持现有 ObjectMapper、UTF-8 JSON `byte[]`，五类 DTO 字段不变，兼容包装 Long 字符串格式。属性为 `application/json`、UTF-8、PERSISTENT，设置逻辑 messageId。普通消息独立模板 `mandatory=true`，SLA 模板 `mandatory=false`。

每次发布创建独立 CorrelationData，最多等待 confirm **3 秒**。普通消息只有 ACK 且没有 Return 才成功；NACK、Return、超时和连接异常都视为发送失败。确认超时不代表 Broker 一定没有接收，调用方重试可能产生重复。

统一消费处理器在同一次投递内处理三次，失败后分别等待 1 秒、2 秒。业务方法继续经过 Spring 事务代理；重试放在业务事务之外，避免复用失败事务。业务及事务提交完成才 ACK；第三次仍失败，`basicReject(deliveryTag, false)` 进入该队列 DLQ，随后处理后续消息。

ACK/Reject 通道 I/O 异常时关闭通道，交 Broker 重新投递；不在失效通道上继续执行业务。三次限制针对同一次投递，连接中断后的重投可重新计数。

工单单队列、SAC、并发 1、prefetch 1 保持收到消息后的串行处理；DLQ 人工补投可能晚于后续事件，跨生产连接的全局发布顺序没有额外保证。SITE 继续使用现有 eventId/唯一约束幂等，日志仍可能重复；SMS、PUSH、SUMMARIZE 保持占位日志。

## 6. SLA 绝对期限与分段

消息保持 `SlaTask(orderId, type, deadline, timestamp)`。建单 insert 后，在原事务内回读数据库实际保存的两个 deadline，再创建初始消息。数据库为秒精度 DATETIME，不使用内存毫秒期限做精确旧任务判断；缺少工单或期限时显式失败并沿原事务传播。

```text
x-delay = max(1, min(deadline - 当前时间, 86_400_000)) 毫秒
```

每段最多 24 小时，避免插件单段约 49.71 天上限；业务 SLA 没有新增上限。每次消费：

1. 校验类型，读取当前 deadline、状态与对应里程碑。
2. 工单不存在、已删除、已完成对应里程碑、已终止或已标记对应超时，ACK。
3. 消息 deadline 与数据库 deadline 不一致，作为旧任务 ACK。
4. 尚未到期，保留原消息体、deadline、timestamp、messageId，覆盖正数 `x-delay` 续投；confirm 成功后才 ACK 当前消息。
5. 到期后调用现有 `markResponseBreached(Long)` / `markHandleBreached(Long)`，仅首次标记时通知管理员。

插件投递后的旧 `x-delay` 可能为负，续投不得直接复制。续投失败由统一处理器重试，耗尽进入 SLA DLQ。

## 7. 一次性 SLA 恢复 CLI

独立目录 `tools/mq-recovery`，独立 Maven 构建；Rabbit 与 Rocket 双客户端只存在该工具，主应用仅 RabbitMQ。详细参数和环境变量见 [恢复工具说明](../tools/mq-recovery/README.md)。

```powershell
mvn -B -f tools/mq-recovery/pom.xml clean package -DskipTests
java -jar tools/mq-recovery/target/mq-recovery-1.0.0.jar preview --target rabbitmq --manifest E:/docker/rabbitmq/recovery/sla-rabbit.json
# 正式补投只在阶段三执行，先审阅固定 manifest
java -jar tools/mq-recovery/target/mq-recovery-1.0.0.jar publish --manifest E:/docker/rabbitmq/recovery/sla-rabbit.json
```

默认 preview，MySQL 仅 SELECT，按主键分页，每页 500。manifest 固定批次、目标、时区、工单 ID、类型、数据库原 deadline；连接与凭证不进入 manifest 或日志。消息四字段不变，timestamp 使用批次时间。

| 类型 | 恢复条件，均要求 deleted=0 |
|---|---|
| RESPONSE | PENDING/ASSIGNED；响应 deadline 非空；响应 breached=0；accepted_time 为空 |
| HANDLE | PENDING/ASSIGNED/ACCEPTED/GOING/PAUSED/TRANSFER_PENDING；处理 deadline 非空；处理 breached=0；submitted_time 为空 |

不按 `sla_escalated=0` 筛选，不调用 applyDeadlines、不重算期限、不清零标记。过期任务立即安排投递。状态与里程碑冲突单列异常，原数据保持不变，交验收处理。

publish 使用固定 manifest，每次发送前复核当前状态与期限，逐条等待确认并持久记录 `CONFIRMED / FAILED / UNKNOWN / SKIPPED`。成功退出只表示任务条目已确认或跳过，manifest 的 anomalies 仍须单独人工验收，不能仅凭退出码判断切换完整。重跑跳过已确认项；发送超时或确认后结果落盘失败属于 UNKNOWN，重投可能产生重复。等待消息尚未进入 Queue，恢复完整性须结合 manifest、confirm、结果日志、消费日志及数据库状态。

## 8. 正向存量切换（阶段三）

1. 保存旧可启动包、配置、Broker 配置和消息清单，保留旧容器与数据。
2. 冻结写请求，等待在途请求完成，运行旧包排空普通消息与重试。
3. 核对工单、SITE、SMS、PUSH、操作日志、AI 六个普通组；旧 SLA 组不要求排空。旧普通重试须成功或耗尽进入已登记 DLQ。
4. 旧组最多 16 次重试，单次 lag=0 不能证明排空。执行只读 `pwsh -File docs/rocketmq-drain.ps1`，对六个业务 Topic 和发现的每个经典/POP Retry Topic 以原消费组显式查询 consumerProgress；保存 Diff、Inflight、完整 topicList、topicStatus 和 Retry/DLQ 归属映射。Topic 的物理 Max Offset 可能属于已消费历史，不能替代 Retry 消费偏移。mqadmin 即使返回 exitCode=0，输出中的 CODE、SubCommandException 或不完整表格仍使脚本失败，失败证据不会生成成功 manifest。
5. Ctrl+C 优雅停旧包，等待旧 JVM 完全退出，再复查六组积压和在途。退出期间若有尾部消息，保持停写，重启旧包排空，再停机复查。
6. 启动新包，门卫通过后按数据库原期限 preview、审阅、publish SLA；解决 manifest FAILED/UNKNOWN 与异常记录，完成真实链路验收后恢复写入。

新旧 SLA 消费者不得同时运行。旧 DLQ 导出登记并保留，不删除、不自动回灌。`topicList` 不加 `-c`，该参数会过滤 Retry/DLQ；脚本兼顾 `%RETRY%group`、`%RETRY%group_topic`、`%RETRY%group+topic`。

## 9. 回滚

恢复写入前可停新包并恢复旧包；仍需确保新旧 SLA 消费者不同时运行，并核对数据库在验收过程中已发生的变化。

恢复写入后：**再次停写 → 排空 Rabbit 普通消息并登记 DLQ → 停止新消费者 → 按最新数据库原 deadline 向 RocketMQ 恢复 SLA → 仅启动旧消费者 → 验收后恢复写入**。

Rocket 恢复使用原生 `deliveryTimestamp=max(当前时间+1000ms, 原deadline)`。旧消费者没有分段 deadline 门卫，不得提前发送长延迟分段消息。

旧 Broker 为 5.3.1，静态预期 timerMaxDelaySec 为 259200（3 天），CLI 在 preview 和 publish 时用 `mqadmin getBrokerConfig` 查询实际有效 `timerWheelEnable`、`timerMaxDelaySec` 并保存安全证据。整批待恢复有效任务中任一超过实际上限，停止简单回滚，保持停写并修复新方案；不缩短 deadline、不强发、不扩大 Broker 配置。

## 10. 必须完成的验收

| 类别 | 场景 |
|---|---|
| 构建 | 主应用 compile、clean package -DskipTests；恢复 CLI 独立构建 |
| 启动 | 插件缺失、拓扑冲突、policy 缺失时失败；正确配置收发正常 |
| 发送故障 | 普通不可路由、NACK、confirm 超时、连接异常符合原业务处理方式 |
| 工单 | 串行处理；同次投递三次；进入 DLQ 后后续事件继续 |
| 通知/日志 | SITE 重复不重复落库；日志入库；SMS/PUSH 保持占位 |
| SLA | 不提前标记、过期处理、达标/终态忽略、秒精度匹配、24h 分段、重启短延迟恢复、续投失败 DLQ |
| AI | 实际 VECTORIZE 链路；SUMMARIZE 占位保持 |
| 存量/回滚 | 六个旧普通组排空；manifest 无未处理 FAILED/UNKNOWN；回滚超限预检有效 |

按项目约定使用真实链路，不新增单元测试。阶段二和阶段三分别记录验收，不在阶段二擅自冻结业务或补投正式存量。

## 11. 保留风险

- 延迟插件已停止维护；等待消息保存在节点本地 Mnesia 单份持久化中。禁用插件会丢失等待消息，quorum 队列不保护该等待窗口。
- SLA confirm 表示插件接收，不保证未来路由成功入队；插件不支持 mandatory，到期只尝试路由一次。
- 数据库与 MQ 非原子窗口，以及 SLA 标记提交后通知发送失败可能遗漏告警，属于本轮保留的既有风险。
- 至少一次投递可能重复处理；SITE 保持现有幂等，操作日志仍可能重复。UNKNOWN 恢复条目重投也可能重复。
- 单节点 quorum 没有节点级高可用能力；并发 1 优先保持行为清晰，吞吐调优需单独验收。
