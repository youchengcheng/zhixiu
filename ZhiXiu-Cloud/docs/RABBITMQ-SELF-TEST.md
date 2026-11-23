# RabbitMQ 迁移自测清单

交付日期：2026-11-21；统一时区：Asia/Shanghai。

代码、运维脚本、恢复工具和中文注释已完成。主应用 compile、clean package -DskipTests、恢复工具独立构建，以及隔离环境中的真实 RabbitMQ 基础设施验证均已通过。完整业务验收由用户自行执行；本轮没有完成正式切换，也没有向主 vhost 补投正式 SLA 存量。

实施顺序及回滚依据 [迁移手册](RABBITMQ-MIGRATION.md)，已通过项目及其证据范围见 [阶段二验收记录](RABBITMQ-PHASE2-ACCEPTANCE.md)。业务请求字段、数据库字段与状态机以根目录 [开发文档](../../物业报修系统-开发文档.md)为准。

## 1. 当前状态与测试准备

2026-11-21 13:18:01（Asia/Shanghai）已从原启动会话发送 Ctrl+C，此前占用 127.0.0.1:8080 的旧 RocketMQ JVM（历史 PID 14864）及其启动进程已退出；核对时 8080 无监听者，新应用尚未启动。此记录仅说明旧应用停止和端口释放，不代表正式停写、最终排空或 SLA 恢复已完成。操作前仍须重新核对进程、启动包与端口。旧包保留在 docs/rollback-before-rabbitmq，不删除旧容器、数据、包或验收记录。

RabbitMQ 现有容器已启用 v4.0.7 延迟插件，主 vhost / 的 6 个 Exchange、14 个 quorum Queue、25 个 Binding 和 7 条死信 policy 已核验。插件等待中的消息不能通过 Queue 数量确认完整性。

启动新包前，在当前终端配置 RABBITMQ_PASSWORD；必要时覆盖 RABBITMQ_HOST、RABBITMQ_PORT、RABBITMQ_USERNAME、RABBITMQ_VHOST 和 RABBITMQ_MANAGEMENT_URL。管理 URL 默认 http://localhost:15672，管理 API 复用 RabbitMQ 凭证，账号需要读取队列与有效 policy 的权限。其他基础设施使用已有 local 配置。密码和密钥不写入测试文档、manifest 或 Git。

完整业务测试准备一个业主、管理员及必要的维修人员账号，使用自己的测试账号登录。测试数据用可辨识的唯一描述，保存 orderId、orderNo、知识 ID 与测试时间。无需向 Codex 提供账号。

## 2. 正式切换顺序

不要在旧 SLA 消费者仍运行时启动新消费者。正式切换按以下顺序执行，每一步核验完成再进入下一步：

1. 备份旧包、配置及 Broker 有效配置；冻结写请求，等待在途请求结束。
2. 使用旧包排空六个普通组及其经典/POP Retry。执行排空快照脚本，逐组检查 Diff 和 Inflight；重试耗尽的消息导出并登记 DLQ。旧 SLA 组无需排空。
3. 在旧应用所属终端用 Ctrl+C 优雅停机，等待实际旧 JVM 完全退出；再次执行排空快照，检查停机期间尾部消息。若仍有消息，保持停写，重启旧包排空，再次停机和复查。
4. 确认旧消费者全部停止后启动新包，检查门卫日志和七个消费者。
5. 基于停写后的最新数据库创建正式 SLA manifest，Review 后 publish；处理所有 FAILED、UNKNOWN、未记录条目及 anomalies，并人工核验 SKIPPED。
6. 完成下面的真实业务与 SLA 验收，再恢复写入。记录各步时间、证据目录和结果。

从 ZhiXiu-Cloud 目录运行：

```powershell
# 只读快照：脚本不代替停写、停 JVM 或最终排空判断；每次使用新目录。
pwsh -File docs/rocketmq-drain.ps1

# 仅在旧消费者已全部停止后启动新包；该命令需已有本地配置和环境变量。
java "-Duser.timezone=Asia/Shanghai" -jar target/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.address=127.0.0.1
```

最近的只读排空快照为 E:/docker/rabbitmq/recovery/rocketmq-final-script-verification-20261121-125345-913：六个普通组与发现的五个 POP Retry 的 Diff/Inflight 为 0，AI 无 Retry，六组范围内未发现 DLQ Topic。当时旧应用仍运行，该结果不能视为正式排空完成。

## 3. 启动与基础配置

成功启动应看到：

```text
RabbitMQ 启动门卫通过：6 Exchange、14 Queue、25 Binding；7 个有效死信 policy；监听器已启动
```

在管理页面 http://localhost:15672 核对七个业务队列有消费者，工单队列启用 SAC，所有消费者并发 1、prefetch 1。检查普通业务队列和对应 .dlq 均为持久化 quorum；业务队列 x-delivery-limit=-1，死信 policy 精确匹配业务队列，未匹配 DLQ。

故障场景仅在隔离测试环境或独立测试 vhost 执行：

| 测试 | 通过标准 |
|---|---|
| 管理 API 地址/凭证错误 | 启动失败，发送门卫不开放，业务监听器未启动 |
| 缺少有效死信 policy、被其他高优先级 policy 覆盖 | 启动失败，日志指明不符合要求的队列/字段 |
| 同名队列参数与声明冲突 | 启动失败，已有队列和消息保留，不自动删除 |
| 延迟 Exchange 类型不可用 | 声明失败，应用启动失败 |

不要通过禁用现有延迟插件制造故障：禁用会丢失等待消息。以上基础故障路径已有隔离验证，可按阶段二记录复核；完整主应用启动仍需自行验收。

## 4. 工单、通知、日志与 AI

使用前端或接口工具调用现有接口；成功需同时检查 HTTP 状态和业务 code，正常业务成功 code=200。登录 POST /api/auth/login 获取 data.token，GET /api/auth/me 核对身份。

| 场景与入口 | 应核对结果 |
|---|---|
| 业主 GET /api/property/list、GET /api/category/tree 后 POST /api/owner/order | 选正常房产和二级分类；返回 orderId/orderNo；工单、初始 deadline 与 CREATED 流转记录正常 |
| GET /api/owner/order/{id}、GET /api/owner/order/{id}/logs | 接口 deadline 与数据库秒精度原值一致；状态流转与日志符合业务契约 |
| 管理员 GET /api/notification/list?pageNum=1&pageSize=200 | 收到与本工单匹配的“新报修工单待派单”站内通知；SSE 在线推送保持现有行为 |
| 按现有状态机分别触发 CREATED、ASSIGNED、ACCEPTED、ARRIVE、PAUSE、RESUME、TRANSFER、SUBMITTED、COMPLETED、CANCELED | 十种路由均被工单消费者处理；日志顺序及业务副作用正确；同队列串行，最大并发 1 |
| SITE 同 eventId 重复投递 | notification 表没有重复业务通知；重复消息被正常确认 |
| 发起会记录访问日志的现有业务请求 | operation_log 正常入库，关联用户与请求正确 |
| SMS、PUSH 通道消息 | 现有占位日志出现；不要求真实短信或设备推送 |
| 管理员 POST /api/admin/knowledge | 新知识触发实际 VECTORIZE，最终 vectorized=1 |
| POST /api/admin/knowledge/{id}/revectorize | 向量化完成，version 增长 |
| GET /api/admin/knowledge/test-search?query=...&topK=3 | 能检索出本次测试知识 ID，验证实际 Redis 向量检索链路 |
| SUMMARIZE 消息 | 现有占位行为保持，消息正常确认 |

工单的全部十种事件需要多张测试工单覆盖，不能在一张工单上强行执行互斥流转。测试结束通过现有接口取消测试工单、删除测试知识；例如 POST /api/owner/order/{id}/cancel 的请求含 cancelReason，DELETE /api/admin/knowledge/{id} 清理测试知识。禁止绕过状态机直接改 status。

## 5. 失败、重试与死信

以下故障使用可恢复的隔离测试条件，并分别保留生产端异常、消费日志、DLQ 与业务数据库结果。

| 场景 | 通过标准 |
|---|---|
| 普通消息无匹配路由 | mandatory Return，发送失败；不可误报成功 |
| quorum 拒绝发布产生 NACK | 发送失败 |
| confirm 超过 3 秒、连接中断 | 发送失败；超时可能已送达，核对可能重复 |
| 建单工单事件或初始 SLA 发送失败 | 异常沿原业务事务传播，事务回滚；核对数据库实际结果 |
| 通知、操作日志或 AI 发送失败 | 保持原捕获告警/afterCommit 行为，不扩大事务边界 |
| 消费业务持续失败 | 同一次投递共执行三次，失败后约等 1 秒、2 秒；每次经过独立业务事务 |
| 三次耗尽后再发正常工单事件 | 失败消息进入 order-event-consumer-group.dlq，正常后续事件继续处理 |
| 其他六个消费者持续失败 | 分别进入自己的 .dlq，未进入其他消费组死信队列；不自动回灌 |
| ACK/Reject 通道 I/O 异常 | 关闭失效通道，Broker 重投；不在该通道继续执行业务 |

连接中断后重新投递可以重新计数，三次限制仅针对同一次投递。操作日志仍可能重复，SITE 按现有机制幂等；DLQ 人工补投可能晚于后续工单事件。

## 6. SLA 实际业务验收

使用可控期限的隔离业务数据；记录每条消息的 orderId、type、数据库原 deadline、messageId、发送确认、消费时间、超时标记及管理员通知。业务接口和字段保持开发契约，不新增接口或字段来测试。

| 场景 | 通过标准 |
|---|---|
| deadline 尚未到期 | 不提前设置 breached；保留原 deadline/timestamp/messageId 续投，confirm 成功后才 ACK 原消息 |
| deadline 已过期 | 立即安排正延迟投递；到期处理只首次标记并通知管理员 |
| RESPONSE 已受理、HANDLE 已提交 | 对应里程碑已完成，任务直接 ACK，不误报超时 |
| 完成/取消等终态、已删除、工单不存在、已标记对应超时 | 任务直接 ACK，不重复标记或重复首次告警 |
| 建单内存 deadline 有毫秒部分 | 消息使用 insert 后回读的数据库秒精度 deadline；正常到期不被误判为旧任务 |
| 消息 deadline 与当前数据库 deadline 不一致 | 旧任务 ACK，不覆盖当前期限，也不误标记 |
| deadline 超过 24 小时 | 每段 x-delay 最大 86400000；尚未到期继续分段，业务期限不缩短；完整跨段到期需持续观察 |
| 插件投递后旧 x-delay 为负 | 续投覆盖为正数，未复制旧负值 |
| 短延迟消息发布确认后正常重启 Rabbit | Rabbit 就绪后消息恢复并按原 deadline 处理；保持节点身份及数据挂载 |
| 续投持续失败 | 共处理三次后进入 sla-consumer-group.dlq；原消息不能在续投失败时提前 ACK |
| 不支持的任务类型或异常载荷 | 按三次失败及 SLA DLQ 流程处理，后续正常任务继续 |

基础设施阶段已验证实际短延迟、24h 属性封顶及正常重启恢复；尚未等待完整 24h，也未验证所有实际业务标记。SLA confirm 只表示插件接收，不能保证未来一定入队；验收结合日志与数据库，不单看 Queue 数量。

## 7. SLA 恢复与回滚

详细环境变量见 [恢复 CLI](../tools/mq-recovery/README.md)。DB_PASSWORD 必填，RabbitMQ publish 还需 RABBITMQ_PASSWORD。工具只 SELECT，不重算期限、不清零标记。

```powershell
# 先正式停写并停止旧消费者；manifest 必须选择未存在的新路径。
java "-Duser.timezone=Asia/Shanghai" -jar tools/mq-recovery/target/mq-recovery-1.0.0.jar preview --target rabbitmq --manifest E:/docker/rabbitmq/recovery/sla-formal.json

# Review 固定批次、原 deadline、anomalies 后，显式补投。
java "-Duser.timezone=Asia/Shanghai" -jar tools/mq-recovery/target/mq-recovery-1.0.0.jar publish --manifest E:/docker/rabbitmq/recovery/sla-formal.json
```

核对 manifest 的批次、Asia/Shanghai、目标、原期限及任务集合：RESPONSE 为待响应且未受理；HANDLE 为未提交的有效处理状态。均要求 deleted=0、对应 breached=0，deadline 非空；不按 sla_escalated 过滤。状态与里程碑冲突必须人工处理 anomalies，不能视为已恢复。

publish 每条发送前再次核对当前数据；结果在 <manifest>.results.jsonl。验收要求没有未处理 FAILED、UNKNOWN、未记录条目，逐条说明 SKIPPED 原因。保持 manifest 不变重跑，CONFIRMED 应跳过，结果摘要一致。UNKNOWN 重跑可能产生重复，确认后结果文件写失败也要按 UNKNOWN 处理。

最近只读数据库快照有 9 条有效 SLA（RESPONSE 4、HANDLE 5），均已过期，异常 0；快照未停写，不能用于正式补投。隔离阶段曾验证 9/9 CONFIRMED，该批次也不能代替主 vhost 正式恢复。

回滚前按迁移手册第 9 节再次停写、排空 Rabbit 普通消息并登记 DLQ、停止新消费者，再用最新数据库创建 RocketMQ 批次。工具 preview/publish 均查询 Broker 实际 timer 配置，任一任务超过实际上限应整批拒绝发布。最近只读有效配置为 timerWheelEnable=true、timerMaxDelaySec=259200（3 天），将来回滚时仍须复核。不能缩短 deadline、提前发送长延迟分段或扩大 Broker 上限。

## 8. 可选本地辅助入口与构建产物

docs/rabbitmq-phase3-evidence 中有未加入 Git 的本地辅助材料，仅作为便利工具；无账号时直接按上表手动测试：

- phase3-business-smoke.ps1：默认 preview，-Execute 才调用业务、查询或发布；须先完成安全切换准备，并自行配置 VERIFY_OWNER_*、VERIFY_ADMIN_* 及基础设施环境变量。覆盖建单、通知、日志、向量检索及部分 SLA，不能代替完整清单。
- recovery-audit/verify-sla-manifest.ps1：只读核对固定 manifest 与结果；参数 -Manifest <路径> -ExpectedTarget rabbitmq -RequireComplete。anomalies 和 SKIPPED 仍需人工核对。
- 这些辅助材料已通过静态编译/语法检查；本轮未用其执行正式业务写入或补投。辅助说明见各目录 README。

最终构建已通过：

```powershell
mvn -B -q compile
mvn -B clean package -DskipTests
mvn -B -f tools/mq-recovery/pom.xml clean package -DskipTests
```

| 产物 | 本次最终 SHA-256 |
|---|---|
| target/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar | C45987FA5580081CA14A22A3EBAE6BF218A0691BACB26C9FFB4B0E1C54196748 |
| tools/mq-recovery/target/mq-recovery-1.0.0.jar | 5C35B34E4BF62D164C8958E40E9AFA1B1F34A65C6E61F33403905C38B678FC29 |
| docs/rollback-before-rabbitmq/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar | 775C3370B4A39A2BA3E9BB3B4D18FC173EBBA398240679DF2CD76AFF95CC9B79 |

阶段二记录中的包摘要对应当时的隔离验收历史，本表对应最后构建。最终构建日志为 docs/rabbitmq-phase3-package.log 和 docs/rabbitmq-phase3-recovery-package.log，不将包、日志、配置秘密或恢复批次加入 Git。

逐项记录“通过/失败/未测”、测试时间和证据。正式切换、SLA 补投、完整业务链路及恢复写入未完成前，不将整个迁移标为已验收。
