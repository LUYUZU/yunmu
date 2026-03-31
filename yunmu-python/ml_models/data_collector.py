# ml_models/data_collector.py - 数据收集模块
"""
用于收集和标注牛羊行为数据
收集现有规则引擎的预测结果作为伪标签，或手动标注真实数据
"""
import json
import os
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Optional
import numpy as np

class BehaviorDataCollector:
    """行为数据收集器"""
    
    def __init__(self, data_dir: str = None):
        if data_dir is None:
            base_dir = Path(__file__).parent.parent
            data_dir = base_dir / "data"
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(exist_ok=True)
        
        self.raw_data_file = self.data_dir / "raw_behavior_data.json"
        self.labeled_data_file = self.data_dir / "labeled_behavior_data.json"
        
        self.raw_samples = []
        self.labeled_samples = []
        
        self._load_data()
    
    def _load_data(self):
        """加载已有数据"""
        if self.raw_data_file.exists():
            with open(self.raw_data_file, 'r', encoding='utf-8') as f:
                self.raw_samples = json.load(f)
        
        if self.labeled_data_file.exists():
            with open(self.labeled_data_file, 'r', encoding='utf-8') as f:
                self.labeled_samples = json.load(f)
    
    def _save_raw_data(self):
        """保存原始数据"""
        with open(self.raw_data_file, 'w', encoding='utf-8') as f:
            json.dump(self.raw_samples, f, ensure_ascii=False, indent=2)
    
    def _save_labeled_data(self):
        """保存标注数据"""
        with open(self.labeled_data_file, 'w', encoding='utf-8') as f:
            json.dump(self.labeled_samples, f, ensure_ascii=False, indent=2)
    
    def add_sample(self, 
                  accel_x: float, accel_y: float, accel_z: float,
                  gyro_x: float, gyro_y: float, gyro_z: float,
                  posture: str = None,
                  device_id: str = None,
                  timestamp: float = None):
        """
        添加一个样本数据
        
        Args:
            accel_x, accel_y, accel_z: 加速度数据 (m/s²)
            gyro_x, gyro_y, gyro_z: 角速度数据 (rad/s)
            posture: 姿态标签 (standing/lying/walking/feeding/running)
            device_id: 设备ID
            timestamp: 时间戳
        """
        # 计算特征
        features = self._extract_features(
            accel_x, accel_y, accel_z,
            gyro_x, gyro_y, gyro_z
        )
        
        sample = {
            'features': features,
            'raw_sensors': {
                'accel': [accel_x, accel_y, accel_z],
                'gyro': [gyro_x, gyro_y, gyro_z]
            },
            'posture': posture,
            'device_id': device_id,
            'timestamp': timestamp or datetime.now().timestamp(),
            'labeled': posture is not None
        }
        
        self.raw_samples.append(sample)
        
        if posture is not None:
            self.labeled_samples.append(sample)
        
        self._save_raw_data()
        if posture is not None:
            self._save_labeled_data()
        
        return sample
    
    def _extract_features(self, accel_x, accel_y, accel_z,
                         gyro_x, gyro_y, gyro_z) -> Dict:
        """提取特征"""
        # 加速度特征
        accel_magnitude = np.sqrt(accel_x**2 + accel_y**2 + accel_z**2)
        accel_xy = np.sqrt(accel_x**2 + accel_y**2)
        
        # 角速度特征
        gyro_magnitude = np.sqrt(gyro_x**2 + gyro_y**2 + gyro_z**2)
        
        # 方向特征
        accel_angle_y = np.arctan2(accel_y, np.sqrt(accel_x**2 + accel_z**2))
        accel_angle_z = np.arctan2(accel_z, np.sqrt(accel_x**2 + accel_y**2))
        
        return {
            # 加速度特征
            'accel_magnitude': float(accel_magnitude),
            'accel_xy': float(accel_xy),
            'accel_x': float(accel_x),
            'accel_y': float(accel_y),
            'accel_z': float(accel_z),
            'accel_angle_y': float(accel_angle_y),
            'accel_angle_z': float(accel_angle_z),
            
            # 角速度特征
            'gyro_magnitude': float(gyro_magnitude),
            'gyro_x': float(gyro_x),
            'gyro_y': float(gyro_y),
            'gyro_z': float(gyro_z),
            
            # 组合特征
            'accel_gyro_ratio': float(accel_magnitude / (gyro_magnitude + 1e-6)),
            'activity_index': float(accel_magnitude * gyro_magnitude)
        }
    
    def add_samples_batch(self, samples: List[Dict]):
        """批量添加样本"""
        for sample in samples:
            self.add_sample(
                accel_x=sample.get('accel_x', 0),
                accel_y=sample.get('accel_y', 0),
                accel_z=sample.get('accel_z', 9.8),
                gyro_x=sample.get('gyro_x', 0),
                gyro_y=sample.get('gyro_y', 0),
                gyro_z=sample.get('gyro_z', 0),
                posture=sample.get('posture'),
                device_id=sample.get('device_id'),
                timestamp=sample.get('timestamp')
            )
    
    def generate_synthetic_data(self, num_samples_per_class: int = 500) -> Dict:
        """
        生成合成训练数据（基于物理模拟）
        
        模拟不同姿态下的传感器数据分布
        """
        np.random.seed(42)
        
        synthetic_data = []
        
        # 姿态参数配置（基于物理特性）
        posture_configs = {
            'standing': {
                'accel_mean': [0.1, 0.2, 9.7],
                'accel_std': [0.3, 0.3, 0.3],
                'gyro_mean': [0.0, 0.0, 0.0],
                'gyro_std': [0.1, 0.1, 0.1]
            },
            'lying': {
                'accel_mean': [0.0, 9.5, 0.5],
                'accel_std': [0.3, 0.3, 0.3],
                'gyro_mean': [0.0, 0.0, 0.0],
                'gyro_std': [0.1, 0.1, 0.1]
            },
            'walking': {
                'accel_mean': [0.5, 0.8, 9.5],
                'accel_std': [0.5, 0.5, 0.4],
                'gyro_mean': [0.5, 0.1, 0.1],
                'gyro_std': [0.3, 0.2, 0.2]
            },
            'feeding': {
                'accel_mean': [0.3, 0.6, 9.6],
                'accel_std': [0.4, 0.4, 0.3],
                'gyro_mean': [0.1, 0.6, 0.1],
                'gyro_std': [0.2, 0.3, 0.2]
            },
            'running': {
                'accel_mean': [1.0, 1.5, 9.3],
                'accel_std': [0.8, 0.8, 0.6],
                'gyro_mean': [1.2, 0.3, 0.2],
                'gyro_std': [0.5, 0.3, 0.3]
            }
        }
        
        for posture, config in posture_configs.items():
            for _ in range(num_samples_per_class):
                # 生成带噪声的传感器数据
                accel_x = np.random.normal(config['accel_mean'][0], config['accel_std'][0])
                accel_y = np.random.normal(config['accel_mean'][1], config['accel_std'][1])
                accel_z = np.random.normal(config['accel_mean'][2], config['accel_std'][2])
                
                gyro_x = np.random.normal(config['gyro_mean'][0], config['gyro_std'][0])
                gyro_y = np.random.normal(config['gyro_mean'][1], config['gyro_std'][1])
                gyro_z = np.random.normal(config['gyro_mean'][2], config['gyro_std'][2])
                
                sample = {
                    'accel_x': float(accel_x),
                    'accel_y': float(accel_y),
                    'accel_z': float(accel_z),
                    'gyro_x': float(gyro_x),
                    'gyro_y': float(gyro_y),
                    'gyro_z': float(gyro_z),
                    'posture': posture
                }
                
                synthetic_data.append(sample)
                self.add_sample(**sample)
        
        return {'total_samples': len(synthetic_data), 'postures': list(posture_configs.keys())}
    
    def get_training_data(self) -> tuple:
        """获取训练数据格式 (X, y)"""
        if not self.labeled_samples:
            return None, None
        
        X = []
        y = []
        
        posture_map = {
            'standing': 0, 'lying': 1, 'walking': 2, 'feeding': 3, 'running': 4
        }
        
        for sample in self.labeled_samples:
            features = sample['features']
            X.append([
                features['accel_magnitude'], features['accel_xy'],
                features['accel_x'], features['accel_y'], features['accel_z'],
                features['accel_angle_y'], features['accel_angle_z'],
                features['gyro_magnitude'], features['gyro_x'], features['gyro_y'], features['gyro_z'],
                features['accel_gyro_ratio'], features['activity_index']
            ])
            y.append(posture_map.get(sample['posture'], 0))
        
        return np.array(X), np.array(y)
    
    def get_statistics(self) -> Dict:
        """获取数据统计"""
        if not self.labeled_samples:
            return {'total': 0, 'message': '没有标注数据'}
        
        posture_counts = {}
        for sample in self.labeled_samples:
            p = sample['posture']
            posture_counts[p] = posture_counts.get(p, 0) + 1
        
        return {
            'total_samples': len(self.labeled_samples),
            'raw_samples': len(self.raw_samples),
            'posture_distribution': posture_counts,
            'features_count': 12 if self.labeled_samples else 0
        }


# 测试代码
if __name__ == '__main__':
    collector = BehaviorDataCollector()
    
    # 生成合成数据
    print("正在生成合成训练数据...")
    result = collector.generate_synthetic_data(num_samples_per_class=500)
    print(f"生成完成: {result}")
    
    # 查看统计
    stats = collector.get_statistics()
    print(f"\n数据统计: {stats}")
    
    # 获取训练数据
    X, y = collector.get_training_data()
    if X is not None:
        print(f"\n训练数据形状: X={X.shape}, y={y.shape}")
