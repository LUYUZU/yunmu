# models/posture_model.py - 姿态识别模块
import numpy as np
from typing import Dict, List  # 确保导入
import logging

logger = logging.getLogger(__name__)


class PostureClassifier:
    """姿态分类器 - 基于加速度和角速度"""

    def __init__(self):
        self.posture_types = ['standing', 'lying', 'feeding', 'walking']
        self.thresholds = {
            'lying_accel': 9.0,
            'walking_accel_y': 0.5,
            'walking_gyro_x': 0.3,
            'feeding_gyro_y': 0.4
        }

    def predict_posture(self, accel_x: float, accel_y: float, accel_z: float,
                        gyro_x: float = 0, gyro_y: float = 0, gyro_z: float = 0,
                        timestamp: float = None) -> Dict:
        """姿态识别"""
        try:
            accel_magnitude = np.sqrt(accel_x ** 2 + accel_y ** 2 + accel_z ** 2)

            if accel_magnitude < self.thresholds['lying_accel']:
                posture = 'lying'
                confidence = 0.85
            elif accel_y > self.thresholds['walking_accel_y'] and abs(gyro_x) > self.thresholds['walking_gyro_x']:
                posture = 'walking'
                confidence = 0.80
            elif abs(gyro_y) > self.thresholds['feeding_gyro_y']:
                posture = 'feeding'
                confidence = 0.75
            else:
                posture = 'standing'
                confidence = 0.70

            return {
                'success': True,
                'posture_type': posture,
                'confidence': confidence,
                'probabilities': [0.7, 0.1, 0.1, 0.1],
                'features': {
                    'accel': [accel_x, accel_y, accel_z],
                    'gyro': [gyro_x, gyro_y, gyro_z],
                    'accel_magnitude': float(accel_magnitude)
                },
                'timestamp': timestamp
            }
        except Exception as e:
            logger.error(f"姿态预测失败: {e}")
            return {'success': False, 'error': str(e)}

    def batch_predict(self, data_list: List[Dict]) -> List[Dict]:
        """批量预测"""
        results = []
        for data in data_list:
            result = self.predict_posture(
                data.get('accel_x', 0), data.get('accel_y', 0), data.get('accel_z', 9.8),
                data.get('gyro_x', 0), data.get('gyro_y', 0), data.get('gyro_z', 0),
                data.get('timestamp')
            )
            results.append(result)
        return results