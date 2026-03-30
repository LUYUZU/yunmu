#!/usr/bin/env pwsh
# 测试前端后端连接脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "云牧智感 - 前后端连接测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试 1: 检查 Java 环境
Write-Host "[1/6] 检查 Java 环境..." -NoNewline
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    if ($javaVersion) {
        Write-Host " ✓ 已安装" -ForegroundColor Green
        Write-Host "      $javaVersion" -ForegroundColor Gray
    } else {
        Write-Host " ✗ 未找到 Java" -ForegroundColor Red
    }
} catch {
    Write-Host " ✗ Java 未安装或不在 PATH 中" -ForegroundColor Red
}

# 测试 2: 检查 Maven
Write-Host "[2/6] 检查 Maven 环境..." -NoNewline
try {
    $mavenVersion = mvn -version 2>&1 | Select-String "Apache Maven"
    if ($mavenVersion) {
        Write-Host " ✓ 已安装" -ForegroundColor Green
        Write-Host "      $mavenVersion" -ForegroundColor Gray
    } else {
        Write-Host " ✗ 未找到 Maven" -ForegroundColor Red
    }
} catch {
    Write-Host " ✗ Maven 未安装或不在 PATH 中" -ForegroundColor Red
}

# 测试 3: 检查 Python
Write-Host "[3/6] 检查 Python 环境..." -NoNewline
try {
    $pythonVersion = python --version 2>&1
    if ($pythonVersion) {
        Write-Host " ✓ 已安装" -ForegroundColor Green
        Write-Host "      $pythonVersion" -ForegroundColor Gray
    } else {
        Write-Host " ✗ 未找到 Python" -ForegroundColor Red
    }
} catch {
    Write-Host " ✗ Python 未安装或不在 PATH 中" -ForegroundColor Red
}

# 测试 4: 检查 Java 后端端口
Write-Host "[4/6] 检查 Java 后端 (8080 端口)..." -NoNewline
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3 -UseBasicParsing 2>&1
    if ($response.StatusCode -eq 200) {
        Write-Host " ✓ 运行中" -ForegroundColor Green
        Write-Host "      状态：$($response.StatusCode) OK" -ForegroundColor Gray
    } else {
        Write-Host " ✗ 响应异常" -ForegroundColor Yellow
    }
} catch {
    Write-Host " ✗ 未运行或无法连接" -ForegroundColor Red
    Write-Host "      提示：运行 start-backend-fixed.bat 启动 Java 后端" -ForegroundColor Yellow
}

# 测试 5: 检查 Python 服务端口
Write-Host "[5/6] 检查 Python 服务 (5000 端口)..." -NoNewline
try {
    $response = Invoke-WebRequest -Uri "http://localhost:5000/health" -TimeoutSec 3 -UseBasicParsing 2>&1
    if ($response.StatusCode -eq 200) {
        Write-Host " ✓ 运行中" -ForegroundColor Green
        Write-Host "      状态：$($response.StatusCode) OK" -ForegroundColor Gray
    } else {
        Write-Host " ✗ 响应异常" -ForegroundColor Yellow
    }
} catch {
    Write-Host " ✗ 未运行或无法连接" -ForegroundColor Red
    Write-Host "      提示：运行 python run.py 启动 Python 服务" -ForegroundColor Yellow
}

# 测试 6: 检查前端文件
Write-Host "[6/6] 检查前端配置文件..." -NoNewline
$mainJsPath = "yunmu-web\js\main.js"
if (Test-Path $mainJsPath) {
    $content = Get-Content $mainJsPath -Raw
    if ($content -match "API_CONFIG") {
        Write-Host " ✓ API 配置已添加" -ForegroundColor Green
        
        # 提取 API 配置
        if ($content -match "BASE_URL:\s*'([^']+)'") {
            Write-Host "      BASE_URL: $($matches[1])" -ForegroundColor Gray
        }
        if ($content -match "TIMEOUT:\s*(\d+)") {
            Write-Host "      TIMEOUT: $($matches[1])ms" -ForegroundColor Gray
        }
        
        # 检查关键函数
        $functions = @('checkBackendStatus', 'loadAllDataFromBackend', 'handleGenerateMockData')
        foreach ($func in $functions) {
            if ($content -match "function\s+$func") {
                Write-Host "      ✓ 函数 $func 存在" -ForegroundColor Green
            } else {
                Write-Host "      ✗ 函数 $func 缺失" -ForegroundColor Red
            }
        }
    } else {
        Write-Host " ✗ API 配置未找到" -ForegroundColor Red
    }
} else {
    Write-Host " ✗ main.js 文件不存在" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "测试完成!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 总结
Write-Host "启动建议:" -ForegroundColor Yellow
Write-Host "  1. Java 后端：cd yunmu-backend && mvn spring-boot:run" -ForegroundColor White
Write-Host "  2. Python 服务：cd yunmu-python && python run.py" -ForegroundColor White
Write-Host "  3. 前端页面：直接打开 yunmu-web/index.html" -ForegroundColor White
Write-Host ""
