# 云牧智感 — 高原牛羊行为AI监测系统

> 集成步数统计、姿态识别（ML + 规则双轨）、GPS 定位、异常预警、LLM 智能解释的畜牧监测平台。

---

## 项目结构

```
yunmu-python/           # Python ML 服务（Flask）
│   ├── app.py                  # Flask 主入口（含全部 API）
│   ├── train_model.py          # 模型训练脚本
│   ├── llm_gateway.py          # LLM 网关（DeepSeek / OpenAI 兼容）
│   ├── .env.example             # 环境变量模板
│   ├── requirements.txt        # Python 依赖
│   ├── models/                 # 算法模块
│   │   ├── step_counter.py     # 双参数融合步数统计
│   │   ├── posture_model.py    # 规则引擎姿态识别
│   │   └── step_alert.py       # IQR 步数异常检测
│   ├── ml_models/              # 机器学习模块
│   │   ├── trainer.py          # RandomForest / SVM / Logistic 训练器
│   │   ├── posture_predictor.py # ML 姿态分类器（含置信度回退）
│   │   ├── data_collector.py   # 数据采集与持久化
│   │   └── saved_models/       # 训练好的模型文件
│   └── utils/
│       └── gps_processor.py    # 北斗 NMEA 解析 + WSN 辅助定位

yunmu-backend/          # Java Spring Boot 后端
│   └── src/main/java/com/yunmu/
│       ├── controller/          # REST API（Animal/Behavior/Health/Location/Posture 等）
│       ├── service/impl/        # 业务逻辑（MQTT 接入 / 数据持久化 / WebSocket 推送）
│       ├── service/mqtt/       # MQTT 消息处理器
│       ├── service/websocket/  # 实时推送服务
│       ├── entity/             # JPA 实体（Animal / BehaviorResult / StepCount 等）
│       ├── dto/                # 数据传输对象
│       ├── repository/          # JPA 数据访问层
│       └── config/             # Redis / WebSocket / RestTemplate / MQTT 配置

yunmu-web-vue/          # Vue 前端（Vue 2.7 + Element UI + ECharts）
    └── ...                     # 7 个 Tab：总览 / 动物 / 数据 / 位置 / 姿态 / 步数 / 分析
```

---

## 技术栈

| 模块 | 技术选型 | 说明 |
|------|----------|------|
| 前端 | Vue 2.7 + Element UI + ECharts + Vite | 实时数据可视化 |
| Java 后端 | Spring Boot 3.2.11 + Java 21 | REST API、MQTT 接入、数据持久化、WebSocket 推送、Spring @Scheduled 定时任务 |
| 数据库 | SQL Server（jdbc:sqlserver://localhost:1433） | JPA/Hibernate ORM |
| 缓存 | Redis | 实时数据缓存、会话管理 |
| 消息队列 | MQTT（120.27.235.176:1883） | 设备数据接入 |
| Python ML | Flask + scikit-learn + numpy + joblib | 姿态识别、步数统计、异常检测、LLM 网关 |
| LLM | DeepSeek / OpenAI 兼容 API | 异常解释、报告生成、行为语义化、AI 助手多轮对话 |

---

## 数据流向

```
设备传感器（真实项圈）
    │
    ▼ MQTT（tcp://120.27.235.176:1883）
Java MqttMessageHandler ◀── DeviceSimulatorService（演示工具：无设备时在进程内注入同协议报文）
    │
    ▼ HTTP POST（X-API-Token 鉴权）
Python /api/device/data（Flask）
    ├─ GPS 解析（NMEA-0183 + WSN RSSI）
    ├─ 姿态识别（ML 优先，置信度 <0.6 回退规则引擎）
    ├─ 步数统计（双参数融合 + 姿态联动）
    ├─ 异常检测（IQR / IsolationForest）
    └─ LLM 旁路（异常解释 / 报告生成 / 行为语义化）
    │
    ▼ 异步 HTTP POST /api/ml/callback（X-ML-Callback-Token 鉴权 + SSRF 白名单校验）
Java MlCallbackController（回调入口）
    ├─ 数据持久化（SQL Server，姿态统一存英文规范标签）
    ├─ WebSocket 推送前端（posture_update / realtime_data / health_alert）
    └─ 异常告警（阈值规则 + LLM 解释）
```

> 说明：两条链路都是**先写库、再经 WebSocket 推送**（推送内容取自刚保存的内存对象，不回读数据库）。
> 数据库是权威数据源，WebSocket 仅作实时增量通知，断连不影响落库。

---

## 设备数据模拟器（演示工具）

无真实项圈设备、或 MQTT broker 不可达时，可用后端内置的设备数据模拟器产出演示数据。
模拟器**按设备上报协议**构造报文，并从 `MqttMessageHandler.handleMessage(topic, payload)`
注入，因此报文之后的所有环节（Java 解析 → Python 推理 → 回调入库 → WebSocket 推送）**全部是真实链路**，
前端不再存在任何本地伪造数据。

> **定位声明**：模拟器输出的是**模拟数据**，仅用于功能演示与联调，
> **不得作为真实现场采集数据，也不得作为模型效果验证数据**。正式材料中须如实标注为模拟器。

### 接口

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| `/api/simulator/start` | POST | 启动**实时**模拟器，请求体可省略 |
| `/api/simulator/backfill` | POST | **历史回放**：按历史时间戳回填过去一段时间的数据 |
| `/api/simulator/stop` | POST | 停止实时模拟器（已入库数据保留），并中止进行中的回放 |
| `/api/simulator/status` | GET | 查询运行状态与回放进度，供前端刷新页面后同步按钮状态 |

启动参数（均为可选）：

```json
{
  "codes": ["7249", "7250", "7251"],
  "intervalMs": 5000,
  "durationSeconds": 0,
  "anomalyRate": 0.06
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `codes` | `yunmu.simulator.default-codes` | 项圈编号列表，**必须已在 `animals.device_code` 完成绑定**，否则数据会被链路丢弃 |
| `intervalMs` | 5000 | 上报间隔（毫秒），最小 500 |
| `durationSeconds` | 0 | 自动停止时长（秒），0 表示不自动停止 |
| `anomalyRate` | 0.06 | **每 tick 启动一个异常 episode 的概率**（心率/体温越界、长时间躺卧、异常剧烈运动），传 0 则只发正常数据。注意：episode 会持续多个 tick，因此实际异常包占比显著高于该值（0.06 实测约 20%），**不是**「每包异常概率」 |
| `baseLatitude` | 该个体最近位置 | 起始纬度（十进制度数）。不传则取该动物最近一次定位，无历史时落到 29.65 |
| `baseLongitude` | 该个体最近位置 | 起始经度（十进制度数）。不传则取该动物最近一次定位，无历史时落到 91.10 |

> **注意**：这两个字段是**十进制度数**（如 `29.65`），接口内部会由
> `GpsUtils.decimalToDegreeMinute()` 转成协议要求的度分格式再上报。
> `start` 与 `backfill` 的响应都会回显 `baseLatitude` / `baseLongitude`，
> 建议在启动一次耗时数分钟的回放前先核对锚点，避免整批数据落错位置（见常见问题 8）。

### 历史回放（backfill）

实时模拟器只能产生「从现在开始」的数据，趋势分析 / 历史数据 / 步数汇总等页面在演示初期会是空的。
`/api/simulator/backfill` 用于把过去一段时间的数据一次性补齐，**同样走完整链路**。

```json
{
  "codes": ["7249", "7250", "7251"],
  "backfillHours": 24,
  "backfillIntervalSeconds": 300,
  "anomalyRate": 0.06
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `backfillHours` | 24 | 回填过去多少小时，上限 168（7 天） |
| `backfillIntervalSeconds` | 300 | 采样间隔（秒），范围 30~3600 |
| `anomalyRate` | 0 | 异常 episode 启动概率，传 0.06 可让告警类页面也有内容（实际异常包占比约 20%，见上表说明） |

实现要点：

- **回放与实时只差一个时间戳**。`MqttMessageHandler` 解析时间戳时优先读报文的 `ts` 字段，
  据此写入 `sensor_data.timestamp`、同时透传给 Python 端；因此只需把 `ts` 设为历史时刻，
  数据在库中的时间分布即为历史分布，**链路代码一行都不需要改**。
- 设备间并行、设备内串行，保证每只动物的步数累计与位置轨迹随时间连续演进。
- 步数按「注入间隔秒数 × 步频」累积（行走 0.30~0.55 步/秒、奔跑 1.00~1.80 步/秒）。
  5 分钟一个采样点时，单日累计落在真实放牧水平（数千至上万步）；
  若按包数累加，回放几百包只能涨几百步，数值会明显失真。
- 单设备上限 3000 点，超出自动截断（响应中 `truncated: true`）。
- 回放期间用 `GET /api/simulator/status` 查看进度（`backfillDone` / `backfillTotal`）；
  调用 `/api/simulator/stop` 会中止回放。

> 回放产出同样属于**模拟数据**，只是「数据产生方式与真实设备一致」，不得表述为真实现场采集数据。

### 数据生成逻辑

- **六轴数据**按活动档位（`lying` / `feeding` / `standing` / `walking` / `running`）采样，
  均值与标准差与 `ml_models/data_collector.py` 的合成训练数据**同分布**，
  以保证姿态分类结果稳定落在预期类别，演示时不出现"永远同一个姿态"。
- 活动档位带**驻留时长**（4~25 包），模拟真实日节律，不会每包乱跳。
- **定位**：`JD`/`WD` 按协议输出 **NMEA 度分格式（DDMM.MMMM）**，例如经度 91.10° 输出 `9106.000`。
  接收端 `GpsUtils.degreeMinuteToDecimal` 会按度分解析，两侧必须同约定（详见常见问题 8）。
  锚点默认取该个体最近一次位置，无历史时落到高原牧场基准点（29.65°N, 91.10°E 拉萨一带）。
- **生理指标按物种分区生成**，且严格落在 `HealthMonitoringServiceImpl.ANIMAL_NORMAL_RANGES`
  的正常区间内（牛 心率 60~80 / 体温 38.0~39.5；羊 心率 70~90 / 体温 38.5~40.0）。
  健康模块采用严格区间判定，非异常时段越界会立刻生成 CRITICAL 告警，因此模拟器**必须**把
  「健康个体」生成在正常区间内（详见常见问题 9）。
- 异常值刻意落在链路过滤阈值之内（心率 96~115 < 120、体温 40.3~41.2 < 42），
  否则会被 `DataCollectionServiceImpl.filterAbnormalData` 整包丢弃；
  同时异常值下限**高于牛、羊两个物种的上限**，保证无论哪种动物都能被判出异常。
- `bushu` 按协议语义输出**通电后累计步数**，Python 侧求差得到本次增量后再入库。

### 配置项（application.yml，均有默认值）

```yaml
yunmu:
  simulator:
    enabled: true            # 生产环境可置 false 关闭该演示工具
    default-codes: 7249,7250,7251
    default-interval-ms: 5000
    default-anomaly-rate: 0.06
```

---

## 核心算法

### 1. 步数统计算法（StepCounter）

**双参数融合计步**：
- 加速度阈值：`abs(accel_y) >= 0.3`
- 角速度阈值：`abs(gyro_angular_accel) >= 0.5`
- 最小步间隔：0.3 秒
- 姿态联动：lying 状态自动暂停计步

设备级隔离，每设备独立计数器，重启后计数器归零。

### 2. 姿态识别算法（PostureClassifier / MLPostureClassifier）

**标准姿态标签（ML 5 类）**：

| 标签 | 定义 | 典型特征 |
|------|------|----------|
| `standing` | 站立 | 正常重力加速度，倾斜角 60°~120° |
| `lying` | 卧地 | 加速度模长 < 9.0，倾斜角 < 30° 或 > 150° |
| `walking` | 行走 | Y轴加速度 > 0.5，角速度 0.3~1.0 |
| `feeding` | 采食 | 角速度 Y > 0.4，Y轴偏低 |
| `running` | 奔跑 | 陀螺仪模值 > 2.0（高速运动） |

**双轨识别**：
1. ML 优先：RandomForest 模型推理，置信度 < 0.6 时自动回退规则引擎
2. 规则兜底：加速度 + 角速度阈值判断（4 类，无 running）

**abnormal 标签**：表示设备异常（加速度超出 ±20g、疑似设备脱落），不是动物行为。

### 3. 异常检测算法

**步数异常（StepAlert — IQR 算法）**：
1. 收集历史步数，计算 Q1（下四分位数）、Q3（上四分位数）
2. 正常范围：`[Q1 - 1.5×IQR, Q3 + 1.5×IQR]`
3. 超出范围触发告警，分级：info / warning / critical

**异常等级**：
- `info`：轻微波动
- `warning`：偏离正常，建议关注
- `critical`：严重异常，需要立即处理

---

## API 接口

### Python ML 服务（端口 5000）

| 接口路径 | 方法 | 认证 | 说明 |
|----------|------|------|------|
| `/health` | GET | 无 | 健康检查（含 LLM 状态）；service 名 `yunmu-python-ml` |
| `/api/device/data` | POST | Token | 接收设备传感器数据（Java 调用，X-API-Token 鉴权） |
| `/api/predict/behavior` | POST | Token | 多源传感行为识别（加速度 + 可选音频，Java 调用） |
| `/api/count/steps` | POST | Token | 独立步数统计（Java 调用） |
| `/api/device/{device_id}` | GET | Token | 获取设备实时状态 |
| `/api/devices` | GET | Token | 获取所有设备状态 |
| `/api/steps/daily` | GET | Token | 获取每日步数统计 |
| `/api/steps/history/{device_id}` | GET | Token | 获取设备步数历史 |
| `/api/alert/summary` | GET | Token | 获取预警摘要 |
| `/api/llm/status` | GET | 无 | LLM 网关状态 |
| `/api/llm/explain-anomaly` | POST | Token | LLM 异常解释（三段式） |
| `/api/llm/behavior-report` | POST | Token | LLM 生成养殖日报 |
| `/api/llm/behavior-summary` | POST | Token | LLM 行为序列语义化 |
| `/api/llm/chat` | POST | Token | AI 助手多轮对话（前端 AI 助手页签，未配 Key 返回 503） |
| `/api/ml/generate-data` | POST | Token | 生成合成训练数据 |
| `/api/ml/train` | POST | Token | 训练机器学习模型 |
| `/api/ml/model/status` | GET | Token | 模型状态 |

### Java 后端（端口 8080）

| 接口路径 | 说明 |
|----------|------|
| `/api/device/data` POST | 接收设备数据（MQTT → Python） |
| `/api/ml/callback` POST | Python 回调入口（MlCallbackController，X-ML-Callback-Token 鉴权 + SSRF 白名单校验） |
| `/api/animals` GET | 动物档案列表（仅静态档案：编号/类型/品种/月龄/体重/绑定项圈；不含实时指标，也不含名字） |
| `/api/animals/{animalId}` GET | 单个动物档案 |
| `/api/realtime/summary` GET | 全量实时摘要：每只动物的体温/心率/步数/位置 + **最新行为/姿态/健康分**（一次取全，避免逐只 N+1 查询） |
| `/api/realtime/latest/{animalId}` GET | 单只动物最新传感器数据（优先 Redis 缓存，兜底数据库） |
| `/api/simulator/start` POST | 启动设备数据模拟器（演示工具，可传 `codes` / `intervalMs` / `durationSeconds` / `anomalyRate`） |
| `/api/simulator/backfill` POST | 历史回放：按历史时间戳回填过去一段时间的数据（可传 `backfillHours` / `backfillIntervalSeconds`） |
| `/api/simulator/stop` POST | 停止设备数据模拟器（已入库数据保留），并中止进行中的回放 |
| `/api/simulator/status` GET | 查询模拟器运行状态与回放进度（前端刷新页面后同步按钮状态） |
| `/api/behavior/analyze` POST | 行为分析 |
| `/api/animal/*` | 动物管理 CRUD |
| `/api/health/*` | 健康评估 |
| `/api/location/*` | 定位轨迹 |
| `/api/posture/*` | 姿态记录 |
| `/api/step/*` | 步数统计 |
| `/ws` | WebSocket 实时推送（可选 Token 鉴权，见 `yunmu.websocket.auth-token`） |

---

## 配置说明

### Python 服务（环境变量）

```bash
# .env 文件（复制自 .env.example）
DEEPSEEK_API_KEY=your_api_key
DEEPSEEK_MODEL=deepseek-chat
LLM_PROVIDER=deepseek
YUNMU_API_TOKEN=your_secure_token
ALLOWED_CALLBACK_HOSTS=localhost,127.0.0.1,your-java-server
```

### Java 后端（application.yml）

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=yunmu_db;encrypt=false;trustServerCertificate=true
    username: sa
    # 密码通过环境变量覆盖：DB_PASSWORD
    # password: ${DB_PASSWORD:changeme}
  redis:
    host: localhost
    port: 6379

yunmu:
  python-service:
    url: http://localhost:5000
    timeout: 30000
    auth-token: ${YUNMU_API_TOKEN:yunmu-ml-secret-token-2024}   # Java 调用 Python 时以 X-API-Token 携带
  ml:
    callback-url: http://127.0.0.1:8080/api/ml/callback
    callback:
      token: ${YUNMU_ML_CALLBACK_TOKEN:yunmu-callback-token-2024}  # 校验 Python 回调令牌
  websocket:
    auth-token: ${YUNMU_WS_TOKEN:}   # 为空=关闭 WS 鉴权（本地演示默认）

mqtt:
  broker:
    url: tcp://120.27.235.176:1883
  # 缺陷 E 修复：不再默认 root/root 弱口令，凭据从环境变量读取
  # username: ${MQTT_USERNAME:}
  # password: ${MQTT_PASSWORD:}
```

---

## 部署说明

### 数据库初始化

数据库 `yunmu_db` **不需要手工建表**：后端启动时 JPA 会按 8 个实体类自动创建并补全表结构
（`spring.jpa.hibernate.ddl-auto=update`）。只需先建一个空库：

```sql
-- SQL Server
CREATE DATABASE yunmu_db;
```

首次部署（或清库重建）后 **必须**执行动物档案种子脚本，否则链路会丢弃全部上报数据：

```powershell
cd yunmu-backend
.\init_seed_data.ps1
```

原因是 `MqttMessageHandler.extractDeviceAndAnimal` 会用项圈号反查 `animals.device_code` 得到
`animal_id`；查不到绑定关系时，该包会被**静默丢弃**（不调 Python、不写业务表、不推送前端）。
脚本写入 3 条档案（`cow_001`↔7249、`cow_002`↔7250、`sheep_001`↔7251）并打印绑定校验结果。

> 本系统不以名字标识动物，个体只用 `animal_id` 标识，档案中不包含名称字段。

清库重建的完整顺序：**建空库 → 启动后端（自动建表）→ 跑种子脚本 → 启动 Python → 触发实时模拟器或历史回放**。

链路修复后需要**保留档案、只重放数据**时，用清库脚本清空业务表（`animals` 不动）：

```powershell
sqlcmd -S localhost,1433 -U sa -P <密码> -d yunmu_db -i .\clear_simulated_data.sql
```

该脚本清空 `sensor_data` / `posture_results` / `behavior_results` / `location_tracks` /
`step_counts` / `health_status` / `alert_records` 共 7 张模拟产出表，并打印清理后行数供核对。
使用 `DELETE` 而非 `TRUNCATE`，以免表间存在外键时直接失败。

### Python ML 服务

```bash
cd yunmu-python

# 安装依赖（推荐使用清华镜像）
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY 和 YUNMU_API_TOKEN

# 训练模型（如无已训练模型）
python train_model.py

# 启动服务
python app.py
# 服务地址：http://localhost:5000
```

### Java 后端

```bash
cd yunmu-backend

# 确保 SQL Server 和 Redis 已启动
# 修改 application.yml 中的数据库密码

# 运行（开发模式）
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/yunmu-backend-1.0.0.jar
# 服务地址：http://localhost:8080
```

---

## 统一姿态标签体系（ML 5 类标准）

| 标签 | 说明 | 规则引擎 | Java 阈值 |
|------|------|----------|-----------|
| `standing` | 站立 | ✅ | ✅ |
| `lying` | 卧地（静卧/休息/反刍） | ✅ | ✅ |
| `walking` | 行走 | ✅ | ✅ |
| `feeding` | 采食 | ✅ | ✅ |
| `running` | 奔跑 | ✅（新增） | ✅ |
| `abnormal` | 设备异常 | ❌（不属于动物行为） | ✅ |

> `resting`（Java 历史行为分析中的旧标签）现已并入 `lying`，保证统计口径统一。

---

## 个体标识与实时摘要字段

**个体只用编号标识，不对动物命名。** `animals` 表不含名字列，接口与界面一律用 `animal_id`（即项圈编号，如 `7249`）区分个体——避免"编号 + 昵称"双套标识带来的口径不一致。

`GET /api/realtime/summary` 为每只动物返回：

| 字段 | 来源 | 说明 |
|------|------|------|
| `id` / `type` | `animals` 档案 | 个体编号与物种（`cow` / `cattle` / `sheep`） |
| `temperature` / `heartRate` / `steps` | `sensor_data` 最新一条 | 实时指标 |
| `behavior` / `behaviorType` | `behavior_results` 最新一条 | 中文标签 / 英文枚举；记录缺失时用最新姿态兜底 |
| `posture` / `postureType` | `posture_results` 最新一条 | 同上 |
| `healthScore` | 由体温 + 心率按阈值规则折算 | **可解释的规则分，非模型输出，也不构成兽医诊断结论** |
| `status` | 体温或心率越界 | `normal` / `alert` |

> 实时指标一律来自真实链路（MQTT → Python 推理 → 回调入库），前端不做任何伪造或随机兜底。

---

## 数据模态与训练数据说明（Demo 声明）

**关于数据模态**：系统当前真正落地的模态为**三轴加速度 + 三轴陀螺仪（IMU 运动模态）**，并在行为分析链路中支持**可选音频通道**（`/api/predict/behavior` 的 `sound_data`，由轻量启发式算法估计咀嚼/反刍节律，**不参与 ML 模型的训练与推理**）。
尚未引入摄像头视频（无 torch / OpenCV 推理链路），文档中已如实标注，不宣称已具备视觉模态。

**关于训练数据**：`ml_models/data_collector.py` 提供的数据生成器为**合成数据**，用于打通训练 / 推理链路与联调演示。
运行时样本一律以未标注（`posture=None`）落盘，**不再将模型预测结果回灌为标签**（修复了原“合成数据 → 伪标签 → 再训练”闭环）。
生产使用需接入真实人工 / 设备标注数据后重新训练。

**关于 LLM**：`llm_gateway.py` 采用旁路调用，异常解释 / 报告生成 / 行为语义化失败时自动降级为规则文案，不影响主链路。

---

## 常见问题

### 1. Python 服务启动失败
```bash
# 检查 5000 端口
netstat -ano | findstr 5000
# 重新安装依赖
pip install -r requirements.txt --upgrade -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 2. LLM 接口返回 503
确认 `.env` 中 `DEEPSEEK_API_KEY` 已正确配置，`DEEPSEEK_BASE_URL` 可访问。

### 3. Java 无法回调 Python
检查 `ALLOWED_CALLBACK_HOSTS` 是否包含 Java 服务器地址；检查防火墙是否开放 5000 端口。

### 4. 姿态识别与预期不符
当前使用规则引擎时，`running` 标签由陀螺仪模值判断；若需更高精度，训练 ML 模型后自动切换。

### 5. 步数统计偏差
设备字段 `bushu` 是**通电后累计步数**，Java/Python 链路内部按**本次增量**统计
（`StepCountRepository` 用 `SUM(step_count)` 汇总当日步数），因此 `app.py` 会按设备保存上一次
累计值并求差；设备重启导致累计值归零时按新一轮增量处理。若数值仍偏差，确认 `accel_y_history` /
`timestamps` 是否正常上报，规则引擎阈值可在 `models/step_counter.py` 中调整。

### 6. 启动模拟器后前端仍无数据
按顺序排查：① `animals.device_code` 是否已绑定对应项圈编号（未绑定则链路直接丢弃）；
② Python 服务（5000）是否可用；③ 若 Python 调用超时（默认 1500ms）会降级到 Java 本地规则，
数据仍应入库；④ 数据库与 Redis 是否可连接。

### 7. 历史回放后趋势页有数据、但健康相关页面仍为空
回放的数据已入库，说明链路是通的。逐项排查：

- **`health_status` 为空**：该表由 Java 侧 `HealthMonitoringService.monitorRealTimeHealth` 写入，
  与姿态识别不同，它**不依赖 Python**。请确认 `DataCollectionServiceImpl` 的两条处理路径
  （`processSensorData` 与 `processSensorDataWithMlResult`）都调用了 `triggerAsyncHealthMonitor`。
  历史上曾出现"只有 Java 降级路径会触发健康监控"，导致 Python 可用时该表永远为空。
- **进度停在某个比例却报「回放完成」**：任务提交时序问题（先提交再置运行标志，任务首轮即退出）。
  已修复为**先置 `backfilling` 再提交任务**。
- **数据条数略少于预期**：回放是异步落库，刚结束时统计会偏少，等 20~30 秒再统计；
  另有极少数包会被 `filterAbnormalData` 丢弃（心率 <40 或 >120、体温 <35 或 >42、加速度幅值越界）。
- **健康状态只在整点更新**：`ScheduledTasks.hourlyHealthCheck` 每小时整点巡检一次；
  也可直接调 `GET /api/health/assessment/{animalId}` 立即触发一次评估。

### 8. 定位点跑到国外 / 公海里（例如几内亚湾）

**症状**：地图上标记落在大西洋、非洲西海岸一带，坐标约在 0°N / 0°E 附近。

**根因**：`JD` / `WD` 的**格式约定在收发两侧不一致**。本项目协议沿用 NMEA 习惯，
坐标是 **度分格式 DDMM.MMMM**（`GpsUtils.degreeMinuteToDecimal`、Python 侧 `_parse_gga`
的 `lat_raw[:2] + lat_raw[2:]/60` 都是这个口径）。若发送端直接发**十进制度数**，
接收端会把 `91.100000` 当成「91 度 10.000 分」，再算成 `91.1/60 = 1.5183°` —— 于是落到几内亚湾。

**判定特征**（很好认）：存库纬度 ≈ 真实纬度 ÷ 60，经度 ≈ 真实经度 ÷ 60。
例如 29.65°N / 91.10°E 会被存成 0.494°N / 1.518°E。

**修复**：发送端必须用 `GpsUtils.decimalToDegreeMinute()` 输出（模拟器已修正）。
> 注意 `decimalToDegreeMinute` 会补前导零输出定宽 DDMM.MMMM（如 9.5° → `0930.000`），
> 这是为了兼容按字符串切片解析的消费者（Python `_parse_gga` 用 `lat_raw[:2]`）。

**清库重放**：修复后旧数据仍是错的，需清空后重新回放：

```powershell
# 1. 清空模拟数据（保留 animals 档案与项圈绑定）
sqlcmd -S localhost,1433 -U sa -P <密码> -d yunmu_db -i yunmu-backend/clear_simulated_data.sql
# 2. 重启后端后重新回放
#    POST /api/simulator/backfill  {"codes":["7249","7250","7251"],"backfillHours":24,"backfillIntervalSeconds":300}
```

> 回放启动前可先看响应里的 `devices[].baseLatitude / baseLongitude` 确认锚点正确，
> 避免跑完几分钟才发现整批数据落错地方。

### 9. 告警页刷出大量 CRITICAL（健康个体被判异常）

**症状**：`alert_records` 数量远超异常注入比例，例如「心率异常警报: 85 bpm」被判为 CRITICAL。

**根因**：模拟器生成「正常」生理值时的区间与健康模块声明的**物种正常区间不一致**。
`checkHeartRateAbnormal` / `checkTemperatureAbnormal` 采用**严格区间**判定，
`determineAlertLevel` 又把心率 / 体温异常**一律定为 CRITICAL**，因此任何轻微越界都会变成红色告警。

典型踩坑：模拟器给「正常」心率加了活动加成（走路 +5、奔跑 +12），
牛的区间上限是 80，而奔跑时心率可达 92 → 健康的奔跑个体被刷成 CRITICAL。

**修复**：模拟器的正常生理区间必须**严格落在** `HealthMonitoringServiceImpl.ANIMAL_NORMAL_RANGES` 内，
并按物种分别生成（牛 / 羊区间不同，不能共用一套值）。

**自检**：

```sql
-- 非异常时段不应越界；若此区间明显超出 60~80 / 38.0~39.5，说明区间没对齐
SELECT animal_id, MIN(heart_rate), MAX(heart_rate), MIN(temperature), MAX(temperature)
FROM sensor_data GROUP BY animal_id;

-- 与告警数对比：告警应只来自异常注入，而非正常活动
SELECT animal_id, alert_type, COUNT(*) FROM alert_records GROUP BY animal_id, alert_type;
```

### 10. 健康监控静默失效（`health_status` 不再更新、告警凭空消失）

**症状**：链路一切正常（`sensor_data`、`posture_results` 都在涨），但 `health_status` 停在旧值、
`alert_records` 数量远少于实际异常包数；日志里有 `实时健康监控失败` 的 `ERROR`。

**根因**：`HealthStatusRepository.findLatestByAnimalId` 曾声明为返回 `Optional<HealthStatus>` 的
**单条** JPQL（没有 LIMIT）。Hibernate 对单条返回走 `getSingleResult()`，
一旦同一动物存在 **2 行及以上**记录就抛：

```
org.hibernate.NonUniqueResultException: Query did not return a unique result: 2 results were returned
```

多于一行的成因是**并发**：`monitorRealTimeHealth` 由异步线程池执行，同一动物的两条数据
可能同时查不到记录、各自新建一行。此后该动物的**每一次**健康监控调用都会在查库环节抛异常，
而调用点有 try/catch 兜底，于是表现为「静默失效」——只有日志里能看到 `ERROR`。

**修复**：改为 `List + Pageable`（`PageRequest.of(0, 1)`）再取首条，
与 `LocationTrackRepository.findLatestByAnimalId` 保持一致。**不要**写成返回 `Optional` 的无 LIMIT 查询。

> 排查思路：凡是 `@Query` 返回单条却不带 `TOP` / `LIMIT` / `Pageable` 的方法都有这个隐患。
> 用 `SELECT TOP 1`（原生 SQL，如 `StepCountRepository`）或 `List + Pageable` 才是安全的。

### 11. 健康页显示正常、告警页却显示 CRITICAL

**根因**：`checkRealTimeAlerts`（实时路径）原先只写 `alert_records`，
**从不回写** `health_status` 的 `alert_type` / `alert_message` / `alert_level` / `overall_status`。
而这些字段只在 `checkAndGenerateAlerts`（评估接口路径）里才会被设置，
两条路径行为不一致，导致两张表自相矛盾。已修复为两条路径都同步更新。

**自检**：`health_status` 中 `overall_status = 'NORMAL'` 的行，体温/心率应落在正常区间内：

```sql
SELECT animal_id, overall_status, body_temperature, heart_rate, alert_level
FROM health_status
WHERE overall_status = 'NORMAL' AND (body_temperature > 39.5 OR heart_rate > 80);
-- 正常应返回 0 行
```

---

## 主要更新（2024）

1. **文档对齐**：修正为 SQL Server、Spring Boot 3.2.11、`train_model.py` / `app.py` 真实入口
2. **统一标签体系**：以 ML 5 类为标准（standing/lying/walking/feeding/running），落库统一英文规范标签，收敛三套标签口径
3. **LLM 网关**：新增 `llm_gateway.py`，支持 DeepSeek / OpenAI 兼容接口，用于异常解释、报告生成、行为语义化
4. **异常处理改进**：分析失败返回 null 而非假数据，modelType / dataModality 如实记录；异步分析改用有界线程池（不再占用 ForkJoinPool 公共池）
5. **配置外置化**：密码 / Token 通过环境变量覆盖，`.env.example` 提供模板；统一配置键为 `yunmu.python-service.url`
6. **安全加固**：Python `/api/device/data` 等内部接口启用 Token 鉴权；`/api/ml/callback` 增加回调令牌校验并移除通配 CORS；WebSocket 支持可选握手鉴权
7. **接口补全**：新增 `/api/predict/behavior`（多源传感行为识别）与 `/api/count/steps`（独立计步），修复 Java 调用不存在的路由问题
8. **修复缺陷**：`PostureResultRepository` 原生 SQL 表名 / 语法错误（SQL Server `TOP 1`）；GPS 硬编码 NMEA 校验和；前端步数页“活跃程度分布”饼图改用真实数据；定时任务由空实现改为真实巡检 / 统计 / 预警派发
9. **依赖收敛**：移除未使用的 Quartz / H2 / Spring Cloud BOM 与并存的 fastjson 1.x；`springdoc-openapi` 升级到 2.6.0（适配 Spring Boot 3 / jakarta）
10. **演示数据后置到后端**：移除前端 `utils/mock.js` 与 `AnalysisTab.generateDemoData()` 两处本地伪造数据（含接口失败时静默回落假数据的逻辑），改为后端 `DeviceSimulatorService` 按设备协议生成报文并注入 `MqttMessageHandler`，演示数据从此走完整真实链路；前端按钮改为「启动/停止数据模拟器」，状态从后端读取（刷新不丢）
11. **历史回放与建库工具链**：新增 `POST /api/simulator/backfill`，按历史时间戳回填过去一段时间的数据（链路代码零改动）；新增 `init_seed_data.ps1` 初始化动物档案与项圈绑定；`insert_mock_data.ps1` 对齐 JPA 表结构并跳过无实体表
12. **修复健康监控遗漏**：`triggerAsyncAnalysis`（含健康监控）原先只在 Java 降级路径被调用，Python 服务可用时完全不触发，导致 `health_status` 永远为空、健康相关页面无数据；已抽出独立的 `triggerAsyncHealthMonitor` 并让两条处理路径都调用
13. **修复步数口径**：设备 `bushu` 为累计步数，`app.py` 原实现将其当作单次增量累加，导致当日步数被放大约 N 倍；改为按设备保存累计基线并求差
14. **修复定位坐标格式不一致**：模拟器原直接发送十进制度数，接收端按度分（DDMM.MMMM）解析后再除以 60，整批坐标落到几内亚湾（如 29.65°N → 0.494°N）。改为用 `GpsUtils.decimalToDegreeMinute()` 输出协议要求的度分格式；同时修正该函数未补前导零、未固定 Locale 的问题。详见常见问题 8
15. **修复健康阈值与模拟器不一致导致的告警洪水**：模拟器给正常心率加活动加成（走路 +5 / 奔跑 +12），超过牛的区间上限 80（可达 92），864 个回放点刷出 400+ 条 CRITICAL 假告警。改为按物种分别生成正常生理值并严格落在 `ANIMAL_NORMAL_RANGES` 内，异常值下限抬到高于牛、羊两个上限。详见常见问题 9
16. **新增清库脚本**：`yunmu-backend/clear_simulated_data.sql`，用于链路修复后清空模拟数据重放（保留 `animals` 档案与项圈绑定）
17. **修复健康监控静默失效**：`HealthStatusRepository.findLatestByAnimalId` 为无 LIMIT 的单条查询却声明返回 `Optional`，同一动物存在 2 行以上时抛 `NonUniqueResultException`（实测 857 次），调用方 try/catch 兜底后表现为「`health_status` 停止更新、告警凭空消失」。改为 `List + Pageable` 取首条。详见常见问题 10
18. **修复健康状态与告警不一致**：`checkRealTimeAlerts` 原先只写 `alert_records`，不回写 `health_status` 的告警字段，导致告警页 CRITICAL、健康页 NORMAL 自相矛盾。详见常见问题 11
