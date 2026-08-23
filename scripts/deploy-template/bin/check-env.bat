@echo off & setlocal
chcp 65001 >nul
cd /d %~dp0..

echo ===== 相册网站环境检查 =====
echo.

echo [1] Java
java -version 2>&1
if errorlevel 1 (echo    FAIL) else (echo    OK)
echo.

echo [2] 端口 18080
netstat -ano | findstr ":18080" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo    WARN - 已被占用
    netstat -ano | findstr ":18080"
) else (
    echo    OK
)
echo.

echo [3] Redis 6388
powershell -NoProfile -Command "if ((Test-NetConnection 127.0.0.1 -Port 6388 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 0 } else { exit 1 }"
if errorlevel 1 (echo    FAIL) else (echo    OK)
echo.

echo [4] MySQL 3306
powershell -NoProfile -Command "if ((Test-NetConnection 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 0 } else { exit 1 }"
if errorlevel 1 (echo    FAIL) else (echo    OK)
echo.

echo [5] application-mysql-deploy.yml
if exist "resources\application-mysql-deploy.yml" (echo    OK) else (echo    FAIL)
if exist "resources\application-local.yml" (echo    OK - application-local.yml) else (echo    WARN - 可选)
echo.

pause
