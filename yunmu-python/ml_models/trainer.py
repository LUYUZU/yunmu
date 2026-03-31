# ml_models/trainer.py - 机器学习训练模块
"""
使用 scikit-learn 训练牛羊姿态识别模型
支持多种算法：RandomForest, GradientBoosting, SVM, MLP
"""
import os
import json
import joblib
import numpy as np
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Tuple, Optional

# ML 库
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.svm import SVC
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import (
    classification_report, confusion_matrix, 
    accuracy_score, precision_recall_fscore_support
)


class PostureTrainer:
    """姿态识别模型训练器"""
    
    POSTURE_LABELS = ['standing', 'lying', 'walking', 'feeding', 'running']
    POSTURE_MAP = {p: i for i, p in enumerate(POSTURE_LABELS)}
    REVERSE_MAP = {i: p for p, i in POSTURE_MAP.items()}
    
    def __init__(self, model_dir: str = None):
        if model_dir is None:
            base_dir = Path(__file__).parent.parent
            model_dir = base_dir / "ml_models" / "saved_models"
        
        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(exist_ok=True)
        
        self.scaler = StandardScaler()
        self.label_encoder = LabelEncoder()
        self.label_encoder.fit(self.POSTURE_LABELS)
        
        self.model = None
        self.model_type = None
        self.feature_names = [
            'accel_magnitude', 'accel_xy', 'accel_x', 'accel_y', 'accel_z',
            'accel_angle_y', 'accel_angle_z',
            'gyro_magnitude', 'gyro_x', 'gyro_y', 'gyro_z',
            'accel_gyro_ratio', 'activity_index'
        ]
    
    def prepare_features(self, X: np.ndarray) -> np.ndarray:
        """特征预处理"""
        # 标准化
        X_scaled = self.scaler.fit_transform(X)
        return X_scaled
    
    def train(self, X: np.ndarray, y: np.ndarray, 
              model_type: str = 'random_forest',
              test_size: float = 0.2,
              **kwargs) -> Dict:
        """
        训练模型
        
        Args:
            X: 特征矩阵 (n_samples, n_features)
            y: 标签数组
            model_type: 模型类型 ('random_forest', 'gradient_boosting', 'svm', 'mlp')
            test_size: 测试集比例
        
        Returns:
            训练结果字典
        """
        # 数据分割
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=42, stratify=y
        )
        
        # 特征标准化
        X_train_scaled = self.scaler.fit_transform(X_train)
        X_test_scaled = self.scaler.transform(X_test)
        
        # 选择模型
        self.model_type = model_type
        
        if model_type == 'random_forest':
            self.model = RandomForestClassifier(
                n_estimators=kwargs.get('n_estimators', 100),
                max_depth=kwargs.get('max_depth', 15),
                min_samples_split=kwargs.get('min_samples_split', 2),
                random_state=42,
                n_jobs=-1
            )
        elif model_type == 'gradient_boosting':
            self.model = GradientBoostingClassifier(
                n_estimators=kwargs.get('n_estimators', 100),
                max_depth=kwargs.get('max_depth', 5),
                learning_rate=kwargs.get('learning_rate', 0.1),
                random_state=42
            )
        elif model_type == 'svm':
            self.model = SVC(
                kernel=kwargs.get('kernel', 'rbf'),
                C=kwargs.get('C', 1.0),
                gamma=kwargs.get('gamma', 'scale'),
                probability=True,
                random_state=42
            )
        elif model_type == 'mlp':
            self.model = MLPClassifier(
                hidden_layer_sizes=kwargs.get('hidden_layers', (64, 32)),
                activation=kwargs.get('activation', 'relu'),
                max_iter=kwargs.get('max_iter', 500),
                random_state=42,
                early_stopping=True
            )
        else:
            raise ValueError(f"不支持的模型类型: {model_type}")
        
        # 训练
        print(f"正在训练 {model_type} 模型...")
        self.model.fit(X_train_scaled, y_train)
        
        # 评估
        y_pred = self.model.predict(X_test_scaled)
        
        accuracy = accuracy_score(y_test, y_pred)
        
        # 交叉验证
        X_all_scaled = self.scaler.fit_transform(X)
        cv_scores = cross_val_score(self.model, X_all_scaled, y, cv=5)
        
        # 分类报告
        report = classification_report(
            y_test, y_pred, 
            target_names=self.POSTURE_LABELS,
            output_dict=True
        )
        
        # 混淆矩阵
        cm = confusion_matrix(y_test, y_pred)
        
        results = {
            'model_type': model_type,
            'accuracy': float(accuracy),
            'cv_mean': float(cv_scores.mean()),
            'cv_std': float(cv_scores.std()),
            'classification_report': report,
            'confusion_matrix': cm.tolist(),
            'train_samples': len(X_train),
            'test_samples': len(X_test)
        }
        
        print(f"\n训练完成!")
        print(f"测试集准确率: {accuracy:.4f}")
        print(f"5折交叉验证: {cv_scores.mean():.4f} (+/- {cv_scores.std()*2:.4f})")
        
        return results
    
    def save_model(self, model_name: str = None) -> str:
        """保存模型"""
        if model_name is None:
            model_name = f"posture_{self.model_type}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        
        model_path = self.model_dir / f"{model_name}.pkl"
        scaler_path = self.model_dir / f"{model_name}_scaler.pkl"
        
        joblib.dump(self.model, model_path)
        joblib.dump(self.scaler, scaler_path)
        
        # 保存模型信息
        info = {
            'model_type': self.model_type,
            'feature_names': self.feature_names,
            'posture_labels': self.POSTURE_LABELS,
            'trained_at': datetime.now().isoformat()
        }
        
        info_path = self.model_dir / f"{model_name}_info.json"
        with open(info_path, 'w', encoding='utf-8') as f:
            json.dump(info, f, indent=2)
        
        print(f"模型已保存: {model_path}")
        return str(model_path)
    
    def load_model(self, model_name: str):
        """加载模型"""
        model_path = self.model_dir / f"{model_name}.pkl"
        scaler_path = self.model_dir / f"{model_name}_scaler.pkl"
        
        self.model = joblib.load(model_path)
        self.scaler = joblib.load(scaler_path)
        
        info_path = self.model_dir / f"{model_name}_info.json"
        if info_path.exists():
            with open(info_path, 'r', encoding='utf-8') as f:
                info = json.load(f)
                self.model_type = info.get('model_type', 'unknown')
        
        print(f"模型已加载: {model_path}")
    
    def predict(self, X: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """
        预测姿态
        
        Args:
            X: 特征矩阵 (n_samples, n_features) 或 (n_features,)
        
        Returns:
            (预测标签, 预测概率)
        """
        if self.model is None:
            raise ValueError("模型未训练或未加载")
        
        # 处理单样本
        if X.ndim == 1:
            X = X.reshape(1, -1)
        
        X_scaled = self.scaler.transform(X)
        
        predictions = self.model.predict(X_scaled)
        probabilities = self.model.predict_proba(X_scaled)
        
        # 转换回标签
        pred_labels = [self.REVERSE_MAP[p] for p in predictions]
        
        return np.array(pred_labels), probabilities


class StepAnomalyTrainer:
    """步数异常检测训练器（使用 Isolation Forest）"""
    
    def __init__(self, model_dir: str = None):
        if model_dir is None:
            base_dir = Path(__file__).parent.parent
            model_dir = base_dir / "ml_models" / "saved_models"
        
        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(exist_ok=True)
        
        self.scaler = StandardScaler()
        self.model = None
        
    def prepare_features(self, step_history: List[Dict]) -> np.ndarray:
        """从步数历史提取特征"""
        if len(step_history) < 10:
            return None
        
        features = []
        
        for i in range(10, len(step_history)):
            window = step_history[i-10:i]
            steps = [w['steps'] for w in window]
            
            feature = [
                np.mean(steps),      # 平均步数
                np.std(steps),       # 步数标准差
                np.max(steps),       # 最大步数
                np.min(steps),       # 最小步数
                steps[-1] - steps[0],  # 变化量
                np.median(steps)     # 中位数
            ]
            features.append(feature)
        
        return np.array(features)
    
    def train(self, X: np.ndarray, contamination: float = 0.1) -> Dict:
        """训练异常检测模型"""
        from sklearn.ensemble import IsolationForest
        
        X_scaled = self.scaler.fit_transform(X)
        
        self.model = IsolationForest(
            n_estimators=100,
            contamination=contamination,
            random_state=42
        )
        
        self.model.fit(X_scaled)
        
        # 评估
        scores = self.model.score_samples(X_scaled)
        
        return {
            'contamination': contamination,
            'mean_score': float(np.mean(scores)),
            'score_threshold': float(np.percentile(scores, contamination * 100))
        }
    
    def save_model(self, model_name: str = None):
        """保存模型"""
        if model_name is None:
            model_name = f"step_anomaly_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        
        model_path = self.model_dir / f"{model_name}.pkl"
        scaler_path = self.model_dir / f"{model_name}_scaler.pkl"
        
        joblib.dump(self.model, model_path)
        joblib.dump(self.scaler, scaler_path)
        
        print(f"异常检测模型已保存: {model_path}")
    
    def detect_anomaly(self, features: np.ndarray) -> Dict:
        """检测异常"""
        if self.model is None:
            raise ValueError("模型未训练或未加载")
        
        X_scaled = self.scaler.transform(features.reshape(1, -1))
        
        is_anomaly = self.model.predict(X_scaled)[0] == -1
        score = self.model.score_samples(X_scaled)[0]
        
        return {
            'is_anomaly': bool(is_anomaly),
            'anomaly_score': float(score),
            'severity': 'critical' if score < -0.5 else 'warning' if score < -0.2 else 'normal'
        }


# 便捷训练函数
def quick_train(data_collector, model_type: str = 'random_forest') -> Tuple:
    """
    快速训练接口
    
    Returns:
        (trainer, results)
    """
    X, y = data_collector.get_training_data()
    
    if X is None:
        raise ValueError("没有训练数据，请先收集数据")
    
    trainer = PostureTrainer()
    results = trainer.train(X, y, model_type=model_type)
    trainer.save_model(f"posture_{model_type}")
    
    return trainer, results


# 测试代码
if __name__ == '__main__':
    from data_collector import BehaviorDataCollector
    
    print("=" * 50)
    print("牛羊姿态识别模型训练")
    print("=" * 50)
    
    # 1. 收集/生成数据
    print("\n[1/3] 准备训练数据...")
    collector = BehaviorDataCollector()
    collector.generate_synthetic_data(num_samples_per_class=300)
    
    X, y = collector.get_training_data()
    print(f"数据准备完成: {X.shape[0]} 样本, {X.shape[1]} 特征")
    
    # 2. 训练模型
    print("\n[2/3] 训练模型...")
    trainer = PostureTrainer()
    
    # 测试不同模型
    for model_type in ['random_forest', 'gradient_boosting', 'mlp']:
        print(f"\n--- {model_type} ---")
        trainer_new = PostureTrainer()
        results = trainer_new.train(X, y, model_type=model_type)
        print(f"准确率: {results['accuracy']:.4f}")
    
    # 3. 保存最佳模型
    print("\n[3/3] 保存模型...")
    trainer.save_model("posture_best")
    
    print("\n训练完成!")
