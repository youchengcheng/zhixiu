# SLA 一次性恢复工具

独立 Maven CLI，只执行 MySQL SELECT，不修改数据库、不重算 deadline。RabbitMQ 和 RocketMQ 双客户端只属于本工具，主应用只依赖 RabbitMQ。Java 17；依赖版本由 Spring Boot 3.5.16 BOM 及本工具 properties 管理。

```powershell
mvn -B clean package -DskipTests
java -jar target/mq-recovery-1.0.0.jar --help

# 默认 preview：固定批次，文件已存在会拒绝覆盖。
java -jar target/mq-recovery-1.0.0.jar --target rabbitmq --manifest D:/mq-migration/sla-rabbit.json

# 正式停写、Review manifest 后，在阶段三显式执行。
java -jar target/mq-recovery-1.0.0.jar publish --manifest D:/mq-migration/sla-rabbit.json

# 回滚使用最新数据库期限创建新的 RocketMQ 批次。
java -jar target/mq-recovery-1.0.0.jar preview --target rocketmq --manifest D:/mq-migration/sla-rocket.json
java -jar target/mq-recovery-1.0.0.jar publish --manifest D:/mq-migration/sla-rocket.json
```

连接凭证从环境变量读取，不写入 manifest、结果或日志。推荐恢复账号仅有 SELECT 权限。

| 环境变量 | 默认值 / 要求 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/zhixiu?serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | 必填 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USERNAME` / `RABBITMQ_VHOST` | `youcc` / `/` |
| `RABBITMQ_PASSWORD` | RabbitMQ publish 必填；preview 无需 MQ 凭证 |
| `ROCKETMQ_ENDPOINTS` | `localhost:8081` |
| `ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY` | 可选，必须同时配置 |
| `ROCKETMQ_SSL_ENABLED` | `false`，本机 Proxy 使用明文 gRPC |
| `DOCKER_EXE` | `C:\Program Files\Docker\Docker\resources\bin\docker.exe` |
| `ROCKETMQ_BROKER_CONTAINER` | `rocketmq-broker` |
| `ROCKETMQ_BROKER_ADDRESS` | `localhost:10911`，地址由容器内 mqadmin 访问 |

Preview 按主键分页，每页 500 条，保存 manifest version、批次 ID、目标、`Asia/Shanghai`、批次时间、工单 ID/类型、数据库原 deadline 文本及 epoch。状态和里程碑冲突记录写入 `anomalies`，不修改记录；必须在切换验收中处理，不能将这些记录当作已完成恢复。

恢复条件均要求 `deleted=0`，不筛选 `sla_escalated`：

- RESPONSE：PENDING/ASSIGNED，响应 deadline 非空，响应 breached=0，accepted_time 为空。
- HANDLE：PENDING/ASSIGNED/ACCEPTED/GOING/PAUSED/TRANSFER_PENDING，处理 deadline 非空，处理 breached=0，submitted_time 为空。

Publish 校验 manifest 版本、时区、任务唯一性及 deadline 两种表示的一致性；使用 manifest 创建 payload，每条发送前再次 SELECT 核对当前状态、里程碑及原 deadline。消息体只有 `orderId/type/deadline/timestamp` 四字段，Long 以字符串编码，timestamp 固定为批次时间。逻辑消息 ID 为 `SLA-RECOVERY:<batchId>:<orderId>:<type>`。

RabbitMQ 发送正数 `x-delay=max(1,min(deadline-now,86400000))`，使用持久化 JSON、mandatory=false，逐条独立通道等待 confirm 3 秒。后续超过 24 小时的分段由新 SLA 消费者续投。confirm 仅代表插件接收，不能证明未来已成功路由。

RocketMQ preview 和 publish 都直接执行以下命令查询有效配置，无静态上限回退；要求 Broker、Namesrv/Proxy 可用。Manifest 保存 preview 的有效配置摘要；publish 另保存带查询时间的 `.rocket-effective.<timestamp>.json` 证据。查询失败、timerWheelEnable=false 或任何待恢复有效任务超过实际 timerMaxDelaySec 时，在发布前停止简单回滚；保持停写并修复 RabbitMQ。不会缩短 deadline、提前投递分段或扩大 Broker 上限。

```powershell
& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' exec rocketmq-broker sh mqadmin getBrokerConfig -b localhost:10911
```

RocketMQ 原生绝对投递时间为 `max(now+1000ms, 原deadline)`，SDK 单次发送，不给旧消费者发提前分段任务。

结果追加到 `<manifest>.results.jsonl`，每条强制落盘并使用排他锁防止同批次并发发布。结果含 manifest SHA-256、batch ID、任务键及以下状态：

| 状态 | 含义及重跑行为 |
|---|---|
| CONFIRMED | 已收到 Broker 确认且结果已落盘；重跑跳过 |
| FAILED | 明确 NACK 或发送前失败；排障后重跑 |
| UNKNOWN | confirm 超时、发送结果不确定；重跑可能重复 |
| SKIPPED | 当前数据库状态、里程碑或 deadline 已变化；重跑再次核对 |

发送已确认后结果落盘失败也属于 UNKNOWN：工具打印任务键并立即停止。磁盘文件无法写入时，无法保证 UNKNOWN 本身落盘；保留原文件并人工核对 Broker/消费日志。末尾不完整记录不会自动删除或覆盖，需按 UNKNOWN 验收处理。结果存在 FAILED/UNKNOWN/未发布条目时进程返回非零。退出码只反映任务条目状态；即使退出码为 0，manifest 的 anomalies 也必须单独人工验收，不代表异常记录已经处理。

重跑必须保持 manifest 原样；摘要不一致会拒绝使用旧结果。未知结果重投具有至少一次语义；现有 SLA 消费者以数据库 deadline、里程碑和 breached 标记幂等处理，但数据库与 MQ 仍存在非原子窗口。

正式切换禁止新旧 SLA 消费者同时运行；该工具不负责停写、停消费者、排空普通队列或恢复写入。等待中的插件消息尚未进入队列，恢复完整性必须结合 manifest、结果、消费日志和数据库，不能只看 Queue 数量。
