@echo off & setlocal enabledelayedexpansion

rem 相册网站家用启动脚本（mysql-dev + local，端口 18080）
rem 如需指定 JDK，取消下一行注释并改成你的路径：
rem set JAVA_HOME=D:\App\Java\jdk1.8.0_172

if "%JAVA_HOME%"=="" (
    echo 未找到 JAVA_HOME，请安装 JDK 8+ 并配置环境变量，或在本文件顶部指定 JAVA_HOME
    pause
    exit /b 1
)

cd /d %~dp0..
set BIN_DIR=%cd%

cd %BIN_DIR%\lib
for /f "delims=" %%i in ('dir /a-d /b /on sq-admin-server*.jar 2^>nul') do set BOOT_JAR=%%i
if not defined BOOT_JAR (
    echo 未找到 sq-admin-server*.jar，请先运行仓库根目录 pack-album.bat 打包
    pause
    exit /b 1
)

set LIB_JARS=
for %%i in ("*") do (
    if /I not "%%~nxi"=="%BOOT_JAR%" (
        set LIB_JARS=!LIB_JARS!%BIN_DIR%\lib\%%~nxi;
    )
)

cd %BIN_DIR%\bin
set JAVA_OPTS=-server -Xms2g -Xmx2g -Xmn256m -XX:PermSize=128m -Xss256k

echo 启动相册网站 http://127.0.0.1:18080/#/login
echo 停止：本窗口 Ctrl+C
echo.

"%JAVA_HOME%\bin\java" %JAVA_OPTS% -Xbootclasspath/a:%LIB_JARS% -jar %BIN_DIR%\lib\%BOOT_JAR% --spring.profiles.active=mysql-dev,local
pause
