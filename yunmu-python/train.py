#!/usr/bin/env python3
# train.py - 模型训练脚本
import numpy as np
import logging
from pathlib import Path
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
import joblib

from features.feature_extractor import FeatureExtractor
from models.behavior_model import BehaviorClassifier

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def generate_training_data(n_samples=500):
    """生成模拟训练数据"""
    np.random.seed(42)
    
    behavior_labels = {
        'resting': 0,
        'ruminating': 1,
        'feeding': 2,
        'walking': 3,
        'standing': 4
    }
    
    X = []
    y = []
    
    feature_extractor = FeatureExtractor()
    
    for behavior, label in behavior_labels.items():
        n_per_class = n_samples // len(behavior_labels)
        
        for _ in range(n_per_class):
            sample = generate_sample_for_behavior(behavior)
            features = feature_extractor.extract_features(sample)
            
            if not np.all(features == 0):
                X.append(features)
                y.append(label)
    
    return np.array(X), np.array(y)


def generate_sample_for_behavior(behavior: str) -> dict:
    """为特定行为生成模拟传感器数据"""
    np.random.seed(np.random.randint(0, 10000))

    # 加速度数据时间向量（100个点）
    t_accel = np.linspace(0, 2, 100)
    # 声音数据时间向量（1000个点）
    t_sound = np.linspace(0, 2, 1000)

    if behavior == 'resting':
        accel_data = np.random.normal(0, 0.02, 100).tolist()
        sound_data = np.random.normal(0, 0.05, 1000).tolist()
    elif behavior == 'ruminating':
        accel_data = (0.3 * np.sin(2 * np.pi * 1.5 * t_accel) + np.random.normal(0, 0.05, 100)).tolist()
        sound_data = (0.5 * np.sin(2 * np.pi * 2 * t_sound) + np.random.normal(0, 0.1, 1000)).tolist()
    elif behavior == 'feeding':
        accel_data = (0.5 * np.sin(2 * np.pi * 3 * t_accel) + np.random.normal(0, 0.1, 100)).tolist()
        sound_data = (0.7 * np.sin(2 * np.pi * 4 * t_sound) + np.random.normal(0, 0.15, 1000)).tolist()
    elif behavior == 'walking':
        accel_data = (0.8 * np.sin(2 * np.pi * 2 * t_accel) + np.random.normal(0, 0.15, 100)).tolist()
        sound_data = np.random.normal(0, 0.1, 1000).tolist()
    else:  # standing
        accel_data = (0.1 * np.sin(2 * np.pi * 0.5 * t_accel) + np.random.normal(0, 0.03, 100)).tolist()
        sound_data = np.random.normal(0, 0.05, 1000).tolist()

    return {
        'accel_data': accel_data,
        'sound_data': sound_data,
        'accel_sampling_rate': 50,
        'sound_frequency': 1000
    }


def train_models():
    """训练所有模型"""
    logger.info("开始生成训练数据...")
    X, y = generate_training_data(n_samples=500)

    logger.info(f"训练数据形状：X={X.shape}, y={y.shape}")
    logger.info(f"类别分布：{np.bincount(y)}")

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    logger.info(f"训练集：{X_train.shape}, 测试集：{X_test.shape}")

    classifier = BehaviorClassifier()
    logger.info("开始训练模型...")

    for model_name, model in classifier.models.items():
        if model_name == 'voting':
            continue
        logger.info(f"训练 {model_name}...")
        scaler = classifier.scalers[model_name]
        X_scaled = scaler.fit_transform(X_train)
        model.fit(X_scaled, y_train)
        logger.info(f"{model_name} 训练完成")

    # 添加这部分代码来训练 voting classifier
    logger.info("训练 voting classifier...")
    voting_scaler = classifier.scalers['voting']
    X_scaled_voting = voting_scaler.fit_transform(X_train)
    classifier.models['voting'].fit(X_scaled_voting, y_train)
    logger.info("voting classifier 训练完成")

    classifier.save_models('models/trained/behavior_model.pkl')
    logger.info("模型已保存")

    y_pred = classifier.models['voting'].predict(
        classifier.scalers['voting'].transform(X_test)
    )

    logger.info("\n分类报告:")
    target_names = ['resting', 'ruminating', 'feeding', 'walking', 'standing']
    print(classification_report(y_test, y_pred, target_names=target_names))

    return classifier


if __name__ == '__main__':
    Path('models/trained').mkdir(parents=True, exist_ok=True)
    Path('logs').mkdir(parents=True, exist_ok=True)
    train_models()
    logger.info("训练完成!")
