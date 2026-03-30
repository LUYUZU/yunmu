# 云牧智感 - 畜牧监测系统运行说明
## 项目概述
云牧智感是面向畜牧养殖的智能监测系统，集成**步数统计、姿态识别、GPS定位、异常预警**核心功能。系统采用**Python机器学习服务**处理传感器数据，**Java Spring Boot后端**提供业务接口与数据持久化，实现养殖数据智能化分析与管理。

## 项目结构
```
yunmu-ml-service/        # Python 机器学习服务
├── app.py               # Flask 应用主入口（含所有API接口）
├── mqtt_client.py       # MQTT数据接收客户端
├── requirements.txt      # Python 依赖清单
├── models/              # 算法模型模块
│   ├── __init__.py
│   ├── step_counter.py   # 步数统计模块（双参数融合计步算法）
│   ├── posture_model.py  # 姿态识别模块（站立/躺卧/采食/行走）
│   └── step_alert.py     # 步数异常预警模块（IQR算法）
└── utils/               # 工具模块
    ├── __init__.py
    └── gps_processor.py  # 北斗定位处理器（含WSN辅助定位）

yunmu-backend/           # Java Spring Boot 后端
├── src/main/java/com/yunmu/
│   ├── controller/       # REST API 控制器
│   ├── service/          # 业务逻辑层
│   ├── entity/           # 数据实体类
│   ├── repository/       # 数据访问层
│   └── mqtt/             # MQTT客户端（接收设备数据并转发）
└── src/main/resources/
    └── application.yml   # 应用核心配置文件
```

## 快速开始
### 1. Python 机器学习服务
```bash
# 进入Python服务目录
cd yunmu-ml-service

# 安装项目依赖
pip install -r requirements.txt

# 启动服务
python app.py
```
服务启动后默认访问地址：`http://localhost:5000`

#### 核心算法模块
| 模块名称 | 功能 | 算法说明 |
|----------|------|----------|
| StepCounter | 步数统计 | 双参数融合计步，支持姿态联动计步 |
| PostureClassifier | 姿态识别 | 基于加速度+角速度阈值判断 |
| StepAlert | 异常预警 | IQR算法检测步数异常数据 |
| GPSProcessor | 定位处理 | 北斗定位+WSN辅助定位融合 |

### 2. Java后端服务
1. 前置条件：确保**MySQL、Redis**服务已启动
2. 修改配置：编辑`src/main/resources/application.yml`，配置数据库、Python服务地址
3. 启动服务：
```bash
cd yunmu-backend
mvn spring-boot:run
```

### 3. MQTT 数据接收配置
```yaml
mqtt:
  broker-host: 120.27.235.176
  broker-port: 1883
  username: root
  password: root
```

## API 接口文档
### Python 机器学习服务核心接口
| 接口路径 | 请求方法 | 接口描述 |
|----------|----------|----------|
| /health | GET | 服务健康检查 |
| /api/device/data | POST | 接收设备传感器数据（Java转发调用） |
| /api/device/{device_id} | GET | 获取指定设备实时状态 |
| /api/devices | GET | 获取所有设备状态 |
| /api/steps/daily | GET | 获取每日步数统计数据 |
| /api/steps/history/{device_id} | GET | 获取设备步数历史数据 |
| /api/alert/summary | GET | 获取异常预警摘要 |

### 数据格式示例
#### 设备数据上报（POST /api/device/data）
```json
{
    "device_id": "cow_001",
    "longitude": 104.06,
    "latitude": 30.57,
    "move": 2,
    "steps": 500,
    "counter": 1,
    "accel_x": 0.3,
    "accel_y": 0.75,
    "accel_z": 9.65,
    "gyro_x": 0.45,
    "gyro_y": 0.2,
    "gyro_z": 0.15,
    "accel_y_history": [0.1, 0.2, 0.15],
    "timestamps": [0.02, 0.04, 0.06],
    "timestamp": 1700000000
}
```

#### 接口响应示例
```json
{
    "success": true,
    "device_id": "cow_001",
    "posture": "walking",
    "posture_confidence": 0.85,
    "calculated_steps": 1250,
    "step_frequency": 120.5,
    "activity_level": "medium",
    "anomaly": {
        "is_anomaly": false,
        "normal_range": [3000, 8000]
    },
    "daily_summary": {
        "date": "2024-01-15",
        "steps": 1250,
        "goal": 10000,
        "completion": 12.5
    }
}
```

## 测试客户端
项目提供`TestYunmuClientEnhanced.java`测试类，验证全量API接口：
```bash
# 编译测试类
javac -cp ".:jackson-*" TestYunmuClientEnhanced.java
# 运行测试
java -cp ".:jackson-*" TestYunmuClientEnhanced
```

#### 测试覆盖范围
- 健康检查（1项）
- 设备数据接收（6项：站立/躺卧/采食/行走/完整数据/GPS）
- 设备状态查询（2项）
- 步数统计（2项）
- 预警功能（1项）
- 综合测试（3项：多设备/并发/异常处理）

## 算法详解
### 1. 姿态识别（PostureClassifier）
基于加速度模长、角速度阈值判断，规则如下：
| 姿态 | 判断条件 |
|------|----------|
| 躺卧 (lying) | 加速度模长 < 9.0 |
| 行走 (walking) | Y轴加速度 > 0.5 且 X轴角速度 > 0.3 |
| 采食 (feeding) | Y轴角速度 > 0.4 |
| 站立 (standing) | 不满足以上条件 |

### 2. 步数统计（StepCounter）
**双参数融合计步算法**：
- 加速度阈值：`abs(accel_y) >= 0.3`
- 角速度阈值：`abs(gyro_angular_accel) >= 0.5`
- 最小步间隔：0.3秒
- 姿态联动：躺卧状态自动暂停计步

### 3. 异常预警（StepAlert）
基于**IQR四分位距算法**：
1. 计算历史步数的Q1（下四分位数）、Q3（上四分位数）
2. 正常范围：`[Q1 - 1.5*IQR, Q3 + 1.5*IQR]`
3. 异常分级：info / warning / critical

### 4. GPS定位（GPSProcessor）
- 支持北斗NMEA-0183协议解析
- WSN辅助定位：RSSI测距+质心算法
- 自动路径损耗校准，提升定位精度

## 配置说明
### Python 服务配置
修改`app.py`调整服务参数：
```python
# 服务启动配置
app.run(host='0.0.0.0', port=5000, debug=True)

# 算法可调参数
# step_counter.py：加速度阈值、角速度阈值、最小步间隔
# posture_model.py：姿态判断阈值字典
# step_alert.py：历史统计天数
```

### Java 后端配置（application.yml）
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yunmu_db
    username: root
    password: your_password
  redis:
    host: localhost
    port: 3379

yunmu:
  python-service:
    url: http://localhost:5000
  mqtt:
    broker-host: 120.27.235.176
    broker-port: 1883
    username: root
    password: root
```

## 部署流程
### 开发环境
1. 启动Python服务：`python app.py`
2. 启动Java后端：`mvn spring-boot:run`
3. 运行测试客户端验证功能

### 生产环境
```bash
# Python服务（使用gunicorn部署）
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app

# Java后端打包部署
mvn clean package
java -jar target/yunmu-backend-1.0.0.jar
```

## 常见问题
### 1. Python服务启动失败
```bash
# 检查5000端口是否被占用
lsof -i :5000
# 重新升级安装依赖
pip install -r requirements.txt --upgrade
```

### 2. MQTT连接失败
- 检查网络：`ping 120.27.235.176`
- 确认用户名/密码配置正确
- 检查服务器防火墙是否开放1883端口

### 3. 姿态识别不准确
- 调整`posture_model.py`中的阈值参数
- 检查传感器数据是否正常上报

### 4. 步数统计偏差大
- 调整`step_counter.py`中的加速度阈值
- 确认姿态识别结果正常（步数与姿态联动）

### 5. Java无法连接Python服务
- 测试Python服务可用性：`curl http://localhost:5000/health`
- 检查Java配置中Python服务地址是否正确

## 数据流向
```
设备传感器 → MQTT Broker → Java MQTT客户端 → Python /api/device/data
                                                    ↓
                                            ┌───────┴───────┐
                                            ↓               ↓
                                     姿态识别算法      步数统计算法
                                            ↓               ↓
                                     步数异常检测 ←─── 每日步数统计
                                            ↓
                                     设备状态存储
                                            ↓
                                    Java 查询接口
```

## 依赖版本
### Python 依赖
- Flask >= 2.3.0
- Flask-CORS >= 4.0.0
- numpy >= 1.24.0
- paho-mqtt >= 1.6.0

### Java 依赖
- Spring Boot 2.7.x
- MySQL Connector
- Redis Client
- Eclipse Paho MQTT Client
- Jackson Databind

## 主要更新内容
1. **项目结构修正**：同步Python实际项目结构（models/、utils/）
2. **API接口更新**：对齐app.py实际接口，完善路径与功能说明
3. **算法说明**：详细拆解四大核心算法的实现原理
4. **测试客户端**：补充TestYunmuClientEnhanced.java测试说明
5. **数据流向**：可视化展示传感器到业务接口的全数据流
6. **配置说明**：更新MQTT、算法参数配置项
7. **常见问题**：新增算法相关（姿态、步数）故障排查方案