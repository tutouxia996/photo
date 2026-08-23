@echo off & setlocal
chcp 65001 >nul
cd /d %~dp0..
set ROOT=%cd%
set SETUP=%ROOT%\cloudflared-setup

if not exist "%SETUP%\cloudflared.exe" (
    echo [错误] 缺少 cloudflared-setup\cloudflared.exe
    pause
    exit /b 1
)

if not exist "D:\cloudflared" mkdir "D:\cloudflared"
copy /Y "%SETUP%\cloudflared.exe" "D:\cloudflared\cloudflared.exe" >nul

if not exist "%USERPROFILE%\.cloudflared" mkdir "%USERPROFILE%\.cloudflared"
copy /Y "%SETUP%\cert.pem" "%USERPROFILE%\.cloudflared\cert.pem" >nul
copy /Y "%SETUP%\f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json" "%USERPROFILE%\.cloudflared\f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json" >nul

> "%USERPROFILE%\.cloudflared\config.yml" (
echo tunnel: f99d35ee-71e1-4f3c-9b01-4b5851de0cc7
echo credentials-file: %USERPROFILE%\.cloudflared\f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json
echo protocol: http2
echo.
echo ingress:
echo   - hostname: album.pengorbit.top
echo     service: http://127.0.0.1:18080
echo   - service: http_status:404
)

echo 安装完成。先 start.bat，再 start-tunnel.bat
pause
