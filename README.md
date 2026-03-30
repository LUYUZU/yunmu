# 云牧智感 - 畜牧监测系统运行说明

## 项目结构

```
yunmu-python/          # Python 机器学习服务
  ├── app.py          # Flask 应用主入口
  ├── run.py          # 运行脚本
  ├── train.py        # 模型训练脚本
  ├── config.py       # 配置文件
  ├── requirements.txt # Python 依赖
  ├── features/       # 特征提取模块
  ├── models/         # 机器学习模型
  └── utils/          # 工具函数

yunmu-backend/        # Java Spring Boot 后端
  ├── src/main/java/com/yunmu/
  │   ├── controller/     # REST API 控制器
  │   ├── service/        # 业务逻辑
  │   ├── entity/         # 数据实体
  │   └── repository/     # 数据访问
  └── src/main/resources/
      └── application.yml # 应用配置
```

## 快速开始

### 1. Python 服务

```bash
# 进入 Python 目录
cd yunmu-python

# 安装依赖
pip install -r requirements.txt

# 训练模型（首次运行）
python train.py

# 运行服务
python run.py
```

Python 服务将在 http://localhost:5000 启动

### 2. Java 后端

```bash
# 确保 MySQL 和 Redis 已启动

# 修改配置文件
# 编辑 src/main/resources/application.yml
# 设置数据库连接信息

# 使用 Maven 运行
cd yunmu-backend
mvn spring-boot:run
```

Java 后端将在 http://localhost:8080 启动

### 3. 测试 API

```bash
# 测试 Python 服务
curl http://localhost:5000/health

# 测试行为预测
curl -X POST http://localhost:5000/api/predict/behavior \
  -H "Content-Type: application/json" \
  -d '{
    "animal_id": "cow_001",
    "accel_data": [0.1, 0.2, 0.15, ...],
    "sound_data": [0.05, 0.08, ...]
  }'
```

## API 接口

### Python 服务

| 接口 | 方法 | 描述 |
|------|------|------|
| /health | GET | 健康检查 |
| /api/predict/behavior | POST | 行为预测 |
| /api/predict/rumination | POST | 反刍预测 |
| /api/predict/feeding | POST | 采食预测 |
| /api/analyze/health | POST | 健康分析 |

### Java 后端

| 接口 | 方法 | 描述 |
|------|------|------|
| /api/behavior/rumination | POST | 分析反刍行为 |
| /api/behavior/feeding | POST | 分析采食行为 |
| /api/behavior/statistics/{animalId} | GET | 获取行为统计 |

## 配置说明

### Python 配置 (.env)

```
PORT=5000              # 服务端口
MODEL_PATH=models/trained/  # 模型路径
LOG_LEVEL=INFO         # 日志级别
```

### Java 配置 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yunmu_db
    username: root
    password: your_password

yunmu:
  python-service:
    url: http://localhost:5000  # Python 服务地址
```

## 常见问题

### 1. 模型加载失败
运行 `python train.py` 重新训练模型

### 2. 数据库连接失败
检查 MySQL 是否启动，修改 application.yml 中的数据库配置

### 3. Python 服务无法访问
检查防火墙设置，确保 5000 端口开放
