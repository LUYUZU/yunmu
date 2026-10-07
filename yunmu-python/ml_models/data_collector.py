# ml_models/data_collector.py - 数据收集模块
"""
用于收集和标注牛羊行为数据
收集现有规则引擎的预测结果作为伪标签，或手动标注真实数据

修复：批量写入 + 文件锁，防止并发写入损坏数据
"""
import json
import os
import threading
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Optional
import numpy as np

from utils.feature_extractor import features_to_dict


class BehaviorDataCollector:
    """行为数据收集器（线程安全，批量持久化）"""

    # 缓冲区大小：积累多少条样本才写盘
    BATCH_SIZE = 50

    def __init__(self, data_dir: str = None):
        if data_dir is None:
            base_dir = Path(__file__).parent.parent
            data_dir = base_dir / "data"
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(exist_ok=True)

        self.raw_data_file = self.data_dir / "raw_behavior_data.json"
        self.labeled_data_file = self.data_dir / "labeled_behavior_data.json"

        self.raw_samples: List[Dict] = []
        self.labeled_samples: List[Dict] = []

        # 缓冲区（达到 BATCH_SIZE 才写盘）
        self._raw_buffer: List[Dict] = []
        self._labeled_buffer: List[Dict] = []

        # 线程安全锁
        self._lock = threading.Lock()

        self._load_data()

    def _load_data(self):
        """加载已有数据（启动时一次性加载到内存）"""
        if self.raw_data_file.exists():
            with open(self.raw_data_file, 'r', encoding='utf-8') as f:
                self.raw_samples = json.load(f)

        if self.labeled_data_file.exists():
            with open(self.labeled_data_file, 'r', encoding='utf-8') as f:
                self.labeled_samples = json.load(f)

    # -------------------------------------------------------------------------
    # 线程安全的文件写入（带文件锁，防止并发写入损坏 JSON）
    # -------------------------------------------------------------------------

    def _write_json_atomic(self, file_path: Path, data: list):
        """
        原子写入：先写临时文件，再原子替换，防止写入一半时崩溃导致文件损坏
        """
        temp_path = file_path.with_suffix('.tmp')
        try:
            with open(temp_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            # os.replace 在 Windows 与 POSIX 上都支持「替换已存在的目标文件」
            # （Windows 走 MoveFileEx(MOVEFILE_REPLACE_EXISTING)，本身就是原子的），
            # 因此不需要「先删再改名」。
            #
            # 这一点非常关键：早期实现在 os.name == 'nt' 分支里先调用 os.remove(file_path)，
            # 该删除会被主机/安全软件/同步盘的删除拦截层拦下（日志表现为每条
            # [safe-delete][SAFE_DELETE_BULK_CONFIRM_REQUIRED]），并且会把调用线程卡在
            # 「等待删除确认」上。由于本函数在 /api/device/data 的请求线程内被调用
            # （每 BATCH_SIZE=50 个样本写一次盘），效果就是推理接口在约第 50 个请求后整体卡死：
            # Java 侧 1500ms 超时 → 大面积降级，历史回放数据 3/4 丢失。
            os.replace(temp_path, file_path)
        except Exception:
            # 写入失败，清理临时文件
            if temp_path.exists():
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            raise

    def _save_raw_data(self, data: list):
        """写原始数据文件"""
        self._write_json_atomic(self.raw_data_file, data)

    def _save_labeled_data(self, data: list):
        """写标注数据文件"""
        self._write_json_atomic(self.labeled_data_file, data)

    def _flush_buffers(self):
        """
        将缓冲区数据合并写入磁盘（仅在缓冲区非空时操作）
        """
        if self._raw_buffer:
            all_raw = self.raw_samples + self._raw_buffer
            self._save_raw_data(all_raw)
            self.raw_samples = all_raw
            self._raw_buffer.clear()

        if self._labeled_buffer:
            all_labeled = self.labeled_samples + self._labeled_buffer
            self._save_labeled_data(all_labeled)
            self.labeled_samples = all_labeled
            self._labeled_buffer.clear()

    # -------------------------------------------------------------------------
    # 样本管理
    # -------------------------------------------------------------------------

    def add_sample(self,
                   accel_x: float, accel_y: float, accel_z: float,
                   gyro_x: float, gyro_y: float, gyro_z: float,
                   posture: str = None,
                   device_id: str = None,
                   timestamp: float = None) -> Dict:
        """
        添加一个样本（先入内存，积累到 BATCH_SIZE 才写盘）
        """
        features = features_to_dict(accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z)

        sample = {
            'features': features,
            'raw_sensors': {
                'accel': [float(accel_x), float(accel_y), float(accel_z)],
                'gyro': [float(gyro_x), float(gyro_y), float(gyro_z)]
            },
            'posture': posture,
            'device_id': device_id,
            'timestamp': timestamp or datetime.now().timestamp(),
            'labeled': posture is not None
        }

        with self._lock:
            self.raw_samples.append(sample)
            self._raw_buffer.append(sample)

            if posture is not None:
                self.labeled_samples.append(sample)
                self._labeled_buffer.append(sample)

            # 达到缓冲区大小，触发批量写盘
            if len(self._raw_buffer) >= self.BATCH_SIZE:
                self._flush_buffers()

        return sample

    def add_samples_batch(self, samples: List[Dict]):
        """批量添加样本"""
        for sample in samples:
            self.add_sample(
                accel_x=sample.get('accel_x', 0.0),
                accel_y=sample.get('accel_y', 0.0),
                accel_z=sample.get('accel_z', 9.8),
                gyro_x=sample.get('gyro_x', 0.0),
                gyro_y=sample.get('gyro_y', 0.0),
                gyro_z=sample.get('gyro_z', 0.0),
                posture=sample.get('posture'),
                device_id=sample.get('device_id'),
                timestamp=sample.get('timestamp')
            )

    def flush(self):
        """手动触发缓冲区写盘（进程退出前调用）"""
        with self._lock:
            self._flush_buffers()

    def generate_synthetic_data(self, num_samples_per_class: int = 500) -> Dict:
        """
        生成合成训练数据（基于物理模拟）
        """
        np.random.seed(42)

        synthetic_data = []

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
                accel_x = np.random.normal(config['accel_mean'][0], config['accel_std'][0])
                accel_y = np.random.normal(config['accel_mean'][1], config['accel_std'][1])
                accel_z = np.random.normal(config['accel_mean'][2], config['accel_std'][2])
                gyro_x = np.random.normal(config['gyro_mean'][0], config['gyro_std'][0])
                gyro_y = np.random.normal(config['gyro_mean'][1], config['gyro_std'][1])
                gyro_z = np.random.normal(config['gyro_mean'][2], config['gyro_std'][2])

                synthetic_data.append({
                    'accel_x': float(accel_x),
                    'accel_y': float(accel_y),
                    'accel_z': float(accel_z),
                    'gyro_x': float(gyro_x),
                    'gyro_y': float(gyro_y),
                    'gyro_z': float(gyro_z),
                    'posture': posture
                })

        # 批量添加（避免频繁加锁）
        self.add_samples_batch(synthetic_data)

        return {'total_samples': len(synthetic_data),
                'postures': list(posture_configs.keys())}

    def get_training_data(self) -> tuple:
        """获取训练数据格式 (X, y)"""
        if not self.labeled_samples:
            return None, None

        posture_map = {
            'standing': 0, 'lying': 1, 'walking': 2,
            'feeding': 3, 'running': 4
        }

        X = []
        y = []

        with self._lock:
            samples = self.labeled_samples

        for sample in samples:
            f = sample['features']
            X.append([
                f['accel_magnitude'], f['accel_xy'],
                f['accel_x'], f['accel_y'], f['accel_z'],
                f['accel_angle_y'], f['accel_angle_z'],
                f['gyro_magnitude'], f['gyro_x'], f['gyro_y'], f['gyro_z'],
                f['accel_gyro_ratio'], f['activity_index']
            ])
            y.append(posture_map.get(sample['posture'], 0))

        return np.array(X), np.array(y)

    def get_statistics(self) -> Dict:
        """获取数据统计"""
        with self._lock:
            total = len(self.labeled_samples)
            raw = len(self.raw_samples)

        if total == 0:
            return {'total': 0, 'message': '没有标注数据'}

        posture_counts = {}
        for sample in self.labeled_samples:
            p = sample['posture']
            posture_counts[p] = posture_counts.get(p, 0) + 1

        return {
            'total_samples': total,
            'raw_samples': raw,
            'posture_distribution': posture_counts,
            'features_count': 13,
            'buffer_pending': len(self._raw_buffer)
        }


# -------------------------------------------------------------------------
# 测试
# -------------------------------------------------------------------------
if __name__ == '__main__':
    collector = BehaviorDataCollector()

    print("正在生成合成训练数据...")
    result = collector.generate_synthetic_data(num_samples_per_class=500)
    print(f"生成完成: {result}")

    # 手动 flush 确保数据落盘
    collector.flush()

    stats = collector.get_statistics()
    print(f"\n数据统计: {stats}")

    X, y = collector.get_training_data()
    if X is not None:
        print(f"\n训练数据形状: X={X.shape}, y={y.shape}")
