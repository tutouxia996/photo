@echo off & setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d %~dp0..

set "FRP_VER=0.61.1"
set "FRP_DIR=D:\frp"
set "ZIP=%TEMP%\frp_windows_amd64.zip"
set "URL=https://github.com/fatedier/frp/releases/download/v%FRP_VER%/frp_%FRP_VER%_windows_amd64.zip"

echo 安装 frpc 到 %FRP_DIR% ...
if not exist "%FRP_DIR%" mkdir "%FRP_DIR%"

powershell -NoProfile -Command ^
  "try { Invoke-WebRequest -Uri '%URL%' -OutFile '%ZIP%' -UseBasicParsing -TimeoutSec 180; exit 0 } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 (
    echo [错误] 下载失败。可手动下载后解压 frpc.exe 到 %FRP_DIR%
    echo %URL%
    pause
    exit /b 1
)

powershell -NoProfile -Command ^
  "Expand-Archive -Path '%ZIP%' -DestinationPath '%TEMP%\frp_extract' -Force"
copy /Y "%TEMP%\frp_extract\frp_%FRP_VER%_windows_amd64\frpc.exe" "%FRP_DIR%\frpc.exe" >nul
copy /Y "%~dp0..\frp\frpc.toml" "%FRP_DIR%\frpc.toml" >nul

echo.
echo [OK] %FRP_DIR%\frpc.exe
echo [OK] %FRP_DIR%\frpc.toml
echo 下一步：先 start.bat，再 start-frp.bat
pause
