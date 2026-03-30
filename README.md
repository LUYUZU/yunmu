markdown
# 云牧智感 - 畜牧监测系统运行说明

## 项目概述

云牧智感是一个面向畜牧养殖的智能监测系统，集成了步数统计、姿态识别、GPS定位和异常预警等功能。系统采用 Python 机器学习服务处理传感器数据，Java Spring Boot 后端提供业务接口和数据持久化。

## 项目结构
yunmu-ml-service/ # Python 机器学习服务
├── app.py # Flask 应用主入口（含所有API接口）
├── mqtt_client.py # MQTT数据接收客户端
├── requirements.txt # Python 依赖
├── models/ # 算法模型模块
│ ├── init.py
│ ├── step_counter.py # 步数统计模块（双参数融合计步算法）
│ ├── posture_model.py # 姿态识别模块（站立/躺卧/采食/行走）
│ └── step_alert.py # 步数异常预警模块（IQR算法）
└── utils/ # 工具模块
├── init.py
└── gps_processor.py # 北斗定位处理器（含WSN辅助定位）

yunmu-backend/ # Java Spring Boot 后端
├── src/main/java/com/yunmu/
│ ├── controller/ # REST API 控制器
│ ├── service/ # 业务逻辑
│ ├── entity/ # 数据实体
│ ├── repository/ # 数据访问
│ └── mqtt/ # MQTT客户端（接收设备数据并转发）
└── src/main/resources/
└── application.yml # 应用配置

text

## 快速开始

### 1. Python 机器学习服务

```bash
# 进入 Python 目录
cd yunmu-ml-service

# 安装依赖
pip install -r requirements.txt

# 运行服务
python app.py
Python 服务将在 http://localhost:5000 启动

核心算法模块：

模块	功能	算法说明
StepCounter	步数统计	双参数融合计步，支持姿态联动
PostureClassifier	姿态识别	基于加速度和角速度阈值判断
StepAlert	异常预警	IQR算法检测步数异常
GPSProcessor	定位处理	北斗+WSN辅助定位
2. Java 后端服务
bash
# 确保 MySQL 和 Redis 已启动

# 修改配置文件
# 编辑 src/main/resources/application.yml
# 设置数据库连接信息和Python服务地址

# 使用 Maven 运行
cd yunmu-backend
mvn spring-boot:run
Java 后端将在 http://localhost:8080 启动

3. MQTT 数据接收（可选）
Java 后端内置 MQTT 客户端，负责接收设备上报的传感器数据并转发给 Python 服务处理。

MQTT 配置：

yaml
mqtt:
  broker-host: 120.27.235.176
  broker-port: 1883
  username: root
  password: root
API 接口文档
Python 机器学习服务 API
核心数据接口
接口	方法	描述
/health	GET	健康检查
/api/device/data	POST	接收设备传感器数据（Java转发调用）
/api/device/{device_id}	GET	获取指定设备状态
/api/devices	GET	获取所有设备状态
/api/steps/daily	GET	获取每日步数统计
/api/steps/history/{device_id}	GET	获取设备步数历史
/api/alert/summary	GET	获取预警摘要
数据格式示例
设备数据上报 (POST /api/device/data)

json
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
    "accel_y_history": [0.1, 0.2, 0.15, ...],
    "timestamps": [0.02, 0.04, 0.06, ...],
    "timestamp": 1700000000
}
响应示例：

json
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
测试客户端
项目提供了 Java 测试客户端 TestYunmuClientEnhanced.java，用于验证所有 API 接口：

bash
# 编译并运行测试
javac -cp ".:jackson-*" TestYunmuClientEnhanced.java
java -cp ".:jackson-*" TestYunmuClientEnhanced
测试覆盖：

健康检查 (1项)

设备数据接收 (6项：站立/躺卧/采食/行走/完整数据/GPS)

设备状态查询 (2项)

步数统计 (2项)

预警功能 (1项)

综合测试 (3项：多设备/并发/异常处理)

算法详解
1. 姿态识别 (PostureClassifier)
基于加速度模长和角速度的阈值判断：

姿态	判断条件
躺卧 (lying)	加速度模长 < 9.0
行走 (walking)	Y轴加速度 > 0.5 且 X轴角速度 > 0.3
采食 (feeding)	Y轴角速度 > 0.4
站立 (standing)	其他情况
2. 步数统计 (StepCounter)
双参数融合计步算法：

加速度阈值检测：abs(accel_y) >= 0.3

角速度阈值检测：abs(gyro_angular_accel) >= 0.5

最小步间隔：0.3秒

姿态联动：躺卧时暂停计步

3. 异常预警 (StepAlert)
基于 IQR（四分位距）算法：

计算历史步数的 Q1、Q3

正常范围：[Q1 - 1.5*IQR, Q3 + 1.5*IQR]

异常分级：info / warning / critical

4. GPS定位 (GPSProcessor)
支持北斗 NMEA-0183 协议解析

WSN 辅助定位（RSSI 测距 + 质心算法）

自动路径损耗校准

配置说明
Python 服务配置
修改 app.py 中的配置：

python
# 服务配置
app.run(host='0.0.0.0', port=5000, debug=True)

# 算法参数（可在各模块中调整）
# step_counter.py: accel_threshold, gyro_threshold, min_step_interval
# posture_model.py: thresholds 字典
# step_alert.py: history_days
Java 后端配置 (application.yml)
yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yunmu_db
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379

yunmu:
  python-service:
    url: http://localhost:5000
  mqtt:
    broker-host: 120.27.235.176
    broker-port: 1883
    username: root
    password: root

部署流程
开发环境
启动 Python 服务：python app.py

启动 Java 后端：mvn spring-boot:run

运行测试客户端验证

生产环境
bash
# Python 服务（使用 gunicorn）
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app

# Java 后端
mvn clean package
java -jar target/yunmu-backend-1.0.0.jar
常见问题
1. Python 服务启动失败
bash
# 检查端口占用
lsof -i :5000

# 重新安装依赖
pip install -r requirements.txt --upgrade
2. MQTT 连接失败
检查网络连通性：ping 120.27.235.176

确认用户名密码正确

检查防火墙是否开放 1883 端口

3. 姿态识别不准确
调整 posture_model.py 中的阈值参数

检查传感器数据是否正常上报

4. 步数统计偏差较大
检查 step_counter.py 中的加速度阈值

确认姿态识别结果是否正确（步数与姿态联动）

5. Java 无法连接 Python 服务
确认 Python 服务已启动：curl http://localhost:5000/health

检查 Java 配置文件中的 Python 服务地址

数据流向
text
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
依赖版本
Python
Flask >= 2.3.0

Flask-CORS >= 4.0.0

numpy >= 1.24.0

paho-mqtt >= 1.6.0

Java
Spring Boot 2.7.x

MySQL Connector

Redis Client

Eclipse Paho MQTT Client

Jackson Databind

text

## 主要更新内容

1. **项目结构修正**：反映了实际的 Python 项目结构（models/、utils/）

2. **API 接口更新**：根据 `app.py` 中的实际接口进行了修正：
   - `/api/device/data` - 核心数据接收接口
   - `/api/device/{device_id}` - 设备状态查询
   - `/api/devices` - 所有设备查询
   - `/api/steps/daily` - 每日步数
   - `/api/steps/history/{device_id}` - 步数历史
   - `/api/alert/summary` - 预警摘要

3. **算法说明**：详细说明了四个核心算法的实现原理

4. **测试客户端说明**：添加了 `TestYunmuClientEnhanced.java` 的测试说明

5. **数据流向图**：清晰展示了从传感器到最终查询的完整数据流

6. **配置说明**：更新了 MQTT 和算法参数配置

7. **常见问题**：增加了姿态识别、步数统计等算法相关问题的排查方法