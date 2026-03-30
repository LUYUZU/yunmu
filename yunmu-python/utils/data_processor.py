# utils/data_processor.py
import numpy as np
import pandas as pd
from typing import Dict, List, Tuple, Optional
import logging
from scipy import signal
from scipy.ndimage import gaussian_filter1d

logger = logging.getLogger(__name__)


class DataProcessor:
    """数据处理工具类"""

    def __init__(self):
        self.config = {
            'accel_sampling_rate': 50,
            'sound_sampling_rate': 1000,
            'gps_smoothing_window': 5,
            'noise_threshold': 0.1,
            'valid_gps_accuracy': 10.0  # 米
        }

    def preprocess_sensor_data(self, raw_data: Dict) -> Dict:
        """预处理传感器数据"""
        try:
            processed_data = raw_data.copy()

            # 处理加速度数据
            if 'accel_data' in processed_data and processed_data['accel_data']:
                accel_processed = self._process_accelerometer_data(
                    processed_data['accel_data'],
                    processed_data.get('accel_sampling_rate', self.config['accel_sampling_rate'])
                )
                processed_data['accel_processed'] = accel_processed

            # 处理声学数据
            if 'sound_data' in processed_data and processed_data['sound_data']:
                sound_processed = self._process_acoustic_data(
                    processed_data['sound_data'],
                    processed_data.get('sound_frequency', self.config['sound_sampling_rate'])
                )
                processed_data['sound_processed'] = sound_processed

            # 处理GPS数据
            if all(k in processed_data for k in ['latitude', 'longitude']):
                gps_processed = self._process_gps_data(
                    processed_data['latitude'],
                    processed_data['longitude'],
                    processed_data.get('altitude', None),
                    processed_data.get('position_accuracy', None)
                )
                processed_data['gps_processed'] = gps_processed

            # 处理健康数据
            health_data = {
                'temperature': processed_data.get('temperature'),
                'heart_rate': processed_data.get('heart_rate'),
                'respiratory_rate': processed_data.get('respiratory_rate')
            }
            health_processed = self._process_health_data(health_data)
            processed_data['health_processed'] = health_processed

            # 添加时间戳
            processed_data['processed_timestamp'] = pd.Timestamp.now().isoformat()

            return processed_data

        except Exception as e:
            logger.error(f"数据预处理失败: {e}")
            return raw_data

    def _process_accelerometer_data(self, accel_data: List[float],
                                    sampling_rate: int) -> Dict:
        """处理加速度计数据"""
        try:
            if not accel_data:
                return {}

            accel_array = np.array(accel_data)

            # 1. 去除直流分量
            accel_detrended = signal.detrend(accel_array)

            # 2. 低通滤波（去除高频噪声）
            cutoff_freq = 5  # Hz
            nyquist = sampling_rate / 2
            normal_cutoff = cutoff_freq / nyquist

            # 使用巴特沃斯滤波器
            b, a = signal.butter(4, normal_cutoff, btype='low')
            accel_filtered = signal.filtfilt(b, a, accel_detrended)

            # 3. 平滑处理
            accel_smoothed = gaussian_filter1d(accel_filtered, sigma=2)

            # 4. 归一化
            accel_normalized = (accel_smoothed - np.mean(accel_smoothed)) / np.std(accel_smoothed)

            return {
                'raw': accel_array.tolist(),
                'filtered': accel_filtered.tolist(),
                'smoothed': accel_smoothed.tolist(),
                'normalized': accel_normalized.tolist(),
                'sampling_rate': sampling_rate,
                'length': len(accel_array),
                'statistics': {
                    'mean': float(np.mean(accel_array)),
                    'std': float(np.std(accel_array)),
                    'max': float(np.max(accel_array)),
                    'min': float(np.min(accel_array))
                }
            }

        except Exception as e:
            logger.error(f"加速度数据处理失败: {e}")
            return {}

    def _process_acoustic_data(self, sound_data: List[float],
                               sampling_rate: int) -> Dict:
        """处理声学数据"""
        try:
            if not sound_data:
                return {}

            sound_array = np.array(sound_data)

            # 1. 去除静音段
            threshold = self.config['noise_threshold']
            sound_active = sound_array[np.abs(sound_array) > threshold]

            if len(sound_active) == 0:
                sound_active = sound_array

            # 2. 预加重（增强高频）
            pre_emphasis = 0.97
            sound_preemphasized = np.append(
                sound_active[0],
                sound_active[1:] - pre_emphasis * sound_active[:-1]
            )

            # 3. 分帧（用于频谱分析）
            frame_length = int(0.025 * sampling_rate)  # 25ms
            frame_step = int(0.01 * sampling_rate)  # 10ms

            frames = []
            for i in range(0, len(sound_preemphasized) - frame_length, frame_step):
                frame = sound_preemphasized[i:i + frame_length]
                # 加窗
                frame_windowed = frame * np.hamming(frame_length)
                frames.append(frame_windowed)

            # 4. 计算每帧的RMS能量
            frame_energies = [np.sqrt(np.mean(frame ** 2)) for frame in frames]

            return {
                'raw': sound_array.tolist(),
                'active': sound_active.tolist(),
                'preemphasized': sound_preemphasized.tolist(),
                'frame_count': len(frames),
                'frame_energies': frame_energies,
                'sampling_rate': sampling_rate,
                'statistics': {
                    'mean': float(np.mean(sound_array)),
                    'std': float(np.std(sound_array)),
                    'max': float(np.max(sound_array)),
                    'min': float(np.min(sound_array)),
                    'energy': float(np.sum(sound_array ** 2))
                }
            }

        except Exception as e:
            logger.error(f"声学数据处理失败: {e}")
            return {}

    def _process_gps_data(self, latitude: float, longitude: float,
                          altitude: Optional[float],
                          accuracy: Optional[float]) -> Dict:
        """处理GPS数据"""
        try:
            # 验证GPS数据有效性
            is_valid = self._validate_gps_data(latitude, longitude, accuracy)

            processed_data = {
                'latitude': float(latitude),
                'longitude': float(longitude),
                'is_valid': is_valid,
                'processed_time': pd.Timestamp.now().isoformat()
            }

            if altitude is not None:
                processed_data['altitude'] = float(altitude)

            if accuracy is not None:
                processed_data['accuracy'] = float(accuracy)
                processed_data['quality'] = self._assess_gps_quality(float(accuracy))

            # 转换为UTM坐标（用于距离计算）
            utm_coords = self._latlon_to_utm(latitude, longitude)
            if utm_coords:
                processed_data['utm_easting'] = utm_coords[0]
                processed_data['utm_northing'] = utm_coords[1]
                processed_data['utm_zone'] = utm_coords[2]

            return processed_data

        except Exception as e:
            logger.error(f"GPS数据处理失败: {e}")
            return {
                'latitude': float(latitude),
                'longitude': float(longitude),
                'is_valid': False,
                'error': str(e)
            }

    def _process_health_data(self, health_data: Dict) -> Dict:
        """处理健康数据"""
        try:
            processed = {}

            # 体温处理
            if health_data.get('temperature') is not None:
                temp = float(health_data['temperature'])
                processed['temperature'] = temp
                processed['temperature_status'] = self._assess_temperature(temp)

            # 心率处理
            if health_data.get('heart_rate') is not None:
                hr = int(health_data['heart_rate'])
                processed['heart_rate'] = hr
                processed['heart_rate_status'] = self._assess_heart_rate(hr)

            # 呼吸率处理
            if health_data.get('respiratory_rate') is not None:
                rr = int(health_data['respiratory_rate'])
                processed['respiratory_rate'] = rr
                processed['respiratory_status'] = self._assess_respiratory_rate(rr)

            # 综合健康评分
            if all(k in processed for k in ['temperature_status', 'heart_rate_status', 'respiratory_status']):
                status_scores = {
                    'NORMAL': 1.0,
                    'WARNING': 0.5,
                    'ALERT': 0.0
                }

                scores = [
                    status_scores.get(processed['temperature_status'], 0),
                    status_scores.get(processed['heart_rate_status'], 0),
                    status_scores.get(processed['respiratory_status'], 0)
                ]

                processed['health_score'] = float(np.mean(scores))
                processed['health_status'] = self._determine_overall_health(processed['health_score'])

            return processed

        except Exception as e:
            logger.error(f"健康数据处理失败: {e}")
            return {}

    def _validate_gps_data(self, latitude: float, longitude: float,
                           accuracy: Optional[float]) -> bool:
        """验证GPS数据有效性"""
        try:
            # 检查坐标范围
            if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                return False

            # 检查是否为0,0（无效坐标）
            if abs(latitude) < 0.0001 and abs(longitude) < 0.0001:
                return False

            # 检查精度
            if accuracy is not None and accuracy > self.config['valid_gps_accuracy']:
                return False

            return True

        except Exception as e:
            logger.error(f"GPS数据验证失败: {e}")
            return False

    def _assess_gps_quality(self, accuracy: float) -> str:
        """评估GPS质量"""
        if accuracy <= 5.0:
            return 'EXCELLENT'
        elif accuracy <= 10.0:
            return 'GOOD'
        elif accuracy <= 20.0:
            return 'FAIR'
        else:
            return 'POOR'

    def _latlon_to_utm(self, latitude: float, longitude: float) -> Optional[Tuple[float, float, str]]:
        """经纬度转UTM坐标"""
        try:
            # 简化的转换（实际应用中应使用专门的库如pyproj）
            # 这里返回一个占位值
            zone_number = int((longitude + 180) / 6) + 1
            zone_letter = 'N' if latitude >= 0 else 'S'

            # 简单的近似转换
            k0 = 0.9996
            a = 6378137.0  # WGS84椭球体长半轴

            lat_rad = np.radians(latitude)
            lon_rad = np.radians(longitude)

            # 中央子午线
            lon0 = ((zone_number - 1) * 6 - 180 + 3) * np.pi / 180

            # 简化的UTM转换公式
            N = a / np.sqrt(1 - 0.00669438 * np.sin(lat_rad) ** 2)
            T = np.tan(lat_rad) ** 2
            C = 0.00669438 * np.cos(lat_rad) ** 2 / (1 - 0.00669438)
            A = (lon_rad - lon0) * np.cos(lat_rad)

            M = a * ((1 - 0.00669438 / 4 - 3 * 0.00669438 ** 2 / 64 - 5 * 0.00669438 ** 3 / 256) * lat_rad
                     - (3 * 0.00669438 / 8 + 3 * 0.00669438 ** 2 / 32 + 45 * 0.00669438 ** 3 / 1024) * np.sin(
                        2 * lat_rad)
                     + (15 * 0.00669438 ** 2 / 256 + 45 * 0.00669438 ** 3 / 1024) * np.sin(4 * lat_rad)
                     - (35 * 0.00669438 ** 3 / 3072) * np.sin(6 * lat_rad))

            x = k0 * N * (A + (1 - T + C) * A ** 3 / 6 + (
                        5 - 18 * T + T ** 2 + 72 * C - 58 * 0.00669438) * A ** 5 / 120) + 500000
            y = k0 * (M + N * np.tan(lat_rad) * (A ** 2 / 2 + (5 - T + 9 * C + 4 * C ** 2) * A ** 4 / 24
                                                 + (61 - 58 * T + T ** 2 + 600 * C - 330 * 0.00669438) * A ** 6 / 720))

            if latitude < 0:
                y += 10000000

            return float(x), float(y), f"{zone_number}{zone_letter}"

        except Exception as e:
            logger.error(f"UTM转换失败: {e}")
            return None

    def _assess_temperature(self, temperature: float) -> str:
        """评估体温状态"""
        # 牛的正常体温范围：38.0-39.5°C
        if 38.0 <= temperature <= 39.5:
            return 'NORMAL'
        elif 37.5 <= temperature <= 40.0:
            return 'WARNING'
        else:
            return 'ALERT'

    def _assess_heart_rate(self, heart_rate: int) -> str:
        """评估心率状态"""
        # 牛的正常心率范围：60-80 bpm
        if 60 <= heart_rate <= 80:
            return 'NORMAL'
        elif 55 <= heart_rate <= 85:
            return 'WARNING'
        else:
            return 'ALERT'

    def _assess_respiratory_rate(self, respiratory_rate: int) -> str:
        """评估呼吸率状态"""
        # 牛的正常呼吸率范围：20-40 rpm
        if 20 <= respiratory_rate <= 40:
            return 'NORMAL'
        elif 15 <= respiratory_rate <= 45:
            return 'WARNING'
        else:
            return 'ALERT'

    def _determine_overall_health(self, health_score: float) -> str:
        """确定整体健康状态"""
        if health_score >= 0.8:
            return 'HEALTHY'
        elif health_score >= 0.6:
            return 'WARNING'
        else:
            return 'ALERT'

    def batch_process(self, data_list: List[Dict]) -> List[Dict]:
        """批量处理数据"""
        try:
            processed_list = []
            for data in data_list:
                processed = self.preprocess_sensor_data(data)
                processed_list.append(processed)

            return processed_list

        except Exception as e:
            logger.error(f"批量数据处理失败: {e}")
            return data_list

    def calculate_statistics(self, data_list: List[Dict]) -> Dict:
        """计算数据统计信息"""
        try:
            if not data_list:
                return {}

            # 提取各项数据
            temperatures = []
            heart_rates = []
            locations = []

            for data in data_list:
                if 'temperature' in data:
                    temperatures.append(data['temperature'])
                if 'heart_rate' in data:
                    heart_rates.append(data['heart_rate'])
                if 'latitude' in data and 'longitude' in data:
                    locations.append((data['latitude'], data['longitude']))

            stats = {
                'total_samples': len(data_list),
                'temperature_stats': self._calculate_numeric_stats(temperatures),
                'heart_rate_stats': self._calculate_numeric_stats(heart_rates),
                'location_stats': self._calculate_location_stats(locations)
            }

            return stats

        except Exception as e:
            logger.error(f"计算统计数据失败: {e}")
            return {}

    def _calculate_numeric_stats(self, values: List[float]) -> Dict:
        """计算数值统计"""
        if not values:
            return {}

        arr = np.array(values)

        return {
            'count': len(arr),
            'mean': float(np.mean(arr)),
            'std': float(np.std(arr)),
            'min': float(np.min(arr)),
            'max': float(np.max(arr)),
            'median': float(np.median(arr)),
            'q25': float(np.percentile(arr, 25)),
            'q75': float(np.percentile(arr, 75))
        }

    def _calculate_location_stats(self, locations: List[Tuple[float, float]]) -> Dict:
        """计算位置统计"""
        if not locations:
            return {}

        lats = [loc[0] for loc in locations]
        lons = [loc[1] for loc in locations]

        # 计算中心点
        center_lat = np.mean(lats)
        center_lon = np.mean(lons)

        # 计算活动范围（边界框）
        min_lat = np.min(lats)
        max_lat = np.max(lats)
        min_lon = np.min(lons)
        max_lon = np.max(lons)

        return {
            'center': {'latitude': float(center_lat), 'longitude': float(center_lon)},
            'bounding_box': {
                'min_latitude': float(min_lat),
                'max_latitude': float(max_lat),
                'min_longitude': float(min_lon),
                'max_longitude': float(max_lon)
            },
            'location_count': len(locations)
        }