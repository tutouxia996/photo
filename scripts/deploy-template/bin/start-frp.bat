@echo off & setlocal
chcp 65001 >nul

set "FRPC="
for %%P in (
    "D:\frp\frpc.exe"
    "C:\frp\frpc.exe"
    "%~dp0..\frp\frpc.exe"
) do (
    if exist %%~P set "FRPC=%%~P" & goto :found
)

echo [错误] 未找到 frpc.exe
echo 请先运行 bin\install-frpc.bat
pause
exit /b 1

:found
for %%I in ("%FRPC%") do set "FRP_DIR=%%~dpI"
set "CFG=%FRP_DIR%frpc.toml"
if not exist "%CFG%" (
    echo [错误] 缺少 %CFG%
    echo 请从部署包复制 frp\frpc.toml 到该目录
    pause
    exit /b 1
)

echo 使用: %FRPC%
echo 配置: %CFG%
echo 本机: http://127.0.0.1:18080
echo 外网: https://album.pengorbit.top/#/login
echo 请先确认相册 start.bat 已启动
echo 停止穿透: Ctrl+C
echo.

"%FRPC%" -c "%CFG%"
pause
