#!/usr/bin/env python3
# check_env.py - 环境检查脚本
import sys
import subprocess
import importlib

print("=" * 60)
print("云牧智感 - Python 环境检查")
print("=" * 60)

print(f"\nPython 版本：{sys.version}")
print(f"Python 执行路径：{sys.executable}")

required_packages = {
    'flask': 'Flask',
    'flask_cors': 'Flask-CORS',
    'numpy': 'NumPy',
    'scipy': 'SciPy',
    'pandas': 'Pandas',
    'sklearn': 'Scikit-learn',
    'librosa': 'Librosa',
    'joblib': 'Joblib',
    'dotenv': 'python-dotenv'
}

missing_packages = []

print("\n" + "=" * 60)
print("检查依赖包")
print("=" * 60)

for package, display_name in required_packages.items():
    try:
        module = importlib.import_module(package)
        version = getattr(module, '__version__', 'unknown')
        print(f"[OK] {display_name}: {version}")
    except ImportError:
        print(f"[MISSING] {display_name}: 未安装")
        missing_packages.append(package)

if missing_packages:
    print("\n" + "=" * 60)
    print("缺少以下包，是否自动安装？(y/n)")
    print("=" * 60)
    
    try:
        response = input().strip().lower()
        if response == 'y':
            print("\n正在安装依赖...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])
            print("\n安装完成！请重新运行此脚本检查环境。")
        else:
            print("\n请手动运行：pip install -r requirements.txt")
    except Exception as e:
        print(f"\n安装失败：{e}")
        print("\n请手动运行：pip install -r requirements.txt")
else:
    print("\n[SUCCESS] 所有依赖已安装！")

print("\n" + "=" * 60)
print("检查目录结构")
print("=" * 60)

from pathlib import Path

required_dirs = ['models/trained', 'logs', 'data']
for dir_name in required_dirs:
    dir_path = Path(dir_name)
    if dir_path.exists():
        print(f"[OK] {dir_name}: 存在")
    else:
        print(f"[MISSING] {dir_name}: 不存在 (将自动创建)")
        dir_path.mkdir(parents=True, exist_ok=True)

print("\n" + "=" * 60)
print("环境检查完成")
print("=" * 60)
