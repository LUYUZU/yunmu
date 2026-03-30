# models/ensemble_model.py
import numpy as np
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
import joblib
import logging
from typing import Dict, List, Tuple

logger = logging.getLogger(__name__)


class EnsembleModel:
    """集成学习模型"""

    def __init__(self):
        self.models = {}
        self.scalers = {}
        self.model_weights = {}
        self._init_models()

    def _init_models(self):
        """初始化集成模型"""
        # 随机森林
        self.models['random_forest'] = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            min_samples_split=5,
            min_samples_leaf=2,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1
        )

        # 梯度提升树
        self.models['gradient_boosting'] = GradientBoostingClassifier(
            n_estimators=100,
            max_depth=5,
            learning_rate=0.1,
            random_state=42
        )

        # 初始化权重
        self.model_weights = {
            'random_forest': 0.6,
            'gradient_boosting': 0.4
        }

        # 初始化标准化器
        for model_name in self.models:
            self.scalers[model_name] = StandardScaler()

    def train(self, X: np.ndarray, y: np.ndarray, model_name: str = None):
        """训练模型"""
        try:
            if model_name:
                # 训练指定模型
                if model_name in self.models:
                    X_scaled = self.scalers[model_name].fit_transform(X)
                    self.models[model_name].fit(X_scaled, y)
                    logger.info(f"模型 {model_name} 训练完成")
            else:
                # 训练所有模型
                for name, model in self.models.items():
                    X_scaled = self.scalers[name].fit_transform(X)
                    model.fit(X_scaled, y)
                    logger.info(f"模型 {name} 训练完成")

        except Exception as e:
            logger.error(f"集成模型训练失败: {e}")
            raise

    def predict(self, features: np.ndarray, use_weighted: bool = True) -> Tuple[int, float]:
        """集成预测"""
        try:
            if len(features.shape) == 1:
                features = features.reshape(1, -1)

            model_predictions = {}
            model_confidences = {}

            # 获取各个模型的预测
            for model_name, model in self.models.items():
                scaler = self.scalers[model_name]

                try:
                    features_scaled = scaler.transform(features)
                    prediction = model.predict(features_scaled)[0]

                    # 获取置信度
                    if hasattr(model, 'predict_proba'):
                        probabilities = model.predict_proba(features_scaled)[0]
                        confidence = probabilities[prediction]
                    else:
                        confidence = 1.0

                    model_predictions[model_name] = int(prediction)
                    model_confidences[model_name] = float(confidence)

                except Exception as e:
                    logger.error(f"模型 {model_name} 预测失败: {e}")
                    continue

            if not model_predictions:
                return 0, 0.0

            if use_weighted:
                # 加权投票
                return self._weighted_vote(model_predictions, model_confidences)
            else:
                # 简单投票
                return self._simple_vote(model_predictions, model_confidences)

        except Exception as e:
            logger.error(f"集成预测失败: {e}")
            return 0, 0.0

    def predict_with_uncertainty(self, features: np.ndarray) -> Dict:
        """带不确定性的预测"""
        try:
            if len(features.shape) == 1:
                features = features.reshape(1, -1)

            all_predictions = []
            all_confidences = []

            # 收集所有模型的预测
            for model_name, model in self.models.items():
                scaler = self.scalers[model_name]

                try:
                    features_scaled = scaler.transform(features)
                    prediction = model.predict(features_scaled)[0]

                    if hasattr(model, 'predict_proba'):
                        probabilities = model.predict_proba(features_scaled)[0]
                        confidence = probabilities[prediction]
                    else:
                        confidence = 1.0

                    all_predictions.append(int(prediction))
                    all_confidences.append(float(confidence))

                except Exception as e:
                    logger.error(f"模型 {model_name} 预测失败: {e}")
                    continue

            if not all_predictions:
                return {
                    'prediction': 0,
                    'confidence': 0.0,
                    'uncertainty': 1.0
                }

            # 计算最终预测（众数）
            final_prediction = self._get_mode(all_predictions)

            # 计算平均置信度
            avg_confidence = np.mean(all_confidences)

            # 计算不确定性（预测不一致性）
            uncertainty = self._calculate_uncertainty(all_predictions)

            return {
                'prediction': final_prediction,
                'confidence': float(avg_confidence),
                'uncertainty': float(uncertainty),
                'model_predictions': all_predictions,
                'model_confidences': all_confidences
            }

        except Exception as e:
            logger.error(f"带不确定性预测失败: {e}")
            return {
                'prediction': 0,
                'confidence': 0.0,
                'uncertainty': 1.0
            }

    def _weighted_vote(self, predictions: Dict[str, int],
                       confidences: Dict[str, float]) -> Tuple[int, float]:
        """加权投票"""
        weighted_sum = 0.0
        weight_sum = 0.0

        for model_name, prediction in predictions.items():
            confidence = confidences.get(model_name, 0.5)
            weight = self.model_weights.get(model_name, 0.5)

            weighted_sum += prediction * weight * confidence
            weight_sum += weight

        if weight_sum > 0:
            final_prediction = int(round(weighted_sum / weight_sum))
            avg_confidence = np.mean(list(confidences.values()))
            return final_prediction, float(avg_confidence)

        return 0, 0.0

    def _simple_vote(self, predictions: Dict[str, int],
                     confidences: Dict[str, float]) -> Tuple[int, float]:
        """简单投票"""
        # 统计投票
        vote_count = {}
        for prediction in predictions.values():
            vote_count[prediction] = vote_count.get(prediction, 0) + 1

        # 选择票数最多的
        if vote_count:
            final_prediction = max(vote_count.items(), key=lambda x: x[1])[0]
            avg_confidence = np.mean(list(confidences.values()))
            return final_prediction, float(avg_confidence)

        return 0, 0.0

    def _get_mode(self, values: List) -> int:
        """获取众数"""
        if not values:
            return 0
        return max(set(values), key=values.count)

    def _calculate_uncertainty(self, predictions: List[int]) -> float:
        """计算不确定性"""
        if not predictions:
            return 1.0

        # 预测一致性度量
        unique_predictions = len(set(predictions))
        total_predictions = len(predictions)

        if total_predictions == 1:
            return 0.0

        # 不确定性 = 1 - (主要类别的比例)
        main_prediction = self._get_mode(predictions)
        main_count = predictions.count(main_prediction)
        main_ratio = main_count / total_predictions

        return 1.0 - main_ratio

    def get_feature_importance(self, model_name: str = 'random_forest') -> Dict[str, float]:
        """获取特征重要性"""
        try:
            if model_name not in self.models:
                return {}

            model = self.models[model_name]

            if hasattr(model, 'feature_importances_'):
                importance = model.feature_importances_
                return {f'feature_{i}': float(imp) for i, imp in enumerate(importance)}
            else:
                return {}

        except Exception as e:
            logger.error(f"获取特征重要性失败: {e}")
            return {}

    def save_models(self, path: str):
        """保存所有模型"""
        try:
            model_data = {
                'models': self.models,
                'scalers': self.scalers,
                'model_weights': self.model_weights
            }
            joblib.dump(model_data, path)
            logger.info(f"集成模型已保存到: {path}")
        except Exception as e:
            logger.error(f"保存集成模型失败: {e}")

    def load_models(self, path: str):
        """加载所有模型"""
        try:
            model_data = joblib.load(path)
            self.models = model_data['models']
            self.scalers = model_data['scalers']
            self.model_weights = model_data.get('model_weights', self.model_weights)
            logger.info(f"集成模型已从 {path} 加载")
        except Exception as e:
            logger.error(f"加载集成模型失败: {e}")
            self._init_models()