# ML 模块使用指南

## 概述
已为云牧智感系统添加机器学习能力，支持：
- **ML 姿态识别**：RandomForest / SVM / Logistic Regression（5 类姿态）
- **ML 异常检测**：IsolationForest（步数异常）
- **LLM 智能层**：DeepSeek / OpenAI 兼容接口（异常解释、报告生成、行为语义化）
- **自动数据收集**：运行时自动收集传感器数据

## 快速开始

### 1. 安装依赖
```bash
cd yunmu-python
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 2. 一键训练模型
```bash
python train_model.py
```

可选参数：
```bash
python train_model.py --samples 1000 --model random_forest
```

### 3. 启动服务
```bash
python app.py
```

## API 接口

### 训练相关
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ml/generate-data` | POST | 生成合成训练数据 |
| `/api/ml/train` | POST | 训练模型 |
| `/api/ml/model/status` | GET | 查看模型状态 |

### 预测测试
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ml/predict` | POST | 测试 ML 预测（无需认证） |

### LLM 网关
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/llm/status` | GET | LLM 网关状态 |
| `/api/llm/explain-anomaly` | POST | LLM 异常解释（三段式） |
| `/api/llm/behavior-report` | POST | LLM 生成养殖日报 |
| `/api/llm/behavior-summary` | POST | LLM 行为序列语义化 |

### 健康检查
| 接口 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 查看 ML + LLM 状态（无需认证） |

## 示例

### 生成训练数据
```bash
curl -X POST http://localhost:5000/api/ml/generate-data \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer yunmu-ml-secret-token-2024" \
  -d '{"samples_per_class": 500}'
```

### 训练模型
```bash
curl -X POST http://localhost:5000/api/ml/train \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer yunmu-ml-secret-token-2024" \
  -d '{"model_type": "random_forest"}'
```

### 测试预测
```bash
curl -X POST http://localhost:5000/api/ml/predict \
  -H "Content-Type: application/json" \
  -d '{
    "accel_x": 0.5,
    "accel_y": 0.8,
    "accel_z": 9.5,
    "gyro_x": 0.5,
    "gyro_y": 0.1,
    "gyro_z": 0.1
  }'
```

### LLM 异常解释
```bash
curl -X POST http://localhost:5000/api/llm/explain-anomaly \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer yunmu-ml-secret-token-2024" \
  -d '{
    "animal_id": "cow_001",
    "anomaly_type": "step_critical",
    "sensor_data": {
      "daily_steps": 500,
      "normal_range": [3000, 8000],
      "temperature": 40.2,
      "heart_rate": 105
    }
  }'
```

## 工作原理

```
设备数据 → 传感器数据
                ↓
         ┌───────┴───────┐
         ↓               ↓
    ML模型预测      规则引擎备用
    (置信度 < 0.6 时回退)
         ↓
      姿态识别 + 置信度
         │
         ├──→ 异常检测（IQR / IsolationForest）
         │
         └──→ LLM 旁路（异常解释 / 报告生成）
                │
         自动收集训练数据
```

## 统一姿态标签（ML 5 类标准）

| 标签 | 说明 | 规则引擎 | ML 模型 |
|------|------|----------|---------|
| `standing` | 站立 | ✅ | ✅ |
| `lying` | 卧地 | ✅ | ✅ |
| `walking` | 行走 | ✅ | ✅ |
| `feeding` | 采食 | ✅ | ✅ |
| `running` | 奔跑 | ✅（新增） | ✅ |
| `abnormal` | 设备异常 | ❌ | ❌（规则引擎专属） |

## LLM 网关配置

LLM 网关支持 DeepSeek、通义、OpenAI 兼容接口。配置环境变量：

```bash
# .env
DEEPSEEK_API_KEY=your_deepseek_api_key
DEEPSEEK_MODEL=deepseek-chat
LLM_PROVIDER=deepseek
```

切换到 OpenAI：
```bash
OPENAI_API_KEY=your_openai_api_key
OPENAI_MODEL=gpt-4o-mini
LLM_PROVIDER=openai
```

LLM 调用**全部旁路异步**，不影响实时主链路。

## 模型文件位置
```
yunmu-python/ml_models/saved_models/
├── posture_random_forest_*.pkl
├── posture_random_forest_*_scaler.pkl
└── posture_random_forest_*_info.json
```
