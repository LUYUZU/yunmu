# models/acoustic_model.py
import numpy as np
from sklearn.svm import SVC
from sklearn.preprocessing import StandardScaler
import joblib
import logging

logger = logging.getLogger(__name__)


class AcousticModel:
    """声学模型（SVM）"""

    def __init__(self, model_path: str = None):
        self.model = None
        self.scaler = StandardScaler()
        self.feature_names = []
        self._init_model()

        if model_path:
            self.load_model(model_path)

    def _init_model(self):
        """初始化SVM模型"""
        self.model = SVC(
            kernel='rbf',
            C=1.0,
            gamma='scale',
            probability=True,
            class_weight='balanced',
            random_state=42
        )

    def train(self, X: np.ndarray, y: np.ndarray):
        """训练模型"""
        try:
            # 标准化特征
            X_scaled = self.scaler.fit_transform(X)

            # 训练模型
            self.model.fit(X_scaled, y)

            logger.info("声学模型训练完成")

        except Exception as e:
            logger.error(f"声学模型训练失败: {e}")
            raise

    def predict(self, features: np.ndarray) -> Tuple[int, float]:
        """预测行为"""
        try:
            # 标准化特征
            if len(features.shape) == 1:
                features = features.reshape(1, -1)

            features_scaled = self.scaler.transform(features)

            # 预测
            prediction = self.model.predict(features_scaled)[0]

            # 获取置信度
            probabilities = self.model.predict_proba(features_scaled)[0]
            confidence = probabilities[prediction]

            return int(prediction), float(confidence)

        except Exception as e:
            logger.error(f"声学模型预测失败: {e}")
            return 0, 0.0

    def predict_rumination(self, acoustic_features: np.ndarray) -> Tuple[bool, float]:
        """预测反刍行为"""
        try:
            prediction, confidence = self.predict(acoustic_features)

            # 假设标签1代表反刍
            is_ruminating = (prediction == 1)

            return is_ruminating, confidence

        except Exception as e:
            logger.error(f"反刍预测失败: {e}")
            return False, 0.0

    def predict_feeding(self, acoustic_features: np.ndarray) -> Tuple[bool, float]:
        """预测采食行为"""
        try:
            prediction, confidence = self.predict(acoustic_features)

            # 假设标签2代表采食
            is_feeding = (prediction == 2)

            return is_feeding, confidence

        except Exception as e:
            logger.error(f"采食预测失败: {e}")
            return False, 0.0

    def extract_acoustic_features_importance(self) -> Dict[str, float]:
        """提取声学特征重要性"""
        try:
            if hasattr(self.model, 'coef_'):
                # SVM的系数重要性（线性核）
                importance = np.abs(self.model.coef_[0])
            elif hasattr(self.model, 'feature_importances_'):
                # 其他模型的特征重要性
                importance = self.model.feature_importances_
            else:
                # 默认返回均匀重要性
                importance = np.ones(len(self.feature_names)) / len(self.feature_names)

            # 创建特征名到重要性的映射
            feature_importance = {}
            for i, (feature_name, imp) in enumerate(zip(self.feature_names, importance)):
                if i < len(importance):
                    feature_importance[feature_name] = float(imp)

            return feature_importance

        except Exception as e:
            logger.error(f"提取特征重要性失败: {e}")
            return {}

    def save_model(self, path: str):
        """保存模型"""
        try:
            model_data = {
                'model': self.model,
                'scaler': self.scaler,
                'feature_names': self.feature_names
            }
            joblib.dump(model_data, path)
            logger.info(f"声学模型已保存到: {path}")
        except Exception as e:
            logger.error(f"保存声学模型失败: {e}")

    def load_model(self, path: str):
        """加载模型"""
        try:
            model_data = joblib.load(path)
            self.model = model_data['model']
            self.scaler = model_data['scaler']
            self.feature_names = model_data.get('feature_names', [])
            logger.info(f"声学模型已从 {path} 加载")
        except Exception as e:
            logger.error(f"加载声学模型失败: {e}")
            self._init_model()

    def get_model_info(self) -> Dict:
        """获取模型信息"""
        return {
            'model_type': 'SVM',
            'kernel': self.model.kernel if hasattr(self.model, 'kernel') else 'unknown',
            'feature_count': len(self.feature_names),
            'classes': self.model.classes_.tolist() if hasattr(self.model, 'classes_') else []
        }