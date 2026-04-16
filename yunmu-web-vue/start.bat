@echo off
chcp 65001 >nul
title 高原牛羊行为监测系统 - 启动脚本

echo ========================================
echo   高原牛羊行为监测系统
echo   正在启动开发服务器...
echo ========================================
echo.

REM 检查 node_modules 是否存在
if not exist "node_modules\" (
    echo [警告] 未检测到依赖，正在安装...
    echo.
    call npm install --registry=https://registry.npmmirror.com
    echo.
    echo [信息] 依赖安装完成！
    echo.
)

echo [信息] 启动 Vite 开发服务器...
echo.

call npm run dev

echo.
echo [信息] 按任意键退出...
pause >nul