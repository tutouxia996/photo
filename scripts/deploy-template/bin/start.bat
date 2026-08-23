@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0.."
set "BIN_DIR=%cd%"

rem === Java ===
set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD where java >nul 2>&1 && set "JAVA_CMD=java"
if not defined JAVA_CMD echo [ERROR] Java not found. Install JDK 8+ and set JAVA_HOME. && goto fail

if not exist "%BIN_DIR%\lib\sq-admin-server.jar" echo [ERROR] Missing lib\sq-admin-server.jar && goto fail

netstat -ano | findstr ":18080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 echo [ERROR] Port 18080 in use. Close old server first. && goto fail

if not exist "%BIN_DIR%\resources" mkdir "%BIN_DIR%\resources"

set "EXTRA_CONFIG="
if exist "%BIN_DIR%\resources\application-local.yml" set "EXTRA_CONFIG=--spring.config.additional-location=file:%BIN_DIR%/resources/"

echo.
echo Starting album site...
echo URL: http://127.0.0.1:18080/#/login
echo Profile: mysql-deploy,local
echo Config: %BIN_DIR%\resources\
echo Wait 30-60s for Started AdminApplication
echo.

cd /d "%BIN_DIR%\lib"
"%JAVA_CMD%" -server -Xms512m -Xmx1024m -Dloader.path=. -jar sq-admin-server.jar --spring.profiles.active=mysql-deploy,local %EXTRA_CONFIG%
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" echo [FAILED] exit code %EXIT_CODE%
goto end

:fail
set "EXIT_CODE=1"

:end
echo.
pause
exit /b %EXIT_CODE%
