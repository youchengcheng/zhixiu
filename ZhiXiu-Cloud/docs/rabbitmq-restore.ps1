#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$IdentityPath = 'E:\docker\rabbitmq\recovery\node-identity.json',
    [string]$PluginPath = 'E:\docker\rabbitmq\pulgins\rabbitmq_delayed_message_exchange-v4.0.7.ez',
    [string]$Vhost = '/'
)

# 仅复用已经恢复的同名容器；不创建、删除容器，不禁用插件，不改动数据目录。
$ErrorActionPreference = 'Stop'
$DockerExe = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $IdentityPath -PathType Leaf)) { throw '缺少原节点身份文件；停止恢复，先找回原 hostname/nodename。' }
$identity = Get-Content -LiteralPath $IdentityPath -Raw | ConvertFrom-Json
$hostname = & $DockerExe inspect --format '{{.Config.Hostname}}' $identity.container
if ($LASTEXITCODE -ne 0) { throw '已有 RabbitMQ 容器不可读取。' }
$nodeName = & $DockerExe exec $identity.container rabbitmqctl eval 'node().'
if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ 必须已运行且保留原数据目录和节点身份。' }
if (($hostname -join "`n").Trim() -ne $identity.hostname -or ($nodeName -join "`n").Trim() -ne $identity.nodeName) {
    throw '恢复节点身份不一致；保持停写并找回原 hostname/nodename，不覆盖现有等待消息。'
}
if ((Get-FileHash -LiteralPath $PluginPath -Algorithm SHA256).Hash -ne $identity.pluginSha256) {
    throw '插件包 SHA256 与原安装记录不一致；停止恢复。'
}
& (Join-Path $PSScriptRoot 'rabbitmq-init.ps1') -Container $identity.container -Vhost $Vhost -PluginPath $PluginPath -IdentityPath $IdentityPath
if (-not $?) { throw '插件和 policy 恢复失败。' }
Write-Host '插件和 policy 已恢复；启动门卫通过后，结合 manifest 和数据库验收等待消息。'
