@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "EXIT_CODE=0"

echo.
echo ========================================
echo   相册网站一键打包
echo ========================================
echo.

where powershell >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 PowerShell
    set "EXIT_CODE=1"
    goto :done
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\pack-album.ps1" -NoPause %*
set "EXIT_CODE=%ERRORLEVEL%"

:done
echo.
if not "%EXIT_CODE%"=="0" (
    echo 打包失败，退出码: %EXIT_CODE%
) else (
    echo 打包结束。
)
echo.
pause
exit /b %EXIT_CODE%
