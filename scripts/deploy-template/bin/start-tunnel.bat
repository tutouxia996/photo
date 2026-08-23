@echo off & setlocal enabledelayedexpansion
chcp 65001 >nul

set "CLOUDFLARED="

rem 按优先级查找 cloudflared.exe
for %%P in (
    "D:\cloudflared\cloudflared.exe"
    "C:\cloudflared\cloudflared.exe"
    "%ProgramFiles%\cloudflared\cloudflared.exe"
    "%LOCALAPPDATA%\cloudflared\cloudflared.exe"
) do (
    if exist %%~P set "CLOUDFLARED=%%~P" & goto :found
)

where cloudflared >nul 2>&1
if %errorlevel%==0 (
    for /f "delims=" %%i in ('where cloudflared 2^>nul') do set "CLOUDFLARED=%%i" & goto :found
)

echo [错误] 未找到 cloudflared.exe
echo.
echo 部署机需单独安装 Cloudflare 隧道客户端，任选一种方式：
echo   1. 从开发机复制整个 D:\cloudflared\ 文件夹到本机同路径
echo   2. 下载: https://github.com/cloudflare/cloudflared/releases
echo      解压 cloudflared-windows-amd64.exe 为 D:\cloudflared\cloudflared.exe
echo   3. 或修改本文件顶部，把 CLOUDFLARED 改成你的实际路径
echo.
echo 还需复制凭证到 %%USERPROFILE%%\.cloudflared\ ：
echo   - config.yml
echo   - cert.pem
echo   - f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json
echo.
pause
exit /b 1

:found
if not exist "%USERPROFILE%\.cloudflared\config.yml" (
    echo [警告] 未找到 %%USERPROFILE%%\.cloudflared\config.yml
    echo 请从开发机复制 .cloudflared 目录后再启动隧道
    pause
    exit /b 1
)

echo 使用: %CLOUDFLARED%
echo 启动 Cloudflare 隧道 (http2, album-home)
echo 本机: http://127.0.0.1:18080
echo 外网: https://album.pengorbit.top/#/login
echo 停止: Ctrl+C
echo.

"%CLOUDFLARED%" tunnel --protocol http2 run album-home
pause
