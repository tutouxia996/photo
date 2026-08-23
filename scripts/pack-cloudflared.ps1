# 从本机复制 cloudflared 到桌面部署包（打包隧道用，约 52MB）
# 用法: powershell -File scripts\pack-cloudflared.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$desktop = [Environment]::GetFolderPath("Desktop")
$deploy = Join-Path $desktop ([char]0x76f8 + [char]0x518c + [char]0x7f51 + [char]0x7ad9 + "-" + [char]0x90e8 + [char]0x7f72 + [char]0x5305)
$setup = Join-Path $deploy "cloudflared-setup"

if (-not (Test-Path "D:\cloudflared\cloudflared.exe")) {
    throw "未找到 D:\cloudflared\cloudflared.exe"
}

New-Item -ItemType Directory -Force -Path $setup | Out-Null
Copy-Item "D:\cloudflared\cloudflared.exe" (Join-Path $setup "cloudflared.exe") -Force
Copy-Item "$env:USERPROFILE\.cloudflared\cert.pem" (Join-Path $setup "cert.pem") -Force
Copy-Item "$env:USERPROFILE\.cloudflared\f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json" (Join-Path $setup "f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json") -Force

Write-Host "[OK] cloudflared-setup -> $setup"
Write-Host "部署机：复制整个部署包后运行 bin\install-cloudflared.bat"
