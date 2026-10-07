# 云牧智感 — 运行指南

## 环境要求

- **Python**：3.8 ~ 3.11（Python 3.12+ 存在兼容性问题，不推荐）
- **Java**：17+（推荐 Java 21，与 Spring Boot 3.2.11 兼容）
- **数据库**：SQL Server 2019+（非 MySQL）
- **Redis**：6.0+
- **MQTT Broker**：120.27.235.176:1883（已配置，勿轻易修改）

---

## 快速开始

### 1. 初始化数据库

```sql
-- 连接 SQL Server（sqlcmd 或 SSMS）
CREATE DATABASE yunmu_db;
GO

-- JPA ddl-auto=update，实体表会自动创建
-- 如需手动初始化，运行 yunmu-backend 即可
```

### 2. 安装 Python 依赖

```bash
cd yunmu-python

# 方式一：使用清华镜像（推荐，国内速度更快）
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 方式二：使用 pip 默认源
pip install -r requirements.txt
```

> **依赖清单**：`flask`, `flask-cors`, `numpy`, `paho-mqtt`, `scikit-learn`, `joblib`, `requests`

### 3. 配置环境变量

```bash
cd yunmu-python

# 复制环境变量模板
cp .env.example .env

# 编辑 .env，填入以下关键配置：
#   DEEPSEEK_API_KEY=your_api_key          （LLM 功能必须，AI 助手对话依赖）
#   YUNMU_API_TOKEN=your_secure_token      （API 认证令牌）
#   ALLOWED_CALLBACK_HOSTS=localhost,127.0.0.1  （Java 回调地址，跨机部署时追加）

# Java 后端环境变量（Windows PowerShell 示例）：
#   $env:MQTT_USERNAME="your_broker_user"   # MQTT broker 用户名（不再默认 root）
#   $env:MQTT_PASSWORD="your_broker_pass"   # MQTT broker 密码（不再默认 root）
```

### 4. 训练模型（首次运行）

```bash
cd yunmu-python

# 训练脚本会自动生成合成数据并训练 RandomForest 模型
python train_model.py

# 训练完成后，模型保存至 ml_models/saved_models/posture_random_forest.pkl
```

### 5. 启动 Python ML 服务

```bash
cd yunmu-python

python app.py

# 服务将在 http://localhost:5000 启动
# 验证：curl http://localhost:5000/health
```

### 6. 启动 Java 后端

```bash
cd yunmu-backend

# 方式一：直接运行（开发模式，自动重载）
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/yunmu-backend-1.0.0.jar
```

> **确保 SQL Server 已启动**，默认配置 `jdbc:sqlserver://localhost:1433;databaseName=yunmu_db`

服务将在 http://localhost:8080 启动。

### 7. 停止服务

两个服务都是「父进程 + 子进程」结构，**必须按进程树终止（`/T`）**，只杀父进程会留下真正在监听端口的子进程：

```powershell
# 1. 先查出监听 8080 / 5000 的 PID
netstat -ano | findstr ":8080"
netstat -ano | findstr ":5000"

# 2. 查出它们的父进程（mvn / venv 启动器）
Get-CimInstance Win32_Process -Filter "ProcessId=<PID>" |
  Select-Object ProcessId, ParentProcessId, Name, CommandLine | Format-List

# 3. 按树终止父进程（/T 连带子进程，/F 强制）
taskkill /PID <父进程PID> /T /F
```

> **为什么不能只杀子进程**：
> - Java 侧 `mvn spring-boot:run` 会派生一个 `java.exe` 子进程，**端口由子进程持有**；只杀子进程，maven 可能重新拉起或残留。
> - Python 侧用 `.venv\Scripts\python.exe app.py` 启动时，venv 启动器会再派生一个解释器子进程。
>
> **推荐做法**：对父进程（maven 进程 / venv python 进程）执行 `/T /F`，一次性收干净整棵树。

**验证是否关干净**：

```powershell
# 两个端口都不应再有 LISTENING
netstat -ano | findstr ":8080"
netstat -ano | findstr ":5000"
```

> 若 `8080` 被**非本项目**进程占用（例如误启的其它服务），改用显式端口启动：
> `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`

---

## 环境变量说明

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `DEEPSEEK_API_KEY` | LLM 功能必填 | 空 | DeepSeek API Key |
| `DEEPSEEK_MODEL` | 否 | deepseek-chat | DeepSeek 模型名 |
| `LLM_PROVIDER` | 否 | deepseek | LLM 供应商：deepseek / openai / custom |
| `OPENAI_API_KEY` | 否 | 空 | OpenAI API Key（使用 openai 时填写） |
| `YUNMU_API_TOKEN` | 否 | yunmu-ml-secret-token-2024 | API 认证令牌（生产环境必须修改） |
| `ALLOWED_CALLBACK_HOSTS` | 否 | localhost,127.0.0.1 | Java 回调白名单（逗号分隔，跨机部署必须追加） |
| `MQTT_USERNAME` | MQTT 认证时必填 | 空 | MQTT broker 用户名（已去除 root/root 弱口令默认） |
| `MQTT_PASSWORD` | MQTT 认证时必填 | 空 | MQTT broker 密码（已去除 root/root 弱口令默认） |
| `DB_PASSWORD` | 否 | changeme | 数据库密码（Java 端使用 `${DB_PASSWORD:changeme}` 格式） |

---

## 接口测试

```bash
# Python ML 服务健康检查
curl http://localhost:5000/health

# LLM 网关状态（需配置 DEEPSEEK_API_KEY）
curl http://localhost:5000/api/llm/status

# AI 助手对话（通过 Java 后端转发，未配置 Key 时返回 503 降级提示）
curl -X POST http://localhost:8080/api/llm/chat -H "Content-Type: application/json" \
  -d '{"message":"你好，介绍一下系统"}'

# Java 后端 Swagger 文档
open http://localhost:8080/swagger-ui.html

# WebSocket 推送
ws://localhost:8080/ws
```

---

## 常见问题

### Q1：Python 3.12 兼容性问题

部分依赖（如 scikit-learn 的旧版本 wheel）在 Python 3.12 上可能无预编译包。建议使用 **Python 3.10 或 3.11**。

```bash
# 验证 Python 版本
python --version
# 应显示 3.8.x ~ 3.11.x
```

### Q2：数据库连接失败

1. 确认 SQL Server 服务已启动
2. 确认 `application.yml` 中 `url` / `username` / `password` 正确
3. 确认 SQL Server 允许 TCP/IP 连接（SQL Server Configuration Manager）
4. 确认防火墙开放 1433 端口

```bash
# 测试 SQL Server 连接（Windows）
telnet localhost 1433
```

### Q3：MQTT 连接失败

```bash
# 测试 MQTT Broker 连通性
ping 120.27.235.176

# 确认 Broker 凭据：通过环境变量 MQTT_USERNAME / MQTT_PASSWORD 提供
# Windows PowerShell：
#   $env:MQTT_USERNAME="your_broker_user"
#   $env:MQTT_PASSWORD="your_broker_pass"
# Linux/macOS：
#   export MQTT_USERNAME="your_broker_user"
#   export MQTT_PASSWORD="your_broker_pass"
# 说明：项目已去除 root/root 弱口令默认值，未配置时 MQTT 不启用认证（仅限无认证的本地/内网 broker）
```

### Q4：LLM 接口返回 503 Service Unavailable

1. 确认 `DEEPSEEK_API_KEY` 已写入 `.env`
2. 确认网络可访问 `https://api.deepseek.com`
3. 查看 Python 服务日志确认 LLM 网关初始化成功

### Q5：Java 回调 Python 被 SSRF 拦截

如果 Java 和 Python 部署在不同机器，Java 回调 Python 时会被 SSRF 白名单拦截。

**解决**：在 Python 服务环境变量中添加 Java 服务器地址：

```bash
# Python 端
export ALLOWED_CALLBACK_HOSTS=localhost,127.0.0.1,10.0.0.1,your-java-server
# 或在 .env 中
ALLOWED_CALLBACK_HOSTS=localhost,127.0.0.1,your-java-server.internal
```

### Q6：端口被占用

```bash
# Windows：查找占用端口的进程
netstat -ano | findstr 5000
netstat -ano | findstr 8080

# 结束进程（PID 为上一条命令的最后一列）
taskkill /PID <PID> /F

# 或修改配置使用其他端口
```

---

## AI 助手模块（前端对话）

前端 `yunmu-web-vue` 新增 **AI 助手** 页签（不破坏原有页签），支持与 LLM 多轮对话。

- **调用链路**：前端 Vue → Java `/api/llm/chat` → Python `llm_gateway.py`（DeepSeek/OpenAI 兼容）
- **前提**：Python 端 `.env` 配置 `DEEPSEEK_API_KEY`（或 `OPENAI_API_KEY` + `LLM_PROVIDER`）
- **未配置 Key**：Python 网关返回 503，Java 透传降级提示，前端显示"LLM 未配置"横幅，对话不会崩溃
- **能力探测**：前端进入页签时调用 `/api/llm/capabilities`（Java 转发 `/api/llm/status`）判断可用性
- **接口**：
  - `POST /api/llm/chat`：`{"history": [{"role":"user","content":"..."}], "message":"..."}`
  - `GET /api/llm/capabilities`：返回 `{"success":true,"available":bool,"capabilities":{...}}`

---

## 生产部署建议

### Python 服务

```bash
# 使用 gunicorn 多进程部署
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app
```

### Java 后端

```bash
mvn clean package -DskipTests
java -jar target/yunmu-backend-1.0.0.jar --spring.profiles.active=prod
```

### Redis + SQL Server

确保生产环境中数据库和 Redis 正确配置，并移除 `application.yml` 中的明文密码，改用环境变量 `${DB_PASSWORD}`。
