# ml_models/posture_predictor.py - 机器学习推理模块
"""
使用训练好的模型进行实时姿态预测
支持批量预测、置信度阈值、模型热加载

修复：使用共享 feature_extractor，消除重复代码
"""
import os
import json
import joblib
import numpy as np
from pathlib import Path
from typing import Dict, List

from utils.feature_extractor import extract_features, FEATURE_NAMES


class MLPostureClassifier:
    """基于机器学习的姿态分类器"""

    POSTURE_LABELS = ['standing', 'lying', 'walking', 'feeding', 'running']

    def __init__(self, model_path: str = None):
        self.model_dir = self._get_model_dir()
        self.model = None
        self.scaler = None
        self.model_info = None
        self.model_loaded = False
        self.feature_names = FEATURE_NAMES

        # 置信度阈值（低于此值时使用规则引擎兜底）
        self.confidence_threshold = 0.6

        if model_path:
            self.load_model(model_path)
        else:
            self._load_latest_model()

    def _get_model_dir(self) -> Path:
        base_dir = Path(__file__).parent.parent
        return base_dir / "ml_models" / "saved_models"

    def _load_latest_model(self):
        """自动加载最新的模型"""
        if not self.model_dir.exists():
            print("模型目录不存在，跳过加载")
            return

        model_files = [f for f in self.model_dir.glob("posture_*.pkl")
                       if '_scaler' not in f.name]

        if not model_files:
            print("没有找到训练好的模型")
            return

        latest = max(model_files, key=lambda x: x.stat().st_mtime)
        self.load_model(latest.stem)

    def load_model(self, model_name: str):
        """加载指定模型"""
        model_path = self.model_dir / f"{model_name}.pkl"
        scaler_path = self.model_dir / f"{model_name}_scaler.pkl"
        info_path = self.model_dir / f"{model_name}_info.json"

        if not model_path.exists():
            raise FileNotFoundError(f"模型文件不存在: {model_path}")

        self.model = joblib.load(model_path)
        self.scaler = joblib.load(scaler_path)

        if info_path.exists():
            with open(info_path, 'r', encoding='utf-8') as f:
                self.model_info = json.load(f)

        self.model_loaded = True
        print(f"ML模型加载成功: {model_name}")

    def predict(self,
                accel_x: float, accel_y: float, accel_z: float,
                gyro_x: float = 0.0, gyro_y: float = 0.0, gyro_z: float = 0.0
                ) -> Dict:
        """
        预测姿态

        Args:
            accel_x, accel_y, accel_z: 加速度数据 (m/s²)
            gyro_x, gyro_y, gyro_z: 角速度数据 (rad/s)

        Returns:
            预测结果字典
        """
        # 模型未加载，直接用规则引擎
        if not self.model_loaded:
            return self._fallback_predict(
                accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z)

        try:
            # 使用共享特征提取器
            features = extract_features(accel_x, accel_y, accel_z,
                                         gyro_x, gyro_y, gyro_z)
            features_scaled = self.scaler.transform(features.reshape(1, -1))

            # 预测
            prediction = self.model.predict(features_scaled)[0]
            probabilities = self.model.predict_proba(features_scaled)[0]

            posture = self.POSTURE_LABELS[prediction]
            confidence = float(probabilities[prediction])

            # 置信度过低 → 规则引擎兜底
            if confidence < self.confidence_threshold:
                fallback = self._fallback_predict(
                    accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z)
                return {
                    'posture_type': posture,
                    'confidence': confidence,
                    'ml_confidence': confidence,
                    'fallback_used': True,
                    'fallback_posture': fallback['posture_type'],
                    'fallback_confidence': fallback['confidence'],
                    'probabilities': {
                        p: float(prob)
                        for p, prob in zip(self.POSTURE_LABELS, probabilities)
                    },
                    'features': features.tolist(),
                    'model_type': (
                        self.model_info.get('model_type', 'unknown')
                        if self.model_info else 'unknown')
                }

            return {
                'posture_type': posture,
                'confidence': confidence,
                'ml_confidence': confidence,
                'fallback_used': False,
                'probabilities': {
                    p: float(prob)
                    for p, prob in zip(self.POSTURE_LABELS, probabilities)
                },
                'features': features.tolist(),
                'model_type': (
                    self.model_info.get('model_type', 'unknown')
                    if self.model_info else 'unknown')
            }

        except Exception as e:
            print(f"ML预测失败: {e}")
            return self._fallback_predict(
                accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z)

    def _fallback_predict(self, accel_x, accel_y, accel_z,
                          gyro_x, gyro_y, gyro_z) -> Dict:
        """备用规则引擎（物理阈值法）"""
        accel_magnitude = np.sqrt(accel_x**2 + accel_y**2 + accel_z**2)

        if accel_magnitude < 9.0:
            posture = 'lying'
            confidence = 0.7
        elif accel_y > 0.5 and abs(gyro_x) > 0.3:
            posture = 'walking'
            confidence = 0.65
        elif abs(gyro_y) > 0.4:
            posture = 'feeding'
            confidence = 0.6
        else:
            posture = 'standing'
            confidence = 0.6

        return {
            'posture_type': posture,
            'confidence': confidence,
            'ml_confidence': 0.0,
            'fallback_used': True,
            'probabilities': {p: 0.0 for p in self.POSTURE_LABELS},
            'features': extract_features(
                accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z).tolist(),
            'model_type': 'rule_based'
        }

    def batch_predict(self, data_list: List[Dict]) -> List[Dict]:
        """批量预测"""
        results = []
        for data in data_list:
            result = self.predict(
                accel_x=data.get('accel_x', 0.0),
                accel_y=data.get('accel_y', 0.0),
                accel_z=data.get('accel_z', 9.8),
                gyro_x=data.get('gyro_x', 0.0),
                gyro_y=data.get('gyro_y', 0.0),
                gyro_z=data.get('gyro_z', 0.0)
            )
            results.append(result)
        return results

    def is_model_loaded(self) -> bool:
        return self.model_loaded

    def get_model_info(self) -> Dict:
        if not self.model_loaded:
            return {'status': 'no_model', 'message': '没有加载模型'}

        return {
            'status': 'loaded',
            'model_type': (
                self.model_info.get('model_type', 'unknown')
                if self.model_info else 'unknown'),
            'confidence_threshold': self.confidence_threshold,
            'trained_at': (
                self.model_info.get('trained_at')
                if self.model_info else None),
            'feature_count': len(self.feature_names)
        }


# -------------------------------------------------------------------------
# MLStepAnalyzer — 步数异常分析（保留，未大改）
# -------------------------------------------------------------------------

class MLStepAnalyzer:
    """基于机器学习的步数异常分析"""

    def __init__(self, model_path: str = None):
        self.model_dir = self._get_model_dir()
        self.model = None
        self.scaler = None
        self.model_loaded = False
        self.legacy_analyzer = None
        self.step_history = []

        if model_path:
            self.load_model(model_path)
        else:
            self._load_latest_model()

    def _get_legacy_analyzer(self):
        if self.legacy_analyzer is None:
            from models.step_alert import StepAlert
            self.legacy_analyzer = StepAlert()
        return self.legacy_analyzer

    def _get_model_dir(self) -> Path:
        base_dir = Path(__file__).parent.parent
        return base_dir / "ml_models" / "saved_models"

    def _load_latest_model(self):
        if not self.model_dir.exists():
            return
        model_files = [f for f in self.model_dir.glob("step_anomaly_*.pkl")
                       if '_scaler' not in f.name]
        if not model_files:
            print("没有找到异常检测模型，使用统计方法")
            return
        latest = max(model_files, key=lambda x: x.stat().st_mtime)
        self.load_model(latest.stem)

    def load_model(self, model_name: str):
        model_path = self.model_dir / f"{model_name}.pkl"
        scaler_path = self.model_dir / f"{model_name}_scaler.pkl"
        if not model_path.exists():
            raise FileNotFoundError(f"模型文件不存在: {model_path}")
        self.model = joblib.load(model_path)
        self.scaler = joblib.load(scaler_path)
        self.model_loaded = True
        print(f"异常检测模型加载成功: {model_name}")

    def extract_step_features(self, step_history: List[int]) -> np.ndarray:
        if len(step_history) < 10:
            return None
        recent = step_history[-10:]
        return np.array([
            np.mean(recent), np.std(recent),
            np.max(recent), np.min(recent),
            recent[-1] - recent[0], np.median(recent)
        ])

    def analyze(self, current_steps: int, step_history: List[int]) -> Dict:
        if not self.model_loaded:
            legacy = self._get_legacy_analyzer()
            legacy.update_baseline(
                step_history[-30:] if len(step_history) >= 30 else step_history)
            return legacy.check_anomaly(current_steps, None, None)

        try:
            features = self.extract_step_features(step_history)
            if features is None:
                return {'is_anomaly': False, 'reason': '数据不足'}

            features_scaled = self.scaler.transform(features.reshape(1, -1))
            is_anomaly = self.model.predict(features_scaled)[0] == -1
            score = self.model.score_samples(features_scaled)[0]

            if score < -0.5:
                severity = 'critical'
            elif score < -0.2:
                severity = 'warning'
            else:
                severity = 'normal'

            return {
                'is_anomaly': bool(is_anomaly),
                'anomaly_score': float(score),
                'severity': severity,
                'model_type': 'ml'
            }

        except Exception as e:
            print(f"ML异常分析失败: {e}")
            legacy = self._get_legacy_analyzer()
            legacy.update_baseline(step_history[-30:])
            return legacy.check_anomaly(current_steps, None, None)

    def add_step(self, steps: int):
        self.step_history.append(steps)
        if len(self.step_history) > 1000:
            self.step_history = self.step_history[-1000:]


# -------------------------------------------------------------------------
# 自测
# -------------------------------------------------------------------------
if __name__ == '__main__':
    print("=" * 50)
    print("机器学习推理测试")
    print("=" * 50)

    classifier = MLPostureClassifier()

    test_cases = [
        (0.1, 0.2, 9.7, 0.0, 0.0, 0.0, 'standing'),
        (0.0, 9.5, 0.5, 0.0, 0.0, 0.0, 'lying'),
        (0.5, 0.8, 9.5, 0.5, 0.1, 0.1, 'walking'),
        (0.3, 0.6, 9.6, 0.1, 0.6, 0.1, 'feeding'),
        (1.0, 1.5, 9.3, 1.2, 0.3, 0.2, 'running'),
    ]

    print("\n测试用例预测结果:")
    print("-" * 60)

    for accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z, expected in test_cases:
        result = classifier.predict(accel_x, accel_y, accel_z,
                                     gyro_x, gyro_y, gyro_z)
        print(f"输入: accel=({accel_x}, {accel_y}, {accel_z}), "
              f"gyro=({gyro_x}, {gyro_y}, {gyro_z})")
        print(f"  预期: {expected}")
        print(f"  预测: {result['posture_type']} "
              f"(置信度: {result['confidence']:.2f})")
        print(f"  模型: {result.get('model_type', 'unknown')}")
        print()

    print("\n模型信息:", classifier.get_model_info())
