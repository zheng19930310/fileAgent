@echo off
chcp 65001 >nul
echo ========================================
echo   FileAgent - AI文件助手
echo ========================================
echo.
echo 正在清理并重新编译...
echo.

cd /d %~dp0

:: 清理旧编译产物
call mvn clean -q

echo 编译中...
call mvn compile -q

if %errorlevel% neq 0 (
    echo.
    echo [错误] 编译失败，请检查代码
    pause
    exit /b 1
)

echo.
echo 启动应用...
echo.
echo ========================================
echo   打开浏览器: http://localhost:8080
echo ========================================
echo.

call mvn spring-boot:run

pause
