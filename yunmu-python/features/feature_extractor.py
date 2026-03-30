# features/feature_extractor.py
import numpy as np
from scipy import signal, stats
from scipy.fft import fft, fftfreq
import librosa
import pandas as pd
from typing import Dict, List, Tuple
import logging

logger = logging.getLogger(__name__)


class FeatureExtractor:
    """特征提取器"""

    def __init__(self):
        self.accel_sampling_rate = 50  # Hz
        self.sound_sampling_rate = 1000  # Hz
        self.feature_dim = 64  # 固定特征维度
        self.feature_keys = self._get_fixed_feature_keys()

    def _get_fixed_feature_keys(self) -> List[str]:
        """获取固定特征键列表"""
        return [
            'mean', 'std', 'max', 'min', 'range', 'rms', 'skewness', 'kurtosis',
            'zero_crossing_rate', 'peak_count', 'peak_mean_height',
            'dominant_freq', 'low_freq_ratio',
            'jaw_period', 'jaw_frequency', 'chewing_regularity',
            'head_activity_mean', 'head_activity_std', 'head_movement_count', 'head_movement_interval_mean',
            'sound_mean', 'sound_std', 'sound_max', 'sound_min', 'sound_rms', 'sound_energy',
            'dominant_frequency', 'spectral_centroid', 'spectral_bandwidth',
            'mfcc_mean', 'mfcc_std',
            'sound_periodicity', 'periodicity_strength', 'regularity_score',
            'latitude', 'longitude', 'altitude', 'speed'
        ]

    def extract_features(self, data: Dict) -> np.ndarray:
        """提取综合特征"""
        features_dict = {}

        # 加速度特征
        if 'accel_data' in data:
            accel_features = self.extract_accelerometer_features(
                data['accel_data'],
                data.get('accel_sampling_rate', self.accel_sampling_rate)
            )
            features_dict.update(accel_features)

        # 声学特征
        if 'sound_data' in data:
            sound_features = self.extract_acoustic_features(
                data['sound_data'],
                data.get('sound_frequency', self.sound_sampling_rate)
            )
            features_dict.update(sound_features)

        # GPS 特征
        if 'location_data' in data:
            gps_features = self.extract_gps_features(data['location_data'])
            features_dict.update(gps_features)

        # 转换为固定维度的数组
        return self._to_fixed_vector(features_dict)

    def extract_accelerometer_features(self, accel_data: List[float],
                                       sampling_rate: int = 50) -> Dict[str, float]:
        """提取加速度计特征"""
        if not accel_data or len(accel_data) < 10:
            return {}

        accel_array = np.array(accel_data)

        # 基本统计特征
        features = {
            'mean': float(np.mean(accel_array)),
            'std': float(np.std(accel_array)),
            'max': float(np.max(accel_array)),
            'min': float(np.min(accel_array)),
            'range': float(np.ptp(accel_array)),
            'rms': float(np.sqrt(np.mean(accel_array ** 2))),
            'skewness': float(stats.skew(accel_array)),
            'kurtosis': float(stats.kurtosis(accel_array))
        }

        # 时域特征
        features.update(self._extract_time_domain_features(accel_array))

        # 频域特征
        features.update(self._extract_frequency_domain_features(accel_array, sampling_rate))

        # 反刍相关特征
        features.update(self._extract_rumination_features(accel_array, sampling_rate))

        # 采食相关特征
        features.update(self._extract_feeding_features(accel_array, sampling_rate))

        return features

    # features/feature_extractor.py 中修改 extract_acoustic_features 方法

    def extract_acoustic_features(self, sound_data: List[float],
                                  sampling_rate: int = 1000) -> Dict[str, float]:
        """提取声学特征"""
        if not sound_data or len(sound_data) < 100:
            return {}

        sound_array = np.array(sound_data)

        features = {
            'sound_mean': float(np.mean(sound_array)),
            'sound_std': float(np.std(sound_array)),
            'sound_max': float(np.max(sound_array)),
            'sound_min': float(np.min(sound_array)),
            'sound_rms': float(np.sqrt(np.mean(sound_array ** 2))),
            'sound_energy': float(np.sum(sound_array ** 2))
        }

        # 频谱特征
        try:
            # 确保数据长度足够用于FFT
            n = len(sound_array)
            if n >= 512:
                spectrum = np.abs(fft(sound_array))
                freqs = fftfreq(n, 1 / sampling_rate)

                # 取正频率
                positive_idx = freqs > 0
                positive_freqs = freqs[positive_idx]
                positive_spectrum = spectrum[positive_idx]

                if len(positive_spectrum) > 0:
                    # 主频
                    dominant_idx = np.argmax(positive_spectrum)
                    features['dominant_frequency'] = float(np.abs(positive_freqs[dominant_idx]))

                    # 频谱质心
                    features['spectral_centroid'] = float(np.sum(positive_freqs * positive_spectrum) /
                                                          (np.sum(positive_spectrum) + 1e-10))

                    # 频谱带宽
                    spec_centroid = features['spectral_centroid']
                    features['spectral_bandwidth'] = float(
                        np.sqrt(np.sum(((positive_freqs - spec_centroid) ** 2) * positive_spectrum) /
                                (np.sum(positive_spectrum) + 1e-10))
                    )

        except Exception as e:
            logger.warning(f"频谱分析失败: {e}")

        # MFCC特征（反刍声学特征）
        try:
            # 确保数据长度足够
            if len(sound_array) >= 512:
                # 设置合适的 n_fft
                n_fft = min(2048, len(sound_array))
                mfccs = librosa.feature.mfcc(y=sound_array, sr=sampling_rate,
                                             n_mfcc=13, n_fft=n_fft)
                features['mfcc_mean'] = float(np.mean(mfccs))
                features['mfcc_std'] = float(np.std(mfccs))
        except Exception as e:
            logger.warning(f"MFCC提取失败: {e}")

        # 周期性特征（反刍规律性）
        periodicity_features = self._extract_periodicity_features(sound_array, sampling_rate)
        features.update(periodicity_features)

        return features

    def extract_gps_features(self, location_data: Dict) -> Dict[str, float]:
        """提取GPS特征"""
        features = {}

        if 'latitude' in location_data and 'longitude' in location_data:
            features['latitude'] = float(location_data['latitude'])
            features['longitude'] = float(location_data['longitude'])

        if 'altitude' in location_data:
            features['altitude'] = float(location_data['altitude'])

        if 'speed' in location_data:
            features['speed'] = float(location_data['speed'])

        return features

    def extract_health_features(self, data: Dict) -> Dict[str, float]:
        """提取健康特征"""
        features = {}

        # 体温特征
        if 'temperature' in data:
            temp = float(data['temperature'])
            features['temperature'] = temp
            features['temp_normal'] = 1.0 if 38.0 <= temp <= 39.5 else 0.0

        # 心率特征
        if 'heart_rate' in data:
            hr = int(data['heart_rate'])
            features['heart_rate'] = hr
            features['hr_normal'] = 1.0 if 60 <= hr <= 80 else 0.0

        # 呼吸特征
        if 'respiratory_rate' in data:
            rr = int(data['respiratory_rate'])
            features['respiratory_rate'] = rr
            features['rr_normal'] = 1.0 if 20 <= rr <= 40 else 0.0

        # 行为特征
        if 'behavior_data' in data:
            behavior_features = self._extract_behavior_health_features(data['behavior_data'])
            features.update(behavior_features)

        return features

    def _extract_time_domain_features(self, data: np.ndarray) -> Dict[str, float]:
        """提取时域特征"""
        features = {}

        # 过零率
        zero_crossings = np.sum(np.diff(np.sign(data)) != 0)
        features['zero_crossing_rate'] = float(zero_crossings / len(data))

        # 峰值特征
        peaks, properties = signal.find_peaks(np.abs(data), height=0.1)
        features['peak_count'] = float(len(peaks))
        if len(peaks) > 0:
            features['peak_mean_height'] = float(np.mean(properties['peak_heights']))

        return features

    def _extract_frequency_domain_features(self, data: np.ndarray,
                                           sampling_rate: int) -> Dict[str, float]:
        """提取频域特征"""
        features = {}

        try:
            # FFT变换
            n = len(data)
            fft_data = fft(data)
            freqs = fftfreq(n, 1 / sampling_rate)

            # 取正频率
            positive_freqs = freqs[:n // 2]
            magnitude = np.abs(fft_data[:n // 2])

            if len(magnitude) > 0:
                # 主频
                dominant_idx = np.argmax(magnitude)
                features['dominant_freq'] = float(np.abs(positive_freqs[dominant_idx]))

                # 能量分布
                total_energy = np.sum(magnitude ** 2)
                low_freq_energy = np.sum(magnitude[positive_freqs < 5] ** 2)
                features['low_freq_ratio'] = float(low_freq_energy / total_energy if total_energy > 0 else 0)

        except Exception as e:
            logger.warning(f"频域特征提取失败: {e}")

        return features

    def _extract_rumination_features(self, data: np.ndarray,
                                     sampling_rate: int) -> Dict[str, float]:
        """提取反刍相关特征"""
        features = {}

        try:
            # 计算颌部运动特征（假设数据来自颈部加速度计）
            # 寻找周期性模式
            autocorr = np.correlate(data, data, mode='full')
            autocorr = autocorr[len(autocorr) // 2:]

            # 寻找主要周期
            peaks, _ = signal.find_peaks(autocorr[:200])  # 限制在合理范围内
            if len(peaks) > 1:
                period = peaks[1] - peaks[0]
                features['jaw_period'] = float(period / sampling_rate)  # 转换为秒
                features['jaw_frequency'] = float(sampling_rate / period)  # Hz

            # 计算颌部运动规律性
            if 'jaw_period' in features:
                # 计算周期性评分
                period_samples = int(features['jaw_period'] * sampling_rate)
                if period_samples > 0:
                    segments = len(data) // period_samples
                    if segments > 1:
                        segment_means = []
                        for i in range(segments):
                            segment = data[i * period_samples:(i + 1) * period_samples]
                            segment_means.append(np.mean(segment))
                        features['chewing_regularity'] = float(1 - np.std(segment_means) / np.mean(segment_means))

        except Exception as e:
            logger.warning(f"反刍特征提取失败: {e}")

        return features

    def _extract_feeding_features(self, data: np.ndarray,
                                  sampling_rate: int) -> Dict[str, float]:
        """提取采食相关特征"""
        features = {}

        try:
            # 头部运动强度（采食时头部上下运动）
            # 计算活动水平
            moving_window = 10  # 10个样本的窗口
            activity_levels = []
            for i in range(0, len(data) - moving_window, moving_window // 2):
                window = data[i:i + moving_window]
                activity = np.std(window)
                activity_levels.append(activity)

            features['head_activity_mean'] = float(np.mean(activity_levels))
            features['head_activity_std'] = float(np.std(activity_levels))

            # 采食节奏特征
            # 检测头部运动的节奏模式
            envelope = np.abs(signal.hilbert(data))
            peaks, properties = signal.find_peaks(envelope, distance=sampling_rate // 2)
            features['head_movement_count'] = float(len(peaks))

            if len(peaks) > 1:
                intervals = np.diff(peaks) / sampling_rate
                features['head_movement_interval_mean'] = float(np.mean(intervals))
                features['head_movement_interval_std'] = float(np.std(intervals))

        except Exception as e:
            logger.warning(f"采食特征提取失败: {e}")

        return features

    # features/feature_extractor.py 中修改 _extract_periodicity_features 方法

    def _extract_periodicity_features(self, sound_data: np.ndarray,
                                      sampling_rate: int) -> Dict[str, float]:
        """提取周期性特征"""
        features = {}

        try:
            # 确保数据长度足够
            if len(sound_data) < 100:
                return features

            # 自相关分析
            autocorr = np.correlate(sound_data, sound_data, mode='full')
            autocorr = autocorr[len(autocorr) // 2:]

            # 限制搜索范围
            max_lag = min(500, len(autocorr) - 1)
            autocorr = autocorr[:max_lag]

            # 寻找周期性峰值
            if len(autocorr) > 10:
                peaks, properties = signal.find_peaks(autocorr, height=0.1, distance=10)

                if len(peaks) > 1:
                    # 计算主要周期
                    main_period = peaks[1] - peaks[0]
                    if main_period > 0:
                        features['sound_periodicity'] = float(main_period / sampling_rate)

                        # 计算周期性强度
                        if 'peak_heights' in properties and len(properties['peak_heights']) > 1:
                            features['periodicity_strength'] = float(properties['peak_heights'][1] /
                                                                     (properties['peak_heights'][0] + 1e-10))

                    # 计算规律性评分
                    if len(peaks) > 2:
                        intervals = np.diff(peaks[:3])
                        if np.mean(intervals) > 0:
                            features['regularity_score'] = float(1 - np.std(intervals) / np.mean(intervals))

        except Exception as e:
            logger.warning(f"周期性特征提取失败: {e}")

        return features

    def _extract_behavior_health_features(self, behavior_data: Dict) -> Dict[str, float]:
        """提取行为健康特征"""
        features = {}

        # 反刍时长
        if 'rumination_duration' in behavior_data:
            rumination_duration = float(behavior_data['rumination_duration'])
            features['rumination_duration'] = rumination_duration
            # 正常反刍时长：每天4-8小时
            features['rumination_normal'] = 1.0 if 4 <= rumination_duration / 3600 <= 8 else 0.0

        # 采食时长
        if 'feeding_duration' in behavior_data:
            feeding_duration = float(behavior_data['feeding_duration'])
            features['feeding_duration'] = feeding_duration

        # 活动水平
        if 'activity_level' in behavior_data:
            features['activity_level'] = float(behavior_data['activity_level'])

        return features

    def _dict_to_list(self, features_dict: Dict[str, float]) -> List[float]:
        """将特征字典转换为列表"""
        return list(features_dict.values())

    def _to_fixed_vector(self, features_dict: Dict[str, float]) -> np.ndarray:
        """将特征字典转换为固定维度的向量"""
        feature_vector = []
        for key in self.feature_keys:
            value = features_dict.get(key, 0.0)
            if isinstance(value, (int, float)) and not np.isnan(value) and not np.isinf(value):
                feature_vector.append(float(value))
            else:
                feature_vector.append(0.0)
        
        # 确保维度固定
        if len(feature_vector) < self.feature_dim:
            feature_vector.extend([0.0] * (self.feature_dim - len(feature_vector)))
        elif len(feature_vector) > self.feature_dim:
            feature_vector = feature_vector[:self.feature_dim]
        
        return np.array(feature_vector)