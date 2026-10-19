@echo off
setlocal EnableExtensions

REM ============================================================
REM  Edit FFMPEG_DIR if needed. Pure ASCII only (GBK-safe on CN Windows).
REM ============================================================
set "FFMPEG_DIR=D:\ffmpeg\bin"
set "JAVA_HEAP=-Xms1g -Xmx2g -XX:+UseG1GC"
REM ============================================================

echo.
echo ===== album start.bat =====
echo.

cd /d "%~dp0.."
if errorlevel 1 (
  echo [ERROR] Cannot find deploy root. Run from deploy-package\bin\start.bat
  goto fail
)
set "BIN_DIR=%CD%"
echo Deploy dir: %BIN_DIR%

set "JAVA_CMD="
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_CMD (
  where java >nul 2>&1
  if not errorlevel 1 set "JAVA_CMD=java"
)
if not defined JAVA_CMD (
  echo [ERROR] Java not found. Install JDK 8+ and set JAVA_HOME.
  goto fail
)
echo Java: %JAVA_CMD%
"%JAVA_CMD%" -version
echo.

if not exist "%BIN_DIR%\lib\sq-admin-server.jar" (
  echo [ERROR] Missing lib\sq-admin-server.jar
  goto fail
)

netstat -ano | findstr ":18080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo [ERROR] Port 18080 is in use. Kill old Java process first.
  goto fail
)

if not exist "%BIN_DIR%\resources" mkdir "%BIN_DIR%\resources"

if exist "%FFMPEG_DIR%\ffmpeg.exe" (
  set "PATH=%FFMPEG_DIR%;%PATH%"
  set "ALBUM_FFMPEG=%FFMPEG_DIR%\ffmpeg.exe"
  set "ALBUM_FFPROBE=%FFMPEG_DIR%\ffprobe.exe"
  echo FFmpeg: %FFMPEG_DIR%
) else (
  echo [WARN] FFmpeg not found at %FFMPEG_DIR%\ffmpeg.exe
)

set "EXTRA_CONFIG="
if exist "%BIN_DIR%\resources\application-local.yml" (
  set "EXTRA_CONFIG=--spring.config.additional-location=file:%BIN_DIR%/resources/"
  echo Found application-local.yml
) else (
  echo [WARN] No resources\application-local.yml
)

echo.
echo Starting server...
echo URL: http://127.0.0.1:18080/#/login
echo Profile: mysql-deploy,local
echo Heap: %JAVA_HEAP%
echo Wait 30-60 seconds for: Started AdminApplication
echo.

cd /d "%BIN_DIR%\lib"
if errorlevel 1 (
  echo [ERROR] Cannot enter lib folder
  goto fail
)

"%JAVA_CMD%" -server %JAVA_HEAP% -Dloader.path=. -jar sq-admin-server.jar --spring.profiles.active=mysql-deploy,local %EXTRA_CONFIG%
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo [FAILED] Java exit code %EXIT_CODE%
  echo If heap error, edit JAVA_HEAP to: -Xms512m -Xmx1024m
)
goto end

:fail
set "EXIT_CODE=1"
echo.
echo ===== START FAILED =====

:end
echo.
pause
exit /b %EXIT_CODE%
