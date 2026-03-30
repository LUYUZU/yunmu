# utils/model_utils.py
import numpy as np
import pandas as pd
import joblib
import json
import logging
from typing import Dict, List, Tuple, Any
from datetime import datetime
from pathlib import Path

logger = logging.getLogger(__name__)


class ModelUtils:
    """模型工具类"""

    def __init__(self, model_dir: str = 'models/'):
        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(parents=True, exist_ok=True)

        # 模型元数据存储
        self.metadata_file = self.model_dir / 'model_metadata.json'
        self._load_metadata()

    def _load_metadata(self):
        """加载模型元数据"""
        try:
            if self.metadata_file.exists():
                with open(self.metadata_file, 'r') as f:
                    self.metadata = json.load(f)
            else:
                self.metadata = {}
        except Exception as e:
            logger.error(f"加载模型元数据失败: {e}")
            self.metadata = {}

    def _save_metadata(self):
        """保存模型元数据"""
        try:
            with open(self.metadata_file, 'w') as f:
                json.dump(self.metadata, f, indent=2)
        except Exception as e:
            logger.error(f"保存模型元数据失败: {e}")

    def save_model(self, model: Any, model_name: str,
                   model_type: str, metadata: Dict = None):
        """保存模型"""
        try:
            # 创建时间戳
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')

            # 模型文件名
            model_filename = f"{model_name}_{timestamp}.pkl"
            model_path = self.model_dir / model_filename

            # 保存模型
            joblib.dump(model, model_path)
            logger.info(f"模型已保存到: {model_path}")

            # 更新元数据
            model_info = {
                'model_name': model_name,
                'model_type': model_type,
                'file_path': str(model_path),
                'timestamp': timestamp,
                'save_time': datetime.now().isoformat()
            }

            if metadata:
                model_info.update(metadata)

            if model_name not in self.metadata:
                self.metadata[model_name] = []

            self.metadata[model_name].append(model_info)

            # 只保留最近5个版本
            if len(self.metadata[model_name]) > 5:
                self.metadata[model_name] = self.metadata[model_name][-5:]

            self._save_metadata()

            return str(model_path)

        except Exception as e:
            logger.error(f"保存模型失败: {e}")
            return None

    def load_model(self, model_name: str, version: int = -1):
        """加载模型"""
        try:
            if model_name not in self.metadata or not self.metadata[model_name]:
                logger.error(f"模型 {model_name} 不存在")
                return None

            # 获取指定版本的模型信息
            model_info = self.metadata[model_name][version]
            model_path = Path(model_info['file_path'])

            if not model_path.exists():
                logger.error(f"模型文件不存在: {model_path}")
                return None

            # 加载模型
            model = joblib.load(model_path)
            logger.info(f"模型已从 {model_path} 加载")

            return model

        except Exception as e:
            logger.error(f"加载模型失败: {e}")
            return None

    def get_model_info(self, model_name: str = None) -> Dict:
        """获取模型信息"""
        try:
            if model_name:
                if model_name in self.metadata:
                    return {
                        'model_name': model_name,
                        'versions': self.metadata[model_name],
                        'latest_version': self.metadata[model_name][-1] if self.metadata[model_name] else None
                    }
                else:
                    return {'error': f'模型 {model_name} 不存在'}
            else:
                # 返回所有模型信息
                return {
                    'models': list(self.metadata.keys()),
                    'total_models': len(self.metadata),
                    'metadata': self.metadata
                }

        except Exception as e:
            logger.error(f"获取模型信息失败: {e}")
            return {'error': str(e)}

    def evaluate_model(self, model: Any, X_test: np.ndarray,
                       y_test: np.ndarray) -> Dict[str, Any]:
        """评估模型性能"""
        try:
            from sklearn.metrics import (
                accuracy_score, precision_score, recall_score,
                f1_score, confusion_matrix, classification_report
            )

            # 预测
            y_pred = model.predict(X_test)

            # 计算指标
            metrics = {
                'accuracy': float(accuracy_score(y_test, y_pred)),
                'precision': float(precision_score(y_test, y_pred, average='weighted')),
                'recall': float(recall_score(y_test, y_pred, average='weighted')),
                'f1_score': float(f1_score(y_test, y_pred, average='weighted'))
            }

            # 混淆矩阵
            cm = confusion_matrix(y_test, y_pred)
            metrics['confusion_matrix'] = cm.tolist()

            # 分类报告
            if hasattr(model, 'classes_'):
                class_names = model.classes_
                report = classification_report(y_test, y_pred,
                                               target_names=[str(c) for c in class_names],
                                               output_dict=True)
                metrics['classification_report'] = report

            # 计算每个类别的指标
            unique_classes = np.unique(y_test)
            class_metrics = {}

            for class_label in unique_classes:
                # 二分类指标
                y_test_binary = (y_test == class_label).astype(int)
                y_pred_binary = (y_pred == class_label).astype(int)

                if len(np.unique(y_test_binary)) > 1:  # 确保有正负样本
                    class_metrics[str(class_label)] = {
                        'precision': float(precision_score(y_test_binary, y_pred_binary)),
                        'recall': float(recall_score(y_test_binary, y_pred_binary)),
                        'f1': float(f1_score(y_test_binary, y_pred_binary))
                    }

            metrics['class_metrics'] = class_metrics

            return metrics

        except Exception as e:
            logger.error(f"模型评估失败: {e}")
            return {'error': str(e)}

    def cross_validate(self, model: Any, X: np.ndarray, y: np.ndarray,
                       cv: int = 5) -> Dict[str, Any]:
        """交叉验证"""
        try:
            from sklearn.model_selection import cross_val_score, StratifiedKFold

            # 使用分层K折交叉验证
            skf = StratifiedKFold(n_splits=cv, shuffle=True, random_state=42)

            # 交叉验证得分
            scores = cross_val_score(model, X, y, cv=skf, scoring='accuracy')

            cv_results = {
                'cv_folds': cv,
                'accuracy_scores': scores.tolist(),
                'mean_accuracy': float(np.mean(scores)),
                'std_accuracy': float(np.std(scores)),
                'min_accuracy': float(np.min(scores)),
                'max_accuracy': float(np.max(scores))
            }

            # 计算其他指标的交叉验证
            scoring_metrics = ['precision_weighted', 'recall_weighted', 'f1_weighted']
            for metric in scoring_metrics:
                metric_scores = cross_val_score(model, X, y, cv=skf, scoring=metric)
                cv_results[f'{metric}_scores'] = metric_scores.tolist()
                cv_results[f'mean_{metric}'] = float(np.mean(metric_scores))
                cv_results[f'std_{metric}'] = float(np.std(metric_scores))

            return cv_results

        except Exception as e:
            logger.error(f"交叉验证失败: {e}")
            return {'error': str(e)}

    def hyperparameter_tuning(self, model_class, param_grid: Dict,
                              X_train: np.ndarray, y_train: np.ndarray,
                              cv: int = 3) -> Dict[str, Any]:
        """超参数调优"""
        try:
            from sklearn.model_selection import GridSearchCV

            # 创建网格搜索
            grid_search = GridSearchCV(
                estimator=model_class,
                param_grid=param_grid,
                cv=cv,
                scoring='accuracy',
                n_jobs=-1,
                verbose=1
            )

            # 执行网格搜索
            grid_search.fit(X_train, y_train)

            # 收集结果
            results = {
                'best_params': grid_search.best_params_,
                'best_score': float(grid_search.best_score_),
                'best_estimator': str(grid_search.best_estimator_),
                'cv_results': []
            }

            # 详细结果
            for i, (params, mean_score, std_score) in enumerate(
                    zip(grid_search.cv_results_['params'],
                        grid_search.cv_results_['mean_test_score'],
                        grid_search.cv_results_['std_test_score'])):
                results['cv_results'].append({
                    'params': params,
                    'mean_score': float(mean_score),
                    'std_score': float(std_score),
                    'rank': int(grid_search.cv_results_['rank_test_score'][i])
                })

            return results

        except Exception as e:
            logger.error(f"超参数调优失败: {e}")
            return {'error': str(e)}

    def save_evaluation_results(self, results: Dict, filename: str):
        """保存评估结果"""
        try:
            results_dir = self.model_dir / 'evaluations'
            results_dir.mkdir(exist_ok=True)

            # 添加时间戳
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename_with_timestamp = f"{filename}_{timestamp}.json"

            filepath = results_dir / filename_with_timestamp

            # 保存结果
            with open(filepath, 'w') as f:
                json.dump(results, f, indent=2)

            logger.info(f"评估结果已保存到: {filepath}")
            return str(filepath)

        except Exception as e:
            logger.error(f"保存评估结果失败: {e}")
            return None

    def create_model_report(self, model: Any, X_test: np.ndarray,
                            y_test: np.ndarray, model_name: str) -> Dict[str, Any]:
        """创建模型报告"""
        try:
            # 评估模型
            eval_results = self.evaluate_model(model, X_test, y_test)

            # 获取模型信息
            model_info = self._extract_model_info(model)

            # 创建报告
            report = {
                'model_name': model_name,
                'model_info': model_info,
                'evaluation': eval_results,
                'timestamp': datetime.now().isoformat(),
                'test_set_size': len(X_test),
                'feature_count': X_test.shape[1] if len(X_test.shape) > 1 else 1
            }

            # 如果模型有特征重要性，添加到报告
            if hasattr(model, 'feature_importances_'):
                report['feature_importance'] = model.feature_importances_.tolist()

            # 保存报告
            report_path = self.save_evaluation_results(report, f"{model_name}_report")
            if report_path:
                report['report_path'] = report_path

            return report

        except Exception as e:
            logger.error(f"创建模型报告失败: {e}")
            return {'error': str(e)}

    def _extract_model_info(self, model: Any) -> Dict[str, Any]:
        """提取模型信息"""
        model_info = {
            'model_type': type(model).__name__,
            'model_params': str(model.get_params()) if hasattr(model, 'get_params') else {}
        }

        # 添加特定模型的信息
        if hasattr(model, 'n_estimators'):
            model_info['n_estimators'] = model.n_estimators

        if hasattr(model, 'max_depth'):
            model_info['max_depth'] = model.max_depth

        if hasattr(model, 'learning_rate'):
            model_info['learning_rate'] = model.learning_rate

        if hasattr(model, 'kernel'):
            model_info['kernel'] = model.kernel

        if hasattr(model, 'C'):
            model_info['C'] = model.C

        return model_info

    def compare_models(self, models: Dict[str, Any],
                       X_test: np.ndarray, y_test: np.ndarray) -> Dict[str, Any]:
        """比较多个模型"""
        try:
            comparison_results = {
                'models': {},
                'summary': {},
                'best_model': None,
                'best_score': 0.0
            }

            for model_name, model in models.items():
                # 评估模型
                eval_results = self.evaluate_model(model, X_test, y_test)

                comparison_results['models'][model_name] = {
                    'accuracy': eval_results.get('accuracy', 0.0),
                    'precision': eval_results.get('precision', 0.0),
                    'recall': eval_results.get('recall', 0.0),
                    'f1_score': eval_results.get('f1_score', 0.0)
                }

                # 更新最佳模型
                if eval_results.get('accuracy', 0.0) > comparison_results['best_score']:
                    comparison_results['best_score'] = eval_results['accuracy']
                    comparison_results['best_model'] = model_name

            # 计算统计摘要
            accuracies = [results['accuracy'] for results in comparison_results['models'].values()]
            if accuracies:
                comparison_results['summary'] = {
                    'mean_accuracy': float(np.mean(accuracies)),
                    'std_accuracy': float(np.std(accuracies)),
                    'min_accuracy': float(np.min(accuracies)),
                    'max_accuracy': float(np.max(accuracies)),
                    'model_count': len(models)
                }

            # 保存比较结果
            comparison_path = self.save_evaluation_results(
                comparison_results,
                'model_comparison'
            )
            comparison_results['comparison_path'] = comparison_path

            return comparison_results

        except Exception as e:
            logger.error(f"模型比较失败: {e}")
            return {'error': str(e)}