# features/feature_fusion.py
import numpy as np
from typing import Dict, List, Tuple
import logging

logger = logging.getLogger(__name__)


class FeatureFusion:
    """特征融合模块"""

    def __init__(self):
        self.fusion_weights = {
            'rumination': {
                'acoustic': 0.6,
                'accel': 0.4
            },
            'feeding': {
                'acoustic': 0.5,
                'accel': 0.5
            },
            'general': {
                'acoustic': 0.3,
                'accel': 0.4,
                'gps': 0.2,
                'health': 0.1
            }
        }

    def fuse_modality_features(self, modality_features: Dict[str, np.ndarray],
                               behavior_type: str = 'general') -> np.ndarray:
        """融合多模态特征"""
        try:
            if behavior_type not in self.fusion_weights:
                behavior_type = 'general'

            weights = self.fusion_weights[behavior_type]
            fused_features = []

            for modality, weight in weights.items():
                if modality in modality_features:
                    modality_data = modality_features[modality]
                    if len(modality_data) > 0:
                        # 加权特征
                        weighted_features = modality_data * weight
                        fused_features.extend(weighted_features)

            if not fused_features:
                # 如果没有特征，返回空数组
                return np.array([])

            return np.array(fused_features)

        except Exception as e:
            logger.error(f"特征融合失败: {e}")
            return np.array([])

    def temporal_fusion(self, feature_sequence: List[np.ndarray],
                        window_size: int = 10) -> Dict[str, np.ndarray]:
        """时序特征融合"""
        try:
            if not feature_sequence:
                return {}

            # 转换为numpy数组
            seq_array = np.array(feature_sequence)

            # 滑动窗口统计特征
            window_features = {}

            # 均值
            window_features['temporal_mean'] = np.mean(seq_array, axis=0)

            # 标准差
            window_features['temporal_std'] = np.std(seq_array, axis=0)

            # 趋势（一阶差分均值）
            if len(seq_array) > 1:
                diffs = np.diff(seq_array, axis=0)
                window_features['temporal_trend'] = np.mean(diffs, axis=0)

            # 最大值和最小值
            window_features['temporal_max'] = np.max(seq_array, axis=0)
            window_features['temporal_min'] = np.min(seq_array, axis=0)

            # 变化率
            if len(seq_array) > 1:
                changes = np.abs(np.diff(seq_array, axis=0))
                window_features['temporal_change_rate'] = np.mean(changes, axis=0)

            return window_features

        except Exception as e:
            logger.error(f"时序特征融合失败: {e}")
            return {}

    def weighted_fusion_predictions(self, predictions: Dict[str, Tuple[int, float]],
                                    behavior_type: str = 'general') -> Tuple[int, float]:
        """加权融合多个模型的预测结果"""
        try:
            if not predictions:
                return 0, 0.0

            if behavior_type not in self.fusion_weights:
                behavior_type = 'general'

            weights = self.fusion_weights[behavior_type]

            weighted_sum = 0.0
            weight_sum = 0.0
            confidence_sum = 0.0

            for model_name, (prediction, confidence) in predictions.items():
                # 获取模型对应的模态权重
                modality = self._get_modality_from_model(model_name)
                if modality in weights:
                    weight = weights[modality]
                    weighted_sum += prediction * weight * confidence
                    weight_sum += weight
                    confidence_sum += confidence

            if weight_sum > 0 and confidence_sum > 0:
                final_prediction = int(round(weighted_sum / (weight_sum * (confidence_sum / len(predictions)))))
                final_confidence = confidence_sum / len(predictions)
                return final_prediction, final_confidence

            return 0, 0.0

        except Exception as e:
            logger.error(f"预测加权融合失败: {e}")
            return 0, 0.0

    def _get_modality_from_model(self, model_name: str) -> str:
        """根据模型名称获取对应的模态"""
        model_modality_map = {
            'acoustic_svm': 'acoustic',
            'accel_logistic': 'accel',
            'ensemble_rf': 'general',
            'voting': 'general'
        }
        return model_modality_map.get(model_name, 'general')

    def decision_level_fusion(self, model_results: List[Dict]) -> Dict:
        """决策级融合"""
        try:
            if not model_results:
                return {}

            # 统计各个模型的预测结果
            predictions = {}
            confidences = {}

            for result in model_results:
                model_name = result.get('model_name', 'unknown')
                prediction = result.get('prediction', 0)
                confidence = result.get('confidence', 0.0)

                if model_name not in predictions:
                    predictions[model_name] = []
                    confidences[model_name] = []

                predictions[model_name].append(prediction)
                confidences[model_name].append(confidence)

            # 计算每个模型的平均预测和置信度
            model_stats = {}
            for model_name in predictions:
                model_stats[model_name] = {
                    'mean_prediction': np.mean(predictions[model_name]),
                    'std_prediction': np.std(predictions[model_name]),
                    'mean_confidence': np.mean(confidences[model_name]),
                    'prediction_mode': self._get_mode(predictions[model_name])
                }

            # 基于置信度的加权投票
            final_prediction = self._confidence_weighted_vote(model_stats)

            return {
                'final_prediction': final_prediction,
                'model_statistics': model_stats,
                'fusion_method': 'decision_level'
            }

        except Exception as e:
            logger.error(f"决策级融合失败: {e}")
            return {}

    def _get_mode(self, values: List) -> float:
        """获取众数"""
        if not values:
            return 0
        return max(set(values), key=values.count)

    def _confidence_weighted_vote(self, model_stats: Dict) -> int:
        """基于置信度的加权投票"""
        weighted_sum = 0.0
        weight_sum = 0.0

        for model_name, stats in model_stats.items():
            prediction = stats['prediction_mode']
            confidence = stats['mean_confidence']

            # 使用置信度作为权重
            weighted_sum += prediction * confidence
            weight_sum += confidence

        if weight_sum > 0:
            return int(round(weighted_sum / weight_sum))

        return 0