# install_deps.py
import subprocess
import sys

# 依赖列表
dependencies = [
    "Flask==2.3.3",
    "flask-cors==4.0.0",
    "numpy==1.24.3",
    "pandas==2.0.3",
    "scipy==1.10.1",
    "scikit-learn==1.3.0",
    "joblib==1.3.1",
    "librosa==0.10.0",
    "python-dotenv==1.0.0"
]

# 可选依赖
optional_deps = [
    "matplotlib==3.7.2",
    "seaborn==0.12.2",
    "plotly==5.17.0",
    "gunicorn==21.2.0",
    "greenlet==2.0.2",
    "pytest==7.4.2",
    "pytest-cov==4.1.0",
    "black==23.9.1",
    "flake8==6.1.0",
    "tqdm==4.66.1",
    "requests==2.31.0"
]


def install_package(package, use_mirror=True):
    """安装单个包"""
    print(f"正在安装: {package}")

    cmd = [sys.executable, "-m", "pip", "install"]

    if use_mirror:
        # 使用国内镜像加速
        cmd.extend([
            "-i", "https://pypi.tuna.tsinghua.edu.cn/simple",
            "--trusted-host", "pypi.tuna.tsinghua.edu.cn"
        ])

    cmd.append(package)

    try:
        subprocess.check_call(cmd)
        print(f"✓ 成功安装: {package}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ 安装失败: {package}, 错误: {e}")
        return False


def main():
    print("开始安装云牧智感项目依赖...")

    # 安装核心依赖
    print("\n=== 安装核心依赖 ===")
    success_count = 0
    for dep in dependencies:
        if install_package(dep):
            success_count += 1

    # 安装可选依赖
    print("\n=== 安装可选依赖 ===")
    for dep in optional_deps:
        install_package(dep)

    print(f"\n安装完成！成功安装 {success_count}/{len(dependencies)} 个核心依赖")

    # 验证安装
    print("\n=== 验证安装 ===")
    try:
        import flask
        import numpy
        import sklearn
        print("✓ 核心依赖验证通过")
    except ImportError as e:
        print(f"✗ 依赖验证失败: {e}")


if __name__ == "__main__":
    main()