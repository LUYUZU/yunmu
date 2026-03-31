# ML 模块使用指南

## 概述
已为你的云牧智感系统添加机器学习能力，支持：
- **ML姿态识别**：RandomForest / GradientBoosting / SVM / MLP
- **ML异常检测**：Isolation Forest
- **自动数据收集**：运行时自动收集传感器数据

## 快速开始

### 1. 安装依赖
```bash
cd yunmu-python
pip install -r requirements.txt
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
| `/api/ml/predict` | POST | 测试ML预测 |
| `/health` | GET | 查看ML状态 |

## 示例

### 生成训练数据
```bash
curl -X POST http://localhost:5000/api/ml/generate-data \
  -H "Content-Type: application/json" \
  -d '{"samples_per_class": 500}'
```

### 训练模型
```bash
curl -X POST http://localhost:5000/api/ml/train \
  -H "Content-Type: application/json" \
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

## 工作原理

```
设备数据 → 传感器数据
                ↓
         ┌───────┴───────┐
         ↓               ↓
    ML模型预测      规则引擎备用
         ↓               ↓
      姿态识别 + 置信度
                ↓
         自动收集训练数据
```

## 模型文件位置
```
yunmu-python/ml_models/saved_models/
├── posture_random_forest_*.pkl
├── posture_random_forest_*_scaler.pkl
└── posture_random_forest_*_info.json
```