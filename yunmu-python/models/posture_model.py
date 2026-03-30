# models/posture_model.py
import numpy as np
from scipy import signal
from scipy.ndimage import gaussian_filter1d
import pywt
from typing import Dict, List, Tuple, Any
import logging
import tensorflow as tf
from tensorflow.keras import layers, models, regularizers

logger = logging.getLogger(__name__)


class CMSEBlock(layers.Layer):
    """跨模态注意力机制模块"""

    def __init__(self, filters, reduction=16):
        super(CMSEBlock, self).__init__()
        self.filters = filters
        self.reduction = reduction
        self.global_avg_pool = layers.GlobalAveragePooling1D()
        self.global_max_pool = layers.GlobalMaxPooling1D()

        self.dense1 = layers.Dense(filters // reduction, activation='relu')
        self.dense2 = layers.Dense(filters, activation='sigmoid')

    def call(self, inputs):
        # 输入形状: (batch, time, features)
        avg_pool = self.global_avg_pool(inputs)
        max_pool = self.global_max_pool(inputs)

        # 跨模态注意力
        avg_weight = self.dense2(self.dense1(avg_pool))
        max_weight = self.dense2(self.dense1(max_pool))

        # 融合权重
        attention_weight = (avg_weight + max_weight) / 2
        attention_weight = tf.expand_dims(attention_weight, axis=1)

        return inputs * attention_weight


class TemporalStream(layers.Layer):
    """深度时序流：1D-CNN + 跨模态注意力"""

    def __init__(self, filters=64, kernel_size=5):
        super(TemporalStream, self).__init__()
        self.conv1 = layers.Conv1D(filters, kernel_size, padding='same', activation='relu')
        self.cmse = CMSEBlock(filters)
        self.conv2 = layers.Conv1D(filters * 2, kernel_size, padding='same', activation='relu')
        self.conv3 = layers.Conv1D(filters * 4, kernel_size, padding='same', activation='relu')
        self.global_pool = layers.GlobalAveragePooling1D()

    def call(self, inputs):
        x = self.conv1(inputs)
        x = self.cmse(x)
        x = self.conv2(x)
        x = self.conv3(x)
        x = self.global_pool(x)
        return x


class StatisticalStream(layers.Layer):
    """统计物理流：时频域统计特征 + 轴间耦合特征"""

    def __init__(self, feature_dim=128):
        super(StatisticalStream, self).__init__()
        self.feature_dim = feature_dim
        self.dense1 = layers.Dense(feature_dim // 2, activation='relu')
        self.dense2 = layers.Dense(feature_dim, activation='relu')

    def call(self, inputs):
        # inputs形状: (batch, time, 6) - acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z
        # 提取统计特征
        mean = tf.reduce_mean(inputs, axis=1)
        std = tf.math.reduce_std(inputs, axis=1)
        max_val = tf.reduce_max(inputs, axis=1)
        min_val = tf.reduce_min(inputs, axis=1)

        # 轴间耦合特征（加速度和陀螺仪的相关性）
        acc = inputs[:, :, :3]
        gyro = inputs[:, :, 3:]
        acc_gyro_corr = tf.abs(tf.reduce_mean(acc * gyro, axis=1))

        # 频域特征（简化版，通过FFT）
        fft_features = tf.abs(tf.signal.fft(tf.cast(inputs, tf.complex64)))
        fft_mean = tf.reduce_mean(fft_features, axis=1)
        fft_std = tf.math.reduce_std(fft_features, axis=1)

        # 拼接所有统计特征
        stats = tf.concat([mean, std, max_val, min_val, acc_gyro_corr, fft_mean, fft_std], axis=1)

        x = self.dense1(stats)
        x = self.dense2(x)
        return x


class DSFFSTA6D(models.Model):
    """DSFF-STA-6D模型：双流结构"""

    def __init__(self, num_classes=5, temporal_filters=64, statistical_dim=128):
        super(DSFFSTA6D, self).__init__()
        self.temporal_stream = TemporalStream(temporal_filters)
        self.statistical_stream = StatisticalStream(statistical_dim)

        # 特征融合层
        fusion_dim = temporal_filters * 4 + statistical_dim
        self.fusion = layers.Concatenate()
        self.dense1 = layers.Dense(fusion_dim // 2, activation='relu')
        self.dropout = layers.Dropout(0.3)
        self.dense2 = layers.Dense(fusion_dim // 4, activation='relu')
        self.output_layer = layers.Dense(num_classes, activation='softmax')

        # 用于Focal Loss的alpha参数
        self.alpha = 0.25
        self.gamma = 2.0

    def call(self, inputs):
        # 深度时序特征
        temporal_features = self.temporal_stream(inputs)

        # 统计物理特征
        statistical_features = self.statistical_stream(inputs)

        # 特征融合
        fused = self.fusion([temporal_features, statistical_features])

        x = self.dense1(fused)
        x = self.dropout(x)
        x = self.dense2(x)
        output = self.output_layer(x)

        return output

    def focal_loss(self, y_true, y_pred):
        """Focal Loss处理类别不平衡"""
        epsilon = tf.keras.backend.epsilon()
        y_pred = tf.clip_by_value(y_pred, epsilon, 1. - epsilon)

        cross_entropy = -y_true * tf.math.log(y_pred)

        # 计算focal loss
        weight = tf.pow(1 - y_pred, self.gamma)
        focal_loss = self.alpha * weight * cross_entropy

        return tf.reduce_mean(focal_loss, axis=1)


class PostureClassifier:
    """姿态分类器 - 基于DSFF-STA-6D模型"""

    def __init__(self, model_path: str = None):
        self.posture_types = ['standing', 'lying', 'walking', 'feeding', 'ruminating']
        self.num_classes = len(self.posture_types)
        self.window_size = 4500  # 90秒 * 50Hz采样率
        self.sampling_rate = 50

        # 初始化模型
        self.model = self._build_model()

        if model_path:
            self.load_model(model_path)

    def _build_model(self) -> DSFFSTA6D:
        """构建DSFF-STA-6D模型"""
        model = DSFFSTA6D(num_classes=self.num_classes)

        # 编译模型
        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
            loss=model.focal_loss,
            metrics=['accuracy']
        )

        return model

    def preprocess_signal(self, accel_x: np.ndarray, accel_y: np.ndarray,
                          accel_z: np.ndarray, gyro_x: np.ndarray = None,
                          gyro_y: np.ndarray = None, gyro_z: np.ndarray = None) -> np.ndarray:
        """数据预处理：小波去噪 + 滑动窗口"""
        # 双通道自适应小波去噪
        accel_denoised = self._wavelet_denoise(accel_x, accel_y, accel_z)

        if gyro_x is not None:
            gyro_denoised = self._wavelet_denoise(gyro_x, gyro_y, gyro_z)
            combined = np.column_stack([accel_denoised, gyro_denoised])
        else:
            combined = accel_denoised

        # Z-Score归一化
        mean = np.mean(combined, axis=0)
        std = np.std(combined, axis=0)
        std[std == 0] = 1
        combined_normalized = (combined - mean) / std

        return combined_normalized

    def _wavelet_denoise(self, x: np.ndarray, y: np.ndarray, z: np.ndarray) -> np.ndarray:
        """小波去噪：db4小波基，3层分解，软阈值"""
        denoised_signals = []

        for signal in [x, y, z]:
            # 小波分解
            coeffs = pywt.wavedec(signal, 'db4', level=3)

            # 软阈值去噪
            sigma = np.median(np.abs(coeffs[-1])) / 0.6745
            threshold = sigma * np.sqrt(2 * np.log(len(signal)))

            coeffs_thresholded = list(coeffs)
            for i in range(1, len(coeffs_thresholded)):
                coeffs_thresholded[i] = pywt.threshold(coeffs_thresholded[i], threshold, mode='soft')

            # 重构信号
            denoised = pywt.waverec(coeffs_thresholded, 'db4')

            # 确保长度一致
            if len(denoised) > len(signal):
                denoised = denoised[:len(signal)]

            denoised_signals.append(denoised)

        return np.column_stack(denoised_signals)

    def create_windows(self, data: np.ndarray, overlap: float = 0.5) -> np.ndarray:
        """创建滑动窗口"""
        step = int(self.window_size * (1 - overlap))
        windows = []

        for start in range(0, len(data) - self.window_size + 1, step):
            window = data[start:start + self.window_size]
            windows.append(window)

        return np.array(windows)

    def data_augmentation(self, windows: np.ndarray) -> np.ndarray:
        """数据增强：高斯噪声、时间扭曲、通道丢弃"""
        augmented = []

        for window in windows:
            # 原始窗口
            augmented.append(window)

            # 高斯噪声
            noise = np.random.normal(0, 0.05, window.shape)
            augmented.append(window + noise)

            # 时间扭曲（拉伸/压缩）
            if len(window) > 10:
                indices = np.linspace(0, len(window) - 1, len(window)).astype(int)
                time_warped = window[indices]
                augmented.append(time_warped)

            # 通道丢弃（随机丢弃一个通道）
            channel_mask = np.random.choice([0, 1, 2], size=window.shape[1], replace=True)
            channel_mask[channel_mask == 1] = 0
            channel_dropped = window * channel_mask
            augmented.append(channel_dropped)

        return np.array(augmented)

    def predict_posture(self, accel_x: float, accel_y: float, accel_z: float,
                        gyro_x: float = 0, gyro_y: float = 0, gyro_z: float = 0,
                        timestamp: float = None) -> Dict[str, Any]:
        """姿态识别"""
        try:
            # 构建输入序列
            accel_seq = np.ones((self.window_size, 3)) * np.array([accel_x, accel_y, accel_z])
            gyro_seq = np.ones((self.window_size, 3)) * np.array([gyro_x, gyro_y, gyro_z])

            # 预处理
            processed = self.preprocess_signal(
                accel_seq[:, 0], accel_seq[:, 1], accel_seq[:, 2],
                gyro_seq[:, 0], gyro_seq[:, 1], gyro_seq[:, 2]
            )

            # 预测
            processed_batch = np.expand_dims(processed, axis=0)
            predictions = self.model.predict(processed_batch, verbose=0)

            posture_idx = np.argmax(predictions[0])
            confidence = float(predictions[0][posture_idx])

            return {
                'success': True,
                'posture_type': self.posture_types[posture_idx],
                'confidence': confidence,
                'probabilities': predictions[0].tolist(),
                'features': {
                    'accel': [accel_x, accel_y, accel_z],
                    'gyro': [gyro_x, gyro_y, gyro_z]
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
                data.get('accel_x', 0),
                data.get('accel_y', 0),
                data.get('accel_z', 9.8),
                data.get('gyro_x', 0),
                data.get('gyro_y', 0),
                data.get('gyro_z', 0),
                data.get('timestamp')
            )
            results.append(result)
        return results

    def save_model(self, path: str):
        """保存模型"""
        self.model.save(path)
        logger.info(f"模型已保存到: {path}")

    def load_model(self, path: str):
        """加载模型"""
        self.model = tf.keras.models.load_model(
            path,
            custom_objects={
                'DSFFSTA6D': DSFFSTA6D,
                'CMSEBlock': CMSEBlock,
                'TemporalStream': TemporalStream,
                'StatisticalStream': StatisticalStream
            }
        )
        logger.info(f"模型已从 {path} 加载")

    def prune_model(self, pruning_rate: float = 0.4):
        """结构化剪枝"""
        import tensorflow_model_optimization as tfmot

        prune_low_magnitude = tfmot.sparsity.keras.prune_low_magnitude

        pruning_params = {
            'pruning_schedule': tfmot.sparsity.keras.PolynomialDecay(
                initial_sparsity=0.0,
                final_sparsity=pruning_rate,
                begin_step=0,
                end_step=1000
            )
        }

        self.model = prune_low_magnitude(self.model, **pruning_params)
        logger.info(f"模型剪枝完成，剪枝率: {pruning_rate * 100}%")

    def quantize_model(self):
        """量化感知训练"""
        converter = tf.lite.TFLiteConverter.from_keras_model(self.model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.int8]

        tflite_model = converter.convert()

        # 保存量化模型
        with open('models/trained/posture_model_quantized.tflite', 'wb') as f:
            f.write(tflite_model)

        logger.info("模型量化完成")
        return tflite_model