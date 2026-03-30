# fix_errors.py
import os
import re


def fix_app_py():
    """修复 app.py 中的反刍预测"""
    app_path = "app.py"

    with open(app_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 修复 predict_rumination 函数
    old_pattern = r'prediction = behavior_classifier\.predict_rumination\(acoustic_features\)'
    new_pattern = 'prediction = behavior_classifier.predict_rumination(acoustic_features, {})'

    content = re.sub(old_pattern, new_pattern, content)

    with open(app_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print("✅ app.py 已修复")


def fix_behavior_model():
    """修复 behavior_model.py"""
    model_path = "models/behavior_model.py"

    with open(model_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 修复 LogisticRegression 初始化
    old_init = "self.models['accel_logistic'] = LogisticRegression(\n            max_iter=1000,\n            class_weight='balanced',\n            random_state=42\n        )"
    new_init = "self.models['accel_logistic'] = LogisticRegression(\n            max_iter=1000,\n            class_weight='balanced',\n            random_state=42,\n            multi_class='ovr',\n            solver='lbfgs'\n        )"

    content = content.replace(old_init, new_init)

    # 修复 predict_rumination 方法
    if "def predict_rumination(self, acoustic_features: Dict" in content:
        content = content.replace(
            "def predict_rumination(self, acoustic_features: Dict, accel_features: Dict) -> Tuple[bool, float]:",
            "def predict_rumination(self, acoustic_features: Dict = None, accel_features: Dict = None) -> Tuple[bool, float]:"
        )

    with open(model_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print("✅ behavior_model.py 已修复")


def fix_feature_extractor():
    """修复 feature_extractor.py"""
    feat_path = "features/feature_extractor.py"

    with open(feat_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 修复 MFCC 的 n_fft
    mfcc_pattern = r'mfccs = librosa\.feature\.mfcc\(y=sound_array, sr=sampling_rate, n_mfcc=13\)'
    mfcc_new = 'n_fft = min(2048, len(sound_array))\n                mfccs = librosa.feature.mfcc(y=sound_array, sr=sampling_rate, n_mfcc=13, n_fft=n_fft)'

    content = re.sub(mfcc_pattern, mfcc_new, content)

    with open(feat_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print("✅ feature_extractor.py 已修复")


if __name__ == "__main__":
    print("开始修复错误...")
    print("=" * 50)

    fix_app_py()
    fix_behavior_model()
    fix_feature_extractor()

    print("\n" + "=" * 50)
    print("修复完成！请重启 Python 服务")