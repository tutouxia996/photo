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

call :auto_update
goto :start

rem ---------- 启动时自动检查并更新 cloudflared ----------
:auto_update
echo [更新] 检查 cloudflared 版本...

set "CUR_VER="
for /f "tokens=3" %%v in ('"%CLOUDFLARED%" --version 2^>^&1') do (
    set "CUR_VER=%%v"
    goto :have_cur
)
:have_cur
if not defined CUR_VER (
    echo [更新] 无法读取当前版本，跳过更新
    echo.
    exit /b 0
)
echo [更新] 当前: !CUR_VER!

set "LATEST="
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "try { (Invoke-RestMethod -Uri 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest' -TimeoutSec 15).tag_name } catch { '' }"`) do set "LATEST=%%v"

if not defined LATEST (
    echo [更新] 无法获取最新版本（网络或 GitHub 不可用），跳过更新
    echo.
    exit /b 0
)
echo [更新] 最新: !LATEST!

if /i "!CUR_VER!"=="!LATEST!" (
    echo [更新] 已是最新，无需更新
    echo.
    exit /b 0
)

for %%I in ("!CLOUDFLARED!") do (
    set "CF_DIR=%%~dpI"
    set "CF_NAME=%%~nxI"
)
set "CF_NEW=!CF_DIR!!CF_NAME!.new"
set "CF_OLD=!CF_DIR!!CF_NAME!.old"
set "DL_URL=https://github.com/cloudflare/cloudflared/releases/download/!LATEST!/cloudflared-windows-amd64.exe"

echo [更新] 正在下载 !LATEST! ...
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri '!DL_URL!' -OutFile '!CF_NEW!' -UseBasicParsing -TimeoutSec 180; exit 0 } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 (
    echo [更新] 下载失败，继续使用当前版本
    if exist "!CF_NEW!" del /f /q "!CF_NEW!" >nul 2>&1
    echo.
    exit /b 0
)

if not exist "!CF_NEW!" (
    echo [更新] 下载文件不存在，跳过更新
    echo.
    exit /b 0
)

if exist "!CF_OLD!" del /f /q "!CF_OLD!" >nul 2>&1
move /Y "!CLOUDFLARED!" "!CF_OLD!" >nul
if errorlevel 1 (
    echo [更新] 无法备份当前程序（可能被占用），跳过更新
    del /f /q "!CF_NEW!" >nul 2>&1
    echo.
    exit /b 0
)

move /Y "!CF_NEW!" "!CLOUDFLARED!" >nul
if errorlevel 1 (
    echo [更新] 替换失败，正在恢复旧版本...
    move /Y "!CF_OLD!" "!CLOUDFLARED!" >nul 2>&1
    del /f /q "!CF_NEW!" >nul 2>&1
    echo.
    exit /b 0
)

del /f /q "!CF_OLD!" >nul 2>&1
echo [更新] 已升级到 !LATEST!
echo.
exit /b 0

:start
echo 使用: %CLOUDFLARED%
echo 启动 Cloudflare 隧道 (http2, album-home)
echo 本机: http://127.0.0.1:18080
echo 外网: https://album.pengorbit.top/#/login
echo 停止: Ctrl+C
echo.

"%CLOUDFLARED%" tunnel --protocol http2 run album-home
pause
