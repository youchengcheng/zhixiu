#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BrokerContainer = 'rocketmq-broker',
    [string]$NameServer = 'rocketmq-namesrv:9876',
    [string]$OutputDirectory = ('E:\docker\rabbitmq\recovery\rocketmq-snapshot-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)

# 只读快照。此脚本不冻结业务、不停 JVM、不删除消息、不自动判定排空。
$ErrorActionPreference = 'Stop'
$DockerExe = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "快照目录已存在，请为本次只读核验选择新目录，避免覆盖原始证据：$OutputDirectory"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
function Save-Snapshot([string]$Name, [string[]]$Arguments) {
    # mqadmin 5.3.1 可能捕获 SubCommandException 后仍以 0 退出；必须同时核验文本与退出码。
    # 禁止把 CODE: 17 / No topic route 等异常记录当成有效的消费进度快照。
    $PSNativeCommandUseErrorActionPreference = $false
    $output = & $DockerExe exec $BrokerContainer sh mqadmin @Arguments 2>&1
    $code = $LASTEXITCODE
    $text = $output -join "`n"
    [IO.File]::WriteAllText((Join-Path $OutputDirectory $Name), ($text -replace '\r?\n', "`r`n") + "`r`n", [Text.UTF8Encoding]::new($false))
    $reportedFailure = $text -match '(?im)\bSubCommandException\b|\bCODE\s*[:=]\s*(?!0\b)-?\d+\b|\b(?:No topic route|MQClientException|MQBrokerException|Remoting\w*Exception)\b|^\s*(?:Exception in thread\b|Caused by:|[\w.$]*(?:Exception|Error):)'
    if ($code -ne 0 -or $reportedFailure) {
        throw "mqadmin 查询失败（exitCode=$code，reportedFailure=$reportedFailure），原始证据已保存且不能视为有效快照：$Name"
    }
    if ($Arguments[0] -eq 'consumerProgress') {
        # 明确要求实际消费表及 totals，空输出/帮助文本也不能冒充 Diff=0、Inflight=0。
        if ($text -notmatch '(?m)^#Topic\s+.*#Diff\s+.*#Inflight\s+' -or
            $text -notmatch '(?m)^Consume Diff Total:\s*\d+\s*$' -or
            $text -notmatch '(?m)^Consume Inflight Total:\s*\d+\s*$') {
            throw "mqadmin 未返回完整 Diff/Inflight 消费表，原始证据已保存且不能视为有效快照：$Name"
        }
    } elseif ($Arguments[0] -eq 'topicStatus') {
        if ($text -notmatch '(?m)^#Broker Name\s+.*#Min Offset\s+.*#Max Offset\s+' -or
            $text -notmatch '(?m)^\S+\s+\d+\s+\d+\s+\d+(?:\s|$)') {
            throw "mqadmin 未返回完整 Topic 队列表，原始证据已保存且不能视为有效快照：$Name"
        }
    }
    return $text
}
function Get-ConsumerProgressRecord([string]$Group, [string]$Topic, [string]$File) {
    $consumerOutput = Save-Snapshot $File @('consumerProgress', '-n', $NameServer, '-g', $Group, '-t', $Topic)
    # 除 totals 外还要求请求 Topic 的数值队列行，避免把空表或其他 Topic 的 totals 当成证据。
    if ($consumerOutput -notmatch ('(?m)^' + [Regex]::Escape($Topic) + '\s+\S+\s+\d+\s+\d+\s+\d+\s+\d+\s+\d+(?:\s|$)')) {
        throw "消费表缺少请求 Topic 的数值队列行，不能视为有效快照：$Group / $Topic"
    }
    return [ordered]@{
        group = $Group
        topic = $Topic
        diff = [long][Regex]::Match($consumerOutput, '(?m)^Consume Diff Total:\s*(\d+)\s*$').Groups[1].Value
        inflight = [long][Regex]::Match($consumerOutput, '(?m)^Consume Inflight Total:\s*(\d+)\s*$').Groups[1].Value
        file = $File
    }
}
$groupTopics = @(
    [pscustomobject]@{ Group = 'order-event-consumer-group'; Topic = 'workorder-event-topic' }
    [pscustomobject]@{ Group = 'notify-site-consumer-group'; Topic = 'notification-topic' }
    [pscustomobject]@{ Group = 'notify-sms-consumer-group'; Topic = 'notification-topic' }
    [pscustomobject]@{ Group = 'notify-push-consumer-group'; Topic = 'notification-topic' }
    [pscustomobject]@{ Group = 'operation-log-consumer-group'; Topic = 'operation-log-topic' }
    [pscustomobject]@{ Group = 'ai-task-consumer-group'; Topic = 'ai-task-topic' }
)
$groups = @($groupTopics | ForEach-Object { $_.Group })
$progress = @()
foreach ($mapping in $groupTopics) {
    # 本机 5.3.1 使用 POP：通常只有 %RETRY%group_topic，没有经典 %RETRY%group。
    # 不带 -t 时 consumerProgress 可能先查询不存在的经典 retry 路由并抛 CODE:17。
    # 六个组逐项指定真实业务 Topic，普通消费进度与 Retry/DLQ 清单分别保留，不能创建缺失 Topic 补证。
    $group = $mapping.Group
    $progress += Get-ConsumerProgressRecord $group $mapping.Topic "consumerProgress-$group.txt"
}
# 不加 -c：该选项会过滤 Retry/DLQ。保留完整 Topic 清单供人工复核。
$topicOutput = Save-Snapshot 'topicList.txt' @('topicList', '-n', $NameServer)
$topics = @($groupTopics | ForEach-Object { $_.Topic } | Select-Object -Unique)
$retryAndDlq = @()
$retryAndDlqOwners = @()
foreach ($line in ($topicOutput -split '\r?\n')) {
    $topic = $line.Trim()
    foreach ($group in $groups) {
        # 兼容经典 %RETRY%group，以及 POP 的 group_topic / group+topic；DLQ 同样登记。
        $suffix = [Regex]::Escape($group) + '(?:[+_].*)?$'
        if ($topic -match ('^%(RETRY|DLQ)%' + $suffix)) {
            if ($topic -notin $retryAndDlq) {
                $retryAndDlq += $topic
                $retryAndDlqOwners += [ordered]@{ group = $group; topic = $topic; kind = $Matches[1] }
            }
            break
        }
    }
}
$retryProgress = @()
$retryIndex = 0
foreach ($mapping in $retryAndDlqOwners) {
    if ($mapping.kind -ne 'RETRY') { continue }
    $retryIndex++
    # Topic 物理 Max Offset 可能只是已消费历史，必须补查原组对实际 Retry Topic 的消费偏移及在途数。
    # DLQ 仅登记和查询 Topic 状态，不查询消费进度、不消费，也不自动回灌。
    $file = "retryConsumerProgress-{0:D3}-{1}.txt" -f $retryIndex, $mapping.group
    $retryProgress += Get-ConsumerProgressRecord $mapping.group $mapping.topic $file
}
$index = 0
$topicSnapshots = @()
foreach ($topic in (($topics + $retryAndDlq) | Select-Object -Unique)) {
    $index++
    $file = "topicStatus-{0:D3}.txt" -f $index
    Save-Snapshot $file @('topicStatus', '-n', $NameServer, '-t', $topic) | Out-Null
    $topicSnapshots += [ordered]@{ topic = $topic; file = $file }
}
$manifest = [ordered]@{
    capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
    groups = $groups
    consumerProgress = $progress
    retryConsumerProgress = $retryProgress
    retryAndDlqTopics = @($retryAndDlq | Select-Object -Unique)
    retryAndDlqOwners = $retryAndDlqOwners
    topicSnapshots = $topicSnapshots
    conclusion = '人工复核：单次 lag=0 不代表已排空；必须核对 Inflight、全部 retry 和已登记 DLQ，并在旧 JVM 完全退出后再次快照。'
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'snapshot.json'), (($manifest | ConvertTo-Json -Depth 5) -replace '\r?\n', "`r`n") + "`r`n", [Text.UTF8Encoding]::new($false))
Write-Host "六个普通消费组与 Retry/DLQ 只读快照已保存：$OutputDirectory"
Write-Host '旧组最多16次重试；停写后等未来重试耗尽或成功，旧JVM退出后复查；有尾部消息需继续运行旧包排空。'
