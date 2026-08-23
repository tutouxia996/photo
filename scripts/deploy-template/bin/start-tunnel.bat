@echo off
setlocal

set "CLOUDFLARED=D:\cloudflared\cloudflared.exe"
if not exist "%CLOUDFLARED%" (
    echo cloudflared not found: %CLOUDFLARED%
    echo Edit this file and set CLOUDFLARED to your install path.
    pause
    exit /b 1
)

echo Starting Cloudflare tunnel (http2, album-home)...
echo Local:  http://127.0.0.1:18080
echo Public: https://album.pengorbit.top/#/login
echo Stop: Ctrl+C in this window
echo.

"%CLOUDFLARED%" tunnel --protocol http2 run album-home
pause
