# ============================================================================
#  智修云 · RocketMQ 本机 Topic 初始化
#  用途：RocketMQ 5.x 客户端不会自动创建 Topic，首次部署（或重建 Broker）后执行一次。
#  前置：容器 rocketmq-broker / rocketmq-namesrv 已启动（复用本机已有容器，不新建）。
#  用法：powershell -ExecutionPolicy Bypass -File docs/rocketmq-init.ps1
# ============================================================================
$ErrorActionPreference = 'Stop'

$docker = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$namesrv = 'rocketmq-namesrv:9876'
$cluster = 'DefaultCluster'

# topic 名 -> 额外参数（顺序消息 / 延迟消息）
# 注意：顺序开关与属性必须一次命令同时给出，mqadmin updateTopic 每次都会重写 TopicConfig，
#      分开执行会互相覆盖（只留 -o true 会退回 NORMAL，发送端报消息类型不匹配）。
$topics = [ordered]@{
    'workorder-event-topic' = @('-o', 'true', '-a', '+message.type=FIFO')   # 工单事件，按工单顺序消费
    'sla-timeout-topic'     = @('-a', '+message.type=DELAY')                # SLA 超时，任意秒延迟消息
    'notification-topic'    = @()                                           # 站内信 / 短信 / 推送扇出
    'operation-log-topic'   = @()                                           # 操作日志异步落库
    'ai-task-topic'         = @()                                           # 知识向量化、工单总结
}

foreach ($topic in $topics.Keys) {
    $extras = $topics[$topic]
    $mqArgs = @('exec', 'rocketmq-broker', 'sh', 'mqadmin', 'updateTopic',
                '-n', $namesrv, '-c', $cluster, '-t', $topic) + $extras
    Write-Host "==> $topic" -ForegroundColor Cyan
    & $docker @mqArgs
}

Write-Host ''
Write-Host '== 校验（应能看到 5 个业务 Topic 与 %DLQ%workorder-event-topic）' -ForegroundColor Yellow
& $docker exec rocketmq-broker sh mqadmin topicList -n $namesrv |
    Select-String -Pattern 'workorder-event-topic|sla-timeout-topic|notification-topic|operation-log-topic|ai-task-topic'
Write-Host '完成。若 workorder-event-topic 需支持顺序消息，注意 RocketMQ 5.x 会自动建 %DLQ%workorder-event-topic。' -ForegroundColor Green