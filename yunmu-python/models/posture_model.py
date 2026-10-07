# models/posture_model.py - 姿态识别模块
import numpy as np
from typing import Dict, List  # 确保导入
import logging

logger = logging.getLogger(__name__)


class PostureClassifier:
    """姿态分类器 - 基于加速度和角速度"""

    def __init__(self):
        self.posture_types = ['standing', 'lying', 'walking', 'feeding', 'running']
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

            # 计算陀螺仪模值
            gyro_magnitude = abs(gyro_x) + abs(gyro_y) + abs(gyro_z)

            # running 优先判断（高速运动）
            if gyro_magnitude > 2.0:
                posture = 'running'
                confidence = 0.85
            elif accel_magnitude < self.thresholds['lying_accel']:
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

            # probabilities 与 posture_types 对应: [standing, lying, walking, feeding, running]
            prob_map = {
                'standing': [0.70, 0.10, 0.10, 0.05, 0.05],
                'lying':    [0.10, 0.70, 0.10, 0.05, 0.05],
                'walking':  [0.10, 0.10, 0.70, 0.05, 0.05],
                'feeding':  [0.10, 0.10, 0.05, 0.70, 0.05],
                'running':  [0.05, 0.05, 0.10, 0.05, 0.75],
            }
            return {
                'success': True,
                'posture_type': posture,
                'confidence': confidence,
                'probabilities': prob_map.get(posture, [0.2, 0.2, 0.2, 0.2, 0.2]),
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