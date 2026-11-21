#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Container = 'rabbitmq',
    [string]$Vhost = '/',
    [string]$PluginPath = 'E:\docker\rabbitmq\pulgins\rabbitmq_delayed_message_exchange-v4.0.7.ez',
    [string]$IdentityPath = 'E:\docker\rabbitmq\recovery\node-identity.json'
)

# 复用已有容器；只安装插件和设置死信 policy，业务拓扑由应用启动门卫声明。
$ErrorActionPreference = 'Stop'
$DockerExe = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'

function Invoke-Docker([string[]]$Arguments) {
    $output = & $DockerExe @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker/RabbitMQ 命令失败：$($output -join [Environment]::NewLine)" }
    return ($output -join "`n")
}

function Write-Utf8([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    [IO.File]::WriteAllText($Path, ($Content -replace '\r?\n', "`r`n") + "`r`n", [Text.UTF8Encoding]::new($false))
}

if (-not (Test-Path -LiteralPath $PluginPath -PathType Leaf)) { throw "延迟插件包不存在：$PluginPath" }
$version = (Invoke-Docker @('exec', $Container, 'rabbitmqctl', 'version')).Trim()
if ($version -ne '4.0.9') { throw "RabbitMQ 版本冲突：需要 4.0.9，实际 $version；停止安装。" }
$hostname = (Invoke-Docker @('inspect', '--format', '{{.Config.Hostname}}', $Container)).Trim()
$nodeName = (Invoke-Docker @('exec', $Container, 'rabbitmqctl', 'eval', 'node().')).Trim()
$identity = [ordered]@{
    container = $Container
    hostname = $hostname
    nodeName = $nodeName
    rabbitmqVersion = $version
    pluginVersion = '4.0.7'
    pluginSha256 = (Get-FileHash -LiteralPath $PluginPath -Algorithm SHA256).Hash
    capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
}
if (Test-Path -LiteralPath $IdentityPath) {
    $previous = Get-Content -LiteralPath $IdentityPath -Raw | ConvertFrom-Json
    if ($previous.nodeName -ne $nodeName -or $previous.hostname -ne $hostname) {
        throw '节点身份与已有恢复记录不一致；保留数据并停止，不覆盖原节点身份。'
    }
} else {
    Write-Utf8 $IdentityPath ($identity | ConvertTo-Json)
}

Invoke-Docker @('cp', $PluginPath, "${Container}:/opt/rabbitmq/plugins/") | Write-Host
Invoke-Docker @('exec', $Container, 'rabbitmq-plugins', 'enable', 'rabbitmq_delayed_message_exchange') | Write-Host
$plugins = Invoke-Docker @('exec', $Container, 'rabbitmq-plugins', 'list', '-e')
if ($plugins -notmatch 'rabbitmq_delayed_message_exchange\s+4\.0\.7') { throw '延迟插件启用状态或版本不符合要求。' }
$plugins | Write-Host

$groups = @(
    'order-event-consumer-group', 'sla-consumer-group',
    'notify-site-consumer-group', 'notify-sms-consumer-group', 'notify-push-consumer-group',
    'operation-log-consumer-group', 'ai-task-consumer-group'
)
foreach ($group in $groups) {
    $definition = [ordered]@{
        'dead-letter-exchange' = 'zhixiu.dead-letter'
        'dead-letter-routing-key' = $group
        'dead-letter-strategy' = 'at-least-once'
        'overflow' = 'reject-publish'
    } | ConvertTo-Json -Compress
    $pattern = '^' + [Regex]::Escape($group) + '$'
    Invoke-Docker @('exec', $Container, 'rabbitmqctl', 'set_policy', '--vhost', $Vhost,
        '--apply-to', 'quorum_queues', '--priority', '50', "zhixiu-dlx.$group", $pattern, $definition) | Write-Host
}

$policies = Invoke-Docker @('exec', $Container, 'rabbitmqctl', 'list_policies', '--vhost', $Vhost, '--formatter', 'json') | ConvertFrom-Json
foreach ($group in $groups) {
    $policy = @($policies | Where-Object { $_.name -eq "zhixiu-dlx.$group" })
    # rabbitmqctl JSON 输出中的 definition 在 4.0.9 为 JSON 字符串，管理 API 则为对象。
    $actualDefinition = if ($policy.Count -eq 1 -and $policy[0].definition -is [string]) {
        $policy[0].definition | ConvertFrom-Json
    } elseif ($policy.Count -eq 1) {
        $policy[0].definition
    } else {
        $null
    }
    if ($policy.Count -ne 1 -or $policy[0].pattern -ne ('^' + [Regex]::Escape($group) + '$') -or
        $policy[0].'apply-to' -ne 'quorum_queues' -or $policy[0].priority -ne 50 -or
        $actualDefinition.'dead-letter-exchange' -ne 'zhixiu.dead-letter' -or
        $actualDefinition.'dead-letter-routing-key' -ne $group -or
        $actualDefinition.'dead-letter-strategy' -ne 'at-least-once' -or
        $actualDefinition.overflow -ne 'reject-publish') {
        throw "死信 policy 校验失败：$group"
    }
}
Write-Host "插件及七条精确死信 policy 已核验，节点身份保存于 $IdentityPath。"
Write-Host '下一步启动新应用，由启动门卫依序声明并核验拓扑；不要删除参数冲突的已有队列。'
