@echo off
echo ========================================
echo    云牧智感 - 高原牛羊行为监测系统
echo    正在启动本地服务器...
echo ========================================

cd /d "%~dp0"

:: 检查是否安装了 npx
where npx >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 npx，请先安装 Node.js
    echo 下载地址: https://nodejs.org/
    pause
    exit /b 1
)

:: 启动服务器
echo [启动] 服务器地址: http://localhost:3000
echo [提示] 按 Ctrl+C 可停止服务器
echo ========================================
start http://localhost:3000
npx serve .