# RabbitMQ 迁移阶段二验收记录

日期：2026-11-21，时区：Asia/Shanghai。

阶段二的代码、基础设施和恢复工具验证已完成，现交付用户自行测试。阶段三的正式停写、最终排空确认、SLA 存量补投、业务链路验收和恢复写入由用户按测试清单及迁移手册执行，本轮未执行正式切换或主 vhost 的 SLA 补投。本记录不代表系统已经完成正式切换。

实施依据：[迁移与切换手册](RABBITMQ-MIGRATION.md)及仓库根目录的唯一业务契约 `物业报修系统-开发文档.md`。

## 1. 本阶段交付

- 主应用五类消息全部改为 RabbitMQ：四个 Producer、七个 Consumer，以及操作日志服务的直接发送。主应用移除 RocketMQ Starter，由 Spring Boot 3.5.16 BOM 管理 AMQP 依赖。
- 发送使用持久化 UTF-8 JSON、逻辑 messageId 和独立 CorrelationData；普通消息 mandatory=true，SLA mandatory=false，confirm 等待上限 3 秒，保留原业务发送失败处理及事务边界。
- 消费使用统一 MANUAL ACK 处理器，同次投递共三次处理，失败间隔 1 秒、2 秒；耗尽转入独立 DLQ 后处理后续消息。工单维持 SAC、并发 1、prefetch 1；事务方法继续经过 Spring 代理。
- SLA 使用数据库回读的秒精度原 deadline、Asia/Shanghai 和最多 24 小时分段；续投保留原消息与标识，覆盖正数 x-delay，确认成功后才 ACK。
- 启动门卫依次声明 Exchange、Queue、Binding，核对实际有效死信 policy，再开放发送并启动监听器。管理 API 元数据传播采用有限重查，最多四次，间隔 1 秒、2 秒、3 秒。
- 独立 [SLA 恢复 CLI](../tools/mq-recovery/README.md)支持 SELECT-only preview、固定 manifest、显式 publish、确认结果落盘与重跑，以及 RocketMQ 回滚整批超限预检；双客户端仅属于工具。
- 提供 [初始化脚本](rabbitmq-init.ps1)、[节点恢复脚本](rabbitmq-restore.ps1)、[旧消息排空检查脚本](rocketmq-drain.ps1)，同步开发契约、后端规范、README、AGENTS 与交接说明，保留历史 RocketMQ 验收记录。

前端无本轮修改，不提交 Git、不切分支。迁移前备份与生产配置对照已核验，用户原有非 MQ 配置保留。

## 2. 环境与构建

复用现有 rabbitmq 容器，RabbitMQ 4.0.9、Erlang 27.3.4.19，延迟插件 v4.0.7 已启用。主 vhost `/` 的拓扑及有效 policy 已验证：6 个 Exchange（含 DLX）、14 个 Queue、25 个 Binding、7 条精确匹配的 quorum 死信 policy。普通队列及 DLQ 均持久化 quorum，业务队列 x-delivery-limit=-1，工单启用 SAC。

插件源包与节点身份记录：

```text
E:\docker\rabbitmq\pulgins\rabbitmq_delayed_message_exchange-v4.0.7.ez
E:\docker\rabbitmq\recovery\node-identity.json
```

已通过以下构建，未新增单元测试：

```powershell
# 主应用目录 ZhiXiu-Cloud
mvn -B -q compile
mvn -B clean package -DskipTests

# 独立工具
mvn -B -f tools/mq-recovery/pom.xml clean package -DskipTests
```

最终主应用 Boot JAR 中 RocketMQ 客户端库为 0 个，RabbitMQ 核心依赖为 3 个。主应用 package 原始输出见 [构建日志](rabbitmq-phase2-evidence/main-package.log)。

| 包 | SHA-256 |
|---|---|
| `target/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar` | `15CBE2D1867AB678FD68D749CCA2651ABAD5EDBCACF72998057D9B199E9EAFCD` |
| `tools/mq-recovery/target/mq-recovery-1.0.0.jar` | `B0EAF6A9781B951D6783851A6D15AC29567FD055F48E88DA2C9D1ED5F9CC9FE9` |
| `docs/rollback-before-rabbitmq/ZhiXiu-Cloud-0.0.1-SNAPSHOT.jar` | `775C3370B4A39A2BA3E9BB3B4D18FC173EBBA398240679DF2CD76AFF95CC9B79` |

旧包及迁移前配置保存在 `docs/rollback-before-rabbitmq/`。旧 Broker 配置、六组消息和 Retry/DLQ 清单在用户正式停写排空时仍须重新保存；本轮补充的只读快照不能代替停机后的最终排空确认。

## 3. 基础设施真实验证

验证使用独立 vhost 和临时 Java 验收程序，调用主应用实际 Rabbit 基础类；未启动完整业务应用、未启动旧 Broker。测试消息与业务主 vhost 隔离。主 `/` 仅声明和核验正式拓扑，没有进行正式 SLA 存量补投。

| 项目 | 结果与证据范围 |
|---|---|
| 启动门卫 | 发送未就绪被拒绝；已有队列 SAC 参数冲突、policy 缺失均阻止门卫；正确拓扑与 policy 通过 |
| 插件缺失路径 | 以 Broker 拒绝未知 Exchange 类型进行故障注入，验证声明失败阻止门卫；未禁用或卸载实际延迟插件 |
| 普通消息 Return | 真实 mandatory 不可路由，发送判定失败 |
| NACK | 真实 quorum overflow 拒绝发布，发送判定失败 |
| confirm 超时 | Docker pause 后实际超过 3 秒判失败，finally 恢复容器；确认超时消息仍可能已送达 |
| 连接异常 | 使用错误端口产生实际连接失败 |
| 消费重试与死信 | 同次投递确实执行三次，间隔约 1 秒、2 秒；独立 DLQ 收到失败消息，后续工单事件继续 |
| 通道异常 | 注入 ACK I/O 异常后，实际缓存通道物理关闭，Broker 重新投递；该结果不等同于业务事务故障验收 |
| 短延迟 | 真实等待消息没有提前投递，投递后旧 x-delay 为负数 |
| 长延迟分段 | 实际发送属性封顶 24 小时，过期任务使用正 1ms；未等待完整 24 小时 |
| SAC 串行及接管 | 两个独立连接、prefetch 1，管理 API 确认注册 2 个、活跃 1 个；20 条消息保持顺序、最大并发 1；关闭活跃连接后备用接管 |
| 正常重启 | 15 秒延迟消息发布确认后正常重启容器，Rabbit 就绪后取回等待消息；插件与主拓扑重启后仍有效 |

证据：[基础设施日志](rabbitmq-phase2-evidence/rabbit-infrastructure.log)、[SAC 日志](rabbitmq-phase2-evidence/sac-verification.log)、[重启日志](rabbitmq-phase2-evidence/restart-verification.log)、[主拓扑日志](rabbitmq-phase2-evidence/primary-topology.log)。

重启日志保留首次连接的 EOF：Docker restart 命令返回时 Rabbit 尚未就绪；确认 rabbitmq-diagnostics check_running 后重试接收成功。此为验收连接时机问题，成功结果与原始异常均保留。

## 4. 恢复工具验证

真实 SELECT preview 得到 9 个有效任务、0 个异常；使用专属 vhost `zhixiu-recovery-phase2` 完成实际 preview → publish → 同批重跑，未向主 vhost 发送，未写数据库，未启动业务消费者。

- 9/9 条记录为 CONFIRMED，FAILED=0、UNKNOWN=0。Rabbit confirm 的含义仍限于插件接收。
- 同批重跑未提供 RabbitMQ 密码也成功，结果 journal SHA-256 不变，证明已确认项跳过且未创建发布客户端。
- 从真实 Management receipts 核对 9 条唯一消息，73 项载荷及属性检查通过：四字段、原 deadline、Long 字符串、批次 timestamp、messageId、UTF-8、PERSISTENT 和投递后负 x-delay 一致，未出现同批重跑重复投递。
- 另有 22 项合成输入检查通过，覆盖工具边界与结果恢复；没有 Broker 发送。已确认重跑分支打开 DB 连接，但没有查询或写入。这部分不作为真实发布或实际 RocketMQ 配置查询证据。
- 专属 vhost 已 finally 删除。两轮隔离验收各有独立批次，均未产生正式存量恢复。

成功证据：[真实发布摘要](rabbitmq-phase2-evidence/recovery-real-rabbit-v2-proof.json)、[发布与重跑日志](rabbitmq-phase2-evidence/recovery-real-rabbit-v2-verification.log)、[真实载荷核对](rabbitmq-phase2-evidence/recovery-real-rabbit-v2-payload-verification.log)、[22 项合成检查](rabbitmq-phase2-evidence/recovery-fixture-22-v2/verification.log)。固定 manifest、results 和真实 receipts 一并保存在该证据目录。

首次载荷比对脚本存在数组展开错误，首次补录合成检查缺少 DB 凭证；原日志保留。修正验收脚本后的成功结果分别记录，未据此改动业务数据或恢复逻辑。

## 5. 用户自测与阶段三待执行

以下由用户按 [自测清单](RABBITMQ-SELF-TEST.md)与迁移手册执行，不能由基础类、隔离工具测试或只读快照替代：

- 完整主应用启动以及故障发送在实际业务事务中的回滚/捕获行为；SITE 重复落库幂等、操作日志入库、SMS/PUSH 占位及实际 VECTORIZE、SUMMARIZE 行为。
- SLA 实际数据库标记、不提前标记、过期处理、达标/终态与旧任务忽略、建单秒精度回读、续投确认后 ACK、续投失败进入 SLA DLQ，以及首次标记后的管理员通知。
- 停写、等待在途完成、旧六个普通组及最多 16 次重试排空、登记旧 DLQ、优雅停旧 JVM 和尾部消息复查。
- 基于停写时最新数据库创建正式 manifest、Review 后 publish，处理所有 FAILED/UNKNOWN 与 anomalies，并结合消费日志、数据库及确认结果验收；Queue 数量不能证明插件等待消息完整。
- 正式回滚前重新查询 RocketMQ 5.3.1 有效 timer 配置并核验整批超限保护；本轮补充只读查询已确认 timerWheelEnable=true、timerMaxDelaySec=259200（3 天），不代替将来回滚时的复核。

阶段三顺序及回滚操作以 [迁移手册第 8、9 节](RABBITMQ-MIGRATION.md)为准；新旧 SLA 消费者不得同时运行。隔离测试结束时的历史状态：Rabbit 容器 running、未 paused；vhost 仅 `/`，测试 vhost 均已删除；主 vhost 14 个队列的消费者、就绪消息及在途消息均为 0；当时旧 Broker/Namesrv 停止、完整主应用未启动。后续只读核验已运行旧 Broker 和旧应用，当前交付进程状态以最终交付说明为准。

### 5.1 本轮补充的只读排空核验

2026-11-21 12:55:14（Asia/Shanghai），最终版本 `rocketmq-drain.ps1` 完整执行 exitCode=0。证据目录：

```text
E:\docker\rabbitmq\recovery\rocketmq-final-script-verification-20261121-125345-913
```

- 六个普通业务组工单、SITE、SMS、PUSH、操作日志、AI 均为 Diff=0、Inflight=0；AI 有实际队列和历史偏移，未跳过。
- 发现五个实际 POP Retry Topic，逐一以原消费组及真实 Retry Topic 查询，均为 Diff=0、Inflight=0；AI 没有 Retry Topic。六组范围内未发现 DLQ Topic。
- 保存 9 个 topicStatus、完整 topicList、6 个业务进度文件、5 个 Retry 进度文件，以及包含 Topic 归属和文件映射的 snapshot.json。物理 Max Offset 属于历史记录，不作为未消费积压。
- 实际不带 `-t` 的旧查询返回 exitCode=0，但含 CODE:17 / SubCommandException；脚本按输出失败正确拒绝。原始失败证据在 `E:\docker\rabbitmq\recovery\rocketmq-output-failure-verification-20261121-124833-957\consumerProgress-missing-classic-retry.txt`。
- 脚本语法、UTF-8 无 BOM、CRLF 和 git diff --check 通过；脚本只查询，不启停应用或 Broker、不创建 Topic、不发布或删除消息。

这是旧应用仍在运行时的只读快照，未执行正式切换，也未完成旧 JVM 退出后的尾部消息复查。用户正式切换时仍须停写、等待在途完成、处理未来重试，优雅停旧包并再次运行快照；当前单次 0 不能直接宣告排空。
## 6. 风险与交付限制

- 延迟插件已停止维护；等待消息为节点本地 Mnesia 单份持久化，quorum 队列不保护等待窗口，禁用插件会丢失等待消息。正常重启实测不代表节点丢失或容器重建已验证。
- SLA confirm 不保证未来路由入队，插件不支持 mandatory，到期只尝试路由一次。
- 保留数据库与 MQ 非原子窗口、SLA 标记提交后通知失败可能遗漏告警，以及至少一次重复处理与操作日志可能重复的既有风险。
- 单节点 quorum 无节点级高可用；并发 1 的吞吐调优另行验收。工单收到后串行，跨发布连接无全局顺序保证，DLQ 人工补投可能晚于后续事件。
- 回滚待恢复任务超过实际 RocketMQ 延迟上限时必须停止简单回滚、保持停写并修复 RabbitMQ；不得缩短 deadline、强行发送或扩大 Broker 配置。

最终检查：修改/新增源码、脚本及交付文本 UTF-8 无 BOM、CRLF；PowerShell 脚本语法及 git diff --check 通过。证据 manifest 和结果 journal 保持原生成字节，不重写其内容或摘要。
