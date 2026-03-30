# models/behavior_model.py
import numpy as np
import joblib
from pathlib import Path
from sklearn.svm import SVC
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, VotingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from typing import Dict, List, Tuple, Any
import logging

logger = logging.getLogger(__name__)


class BehaviorClassifier:
    """行为分类器"""

    def __init__(self, model_path: str = None):
        self.models = {}
        self.scalers = {}
        self.thresholds = {
            'rumination': 0.7,
            'feeding': 0.65,
            'resting': 0.6,
            'walking': 0.6
        }
        self.label_map = {
            0: "resting",
            1: "ruminating",
            2: "feeding",
            3: "walking",
            4: "standing"
        }

        # 初始化模型
        self._init_models()

        # 如果提供了模型路径，加载预训练模型
        if model_path:
            self.load_models(model_path)
        else:
            default_path = 'models/trained/behavior_model.pkl'
            if Path(default_path).exists():
                self.load_models(default_path)

    # models/behavior_model.py 中修改 _init_models 方法

    def _init_models(self):
        """初始化各类模型"""

        # 声学模型（SVM）
        self.models['acoustic_svm'] = SVC(
            kernel='rbf',
            C=1.0,
            gamma='scale',
            probability=True,
            class_weight='balanced'
        )

        # 加速度逻辑回归模型 - 使用默认多分类
        self.models['accel_logistic'] = LogisticRegression(
            max_iter=1000,
            class_weight='balanced',
            random_state=42,
            solver='lbfgs'  # 只需要指定 solver
        )

        # 综合随机森林模型
        self.models['ensemble_rf'] = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            min_samples_split=5,
            min_samples_leaf=2,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1
        )

        # 投票分类器（加权融合）
        self.models['voting'] = VotingClassifier(
            estimators=[
                ('svm', self.models['acoustic_svm']),
                ('logistic', self.models['accel_logistic']),
                ('rf', self.models['ensemble_rf'])
            ],
            voting='soft',
            weights=[0.4, 0.3, 0.3]
        )

        # 初始化标准化器
        for model_name in self.models:
            self.scalers[model_name] = StandardScaler()

    def predict(self, features: np.ndarray) -> str:
        """预测行为类型"""
        try:
            # 使用投票分类器进行预测
            voting_model = self.models['voting']
            scaler = self.scalers['voting']

            # 标准化特征
            if hasattr(features, 'reshape'):
                features_2d = features.reshape(1, -1)
            else:
                features_2d = np.array(features).reshape(1, -1)

            features_scaled = scaler.transform(features_2d)

            # 预测
            prediction = voting_model.predict(features_scaled)[0]

            return self._map_behavior_label(prediction)

        except Exception as e:
            logger.error(f"行为预测失败: {e}")
            return "unknown"

    # models/behavior_model.py 中修改 predict_rumination 方法

    def predict_rumination(self, acoustic_features: Dict = None, accel_features: Dict = None) -> Tuple[bool, float]:
        """预测反刍行为"""
        try:
            # 处理默认值
            if acoustic_features is None:
                acoustic_features = {}
            if accel_features is None:
                accel_features = {}

            # 提取关键特征
            rumination_features = self._extract_rumination_specific_features(
                acoustic_features, accel_features
            )

            # 如果没有特征数据，返回默认值
            if len(rumination_features.get('acoustic', [])) == 0 and len(rumination_features.get('accel', [])) == 0:
                logger.warning("没有足够的特征数据用于反刍预测")
                return False, 0.0

            # 声学模型预测（如果有声学特征）
            acoustic_pred, acoustic_conf = 0, 0.0
            if len(rumination_features.get('acoustic', [])) > 0:
                acoustic_pred, acoustic_conf = self._predict_with_model(
                    'acoustic_svm', rumination_features['acoustic']
                )

            # 加速度模型预测（如果有加速度特征）
            accel_pred, accel_conf = 0, 0.0
            if len(rumination_features.get('accel', [])) > 0:
                accel_pred, accel_conf = self._predict_with_model(
                    'accel_logistic', rumination_features['accel']
                )

            # 加权融合
            is_ruminating = False
            confidence = 0.0

            # 根据是否有数据决定融合策略
            if acoustic_conf > 0 and accel_conf > 0:
                # 两个模型都有数据
                if acoustic_pred == 1 and accel_pred == 1:
                    is_ruminating = True
                    confidence = 0.6 * acoustic_conf + 0.4 * accel_conf
                elif acoustic_pred == 1 or accel_pred == 1:
                    if acoustic_conf > self.thresholds['rumination']:
                        is_ruminating = True
                        confidence = acoustic_conf
                    elif accel_conf > self.thresholds['rumination']:
                        is_ruminating = True
                        confidence = accel_conf
            elif acoustic_conf > 0:
                # 只有声学数据
                is_ruminating = acoustic_pred == 1
                confidence = acoustic_conf
            elif accel_conf > 0:
                # 只有加速度数据
                is_ruminating = accel_pred == 1
                confidence = accel_conf

            return is_ruminating, confidence

        except Exception as e:
            logger.error(f"反刍预测失败: {e}")
            return False, 0.0

    def predict_feeding(self, acoustic_features: Dict,
                        accel_features: Dict) -> Tuple[bool, float]:
        """预测采食行为"""
        try:
            # 提取关键特征
            feeding_features = self._extract_feeding_specific_features(
                acoustic_features, accel_features
            )

            # 声学模型预测
            acoustic_pred, acoustic_conf = self._predict_with_model(
                'acoustic_svm', feeding_features['acoustic']
            )

            # 加速度模型预测
            accel_pred, accel_conf = self._predict_with_model(
                'accel_logistic', feeding_features['accel']
            )

            # 加权融合
            is_feeding = False
            confidence = 0.0

            if acoustic_pred == 2 and accel_pred == 2:  # 假设2代表采食
                # 两个模型都认为是采食
                is_feeding = True
                confidence = 0.5 * acoustic_conf + 0.5 * accel_conf
            elif acoustic_pred == 2 or accel_pred == 2:
                # 只有一个模型认为是采食
                if acoustic_conf > self.thresholds['feeding']:
                    is_feeding = True
                    confidence = acoustic_conf
                elif accel_conf > self.thresholds['feeding']:
                    is_feeding = True
                    confidence = accel_conf

            return is_feeding, confidence

        except Exception as e:
            logger.error(f"采食预测失败: {e}")
            return False, 0.0

    def assess_health(self, health_features: Dict) -> Tuple[str, float]:
        """健康评估"""
        try:
            health_score = 0.0
            factors = []
            weights = []

            # 体温评分
            if 'temp_normal' in health_features:
                temp_score = health_features['temp_normal']
                factors.append(temp_score)
                weights.append(0.3)

            # 心率评分
            if 'hr_normal' in health_features:
                hr_score = health_features['hr_normal']
                factors.append(hr_score)
                weights.append(0.3)

            # 反刍评分
            if 'rumination_normal' in health_features:
                rumination_score = health_features['rumination_normal']
                factors.append(rumination_score)
                weights.append(0.4)

            # 计算加权得分
            if factors and weights:
                total_weight = sum(weights)
                if total_weight > 0:
                    weighted_scores = [f * w for f, w in zip(factors, weights)]
                    health_score = sum(weighted_scores) / total_weight

            # 确定健康状态
            if health_score >= 0.8:
                health_status = "HEALTHY"
            elif health_score >= 0.6:
                health_status = "WARNING"
            else:
                health_status = "ALERT"

            return health_status, health_score

        except Exception as e:
            logger.error(f"健康评估失败: {e}")
            return "UNKNOWN", 0.0

    def generate_health_alerts(self, health_features: Dict) -> List[Dict]:
        """生成健康预警"""
        alerts = []

        # 检查体温异常
        if 'temperature' in health_features:
            temp = health_features['temperature']
            if temp < 38.0 or temp > 39.5:
                alerts.append({
                    'type': 'TEMPERATURE_ABNORMAL',
                    'level': 'WARNING' if 37.5 <= temp <= 40.0 else 'CRITICAL',
                    'message': f'体温异常: {temp:.1f}°C',
                    'value': temp,
                    'threshold_low': 38.0,
                    'threshold_high': 39.5
                })

        # 检查心率异常
        if 'heart_rate' in health_features:
            hr = health_features['heart_rate']
            if hr < 60 or hr > 80:
                alerts.append({
                    'type': 'HEART_RATE_ABNORMAL',
                    'level': 'WARNING' if 55 <= hr <= 85 else 'CRITICAL',
                    'message': f'心率异常: {hr} bpm',
                    'value': hr,
                    'threshold_low': 60,
                    'threshold_high': 80
                })

        # 检查反刍异常
        if 'rumination_duration' in health_features:
            rumination_hours = health_features['rumination_duration'] / 3600
            if rumination_hours < 4:
                alerts.append({
                    'type': 'RUMINATION_INSUFFICIENT',
                    'level': 'WARNING' if rumination_hours >= 3 else 'CRITICAL',
                    'message': f'反刍不足: {rumination_hours:.1f}小时',
                    'value': rumination_hours,
                    'threshold': 4
                })
            elif rumination_hours > 8:
                alerts.append({
                    'type': 'RUMINATION_EXCESSIVE',
                    'level': 'WARNING',
                    'message': f'反刍过多: {rumination_hours:.1f}小时',
                    'value': rumination_hours,
                    'threshold': 8
                })

        return alerts

    def get_confidence(self, features: np.ndarray) -> float:
        """获取预测置信度"""
        try:
            voting_model = self.models['voting']
            scaler = self.scalers['voting']

            # 标准化特征
            if hasattr(features, 'reshape'):
                features_2d = features.reshape(1, -1)
            else:
                features_2d = np.array(features).reshape(1, -1)

            features_scaled = scaler.transform(features_2d)

            # 获取预测概率
            probabilities = voting_model.predict_proba(features_scaled)[0]
            confidence = np.max(probabilities)

            return float(confidence)

        except Exception as e:
            logger.error(f"获取置信度失败: {e}")
            return 0.0

    def _predict_with_model(self, model_name: str,
                            features: np.ndarray) -> Tuple[int, float]:
        """使用指定模型进行预测"""
        try:
            model = self.models[model_name]
            scaler = self.scalers[model_name]

            # 标准化特征
            if hasattr(features, 'reshape'):
                features_2d = features.reshape(1, -1)
            else:
                features_2d = np.array(features).reshape(1, -1)

            features_scaled = scaler.transform(features_2d)

            # 预测
            prediction = model.predict(features_scaled)[0]

            # 获取置信度
            if hasattr(model, 'predict_proba'):
                probabilities = model.predict_proba(features_scaled)[0]
                confidence = probabilities[prediction]
            else:
                confidence = 1.0  # 对于没有概率输出的模型

            return prediction, float(confidence)

        except Exception as e:
            logger.error(f"模型预测失败: {e}")
            return 0, 0.0

    def _extract_rumination_specific_features(self,
                                              acoustic_features: Dict,
                                              accel_features: Dict) -> Dict[str, np.ndarray]:
        """提取反刍特定特征"""
        rumination_features = {
            'acoustic': [],
            'accel': []
        }

        # 声学特征
        acoustic_keys = [
            'sound_periodicity', 'periodicity_strength', 'regularity_score',
            'spectral_centroid', 'mfcc_mean'
        ]

        for key in acoustic_keys:
            if key in acoustic_features:
                rumination_features['acoustic'].append(acoustic_features[key])

        # 加速度特征
        accel_keys = [
            'jaw_period', 'jaw_frequency', 'chewing_regularity',
            'zero_crossing_rate', 'peak_count'
        ]

        for key in accel_keys:
            if key in accel_features:
                rumination_features['accel'].append(accel_features[key])

        # 转换为numpy数组
        rumination_features['acoustic'] = np.array(rumination_features['acoustic'])
        rumination_features['accel'] = np.array(rumination_features['accel'])

        return rumination_features

    def _extract_feeding_specific_features(self,
                                           acoustic_features: Dict,
                                           accel_features: Dict) -> Dict[str, np.ndarray]:
        """提取采食特定特征"""
        feeding_features = {
            'acoustic': [],
            'accel': []
        }

        # 声学特征
        acoustic_keys = [
            'dominant_frequency', 'sound_energy', 'sound_rms',
            'spectral_bandwidth'
        ]

        for key in acoustic_keys:
            if key in acoustic_features:
                feeding_features['acoustic'].append(acoustic_features[key])

        # 加速度特征
        accel_keys = [
            'head_activity_mean', 'head_activity_std',
            'head_movement_count', 'head_movement_interval_mean'
        ]

        for key in accel_keys:
            if key in accel_features:
                feeding_features['accel'].append(accel_features[key])

        # 转换为numpy数组
        feeding_features['acoustic'] = np.array(feeding_features['acoustic'])
        feeding_features['accel'] = np.array(feeding_features['accel'])

        return feeding_features

    def _map_behavior_label(self, label: int) -> str:
        """映射行为标签"""
        return self.label_map.get(label, "unknown")

    def save_models(self, path: str):
        """保存模型"""
        model_data = {
            'models': self.models,
            'scalers': self.scalers,
            'thresholds': self.thresholds
        }
        joblib.dump(model_data, path)
        logger.info(f"模型已保存到: {path}")

    def load_models(self, path: str):
        """加载模型"""
        try:
            model_data = joblib.load(path)
            self.models = model_data['models']
            self.scalers = model_data['scalers']
            self.thresholds = model_data.get('thresholds', self.thresholds)
            logger.info(f"模型已从 {path} 加载")
        except Exception as e:
            logger.error(f"加载模型失败: {e}")
            self._init_models()  # 重新初始化模型