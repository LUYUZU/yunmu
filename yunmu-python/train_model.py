#!/usr/bin/env python
# train_model.py - 一键训练脚本
"""
使用方法:
    python train_model.py                 # 使用默认参数训练
    python train_model.py --samples 1000  # 自定义样本数
    python train_model.py --model svm     # 使用SVM模型
"""
import sys
import os
from pathlib import Path

# 添加项目路径
sys.path.append(str(Path(__file__).parent))

def main():
    print("=" * 60)
    print("云牧智感 - 牛羊姿态识别模型训练")
    print("=" * 60)
    
    # 解析参数
    import argparse
    parser = argparse.ArgumentParser(description='训练姿态识别模型')
    parser.add_argument('--samples', type=int, default=500, help='每类样本数量')
    parser.add_argument('--model', type=str, default='random_forest', 
                       choices=['random_forest', 'gradient_boosting', 'svm', 'mlp'],
                       help='模型类型')
    parser.add_argument('--test-size', type=float, default=0.2, help='测试集比例')
    args = parser.parse_args()
    
    # 导入模块
    from ml_models.data_collector import BehaviorDataCollector
    from ml_models.trainer import PostureTrainer
    
    # 1. 收集/生成数据
    print(f"\n[1/3] 准备训练数据 (每类 {args.samples} 样本)...")
    collector = BehaviorDataCollector()
    result = collector.generate_synthetic_data(num_samples_per_class=args.samples)
    print(f"  生成完成: {result}")
    
    stats = collector.get_statistics()
    print(f"  数据统计: {stats['total_samples']} 总样本")
    
    X, y = collector.get_training_data()
    if X is None:
        print("错误: 没有训练数据")
        return
    
    # 2. 训练模型
    print(f"\n[2/3] 训练 {args.model} 模型...")
    trainer = PostureTrainer()
    results = trainer.train(X, y, model_type=args.model, test_size=args.test_size)
    
    print(f"\n  测试集准确率: {results['accuracy']:.4f}")
    print(f"  交叉验证: {results['cv_mean']:.4f} (+/- {results['cv_std']*2:.4f})")
    
    # 3. 保存模型
    print(f"\n[3/3] 保存模型...")
    model_path = trainer.save_model(f"posture_{args.model}")
    
    print("\n" + "=" * 60)
    print("训练完成!")
    print("=" * 60)
    print(f"\n模型文件: {model_path}")
    print(f"API接口:")
    print(f"  - POST /api/ml/generate-data 生成训练数据")
    print(f"  - POST /api/ml/train 训练模型")
    print(f"  - GET  /api/ml/model/status 查看模型状态")
    print(f"  - POST /api/ml/predict 测试预测")


if __name__ == '__main__':
    main()
