#!/usr/bin/env python3
# run.py
import argparse
import logging
import sys
from pathlib import Path

# 添加项目路径
sys.path.append(str(Path(__file__).parent))

from app import app
from config import Config


def setup_logging(level=logging.INFO):
    """配置日志"""
    logging.basicConfig(
        level=level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler('logs/ml_service.log'),
            logging.StreamHandler(sys.stdout)
        ]
    )


def parse_args():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description='云牧智感 - Python机器学习服务')
    parser.add_argument('--host', default='0.0.0.0', help='服务主机地址')
    parser.add_argument('--port', type=int, default=5000, help='服务端口')
    parser.add_argument('--debug', action='store_true', help='调试模式')
    parser.add_argument('--log-level', default='INFO',
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR'],
                        help='日志级别')
    return parser.parse_args()


def main():
    """主函数"""
    args = parse_args()

    # 配置日志
    log_level = getattr(logging, args.log_level)
    setup_logging(log_level)

    logger = logging.getLogger(__name__)

    # 创建日志目录
    Path('logs').mkdir(exist_ok=True)

    # 显示配置信息
    logger.info(f"启动云牧智感机器学习服务")
    logger.info(f"主机: {args.host}")
    logger.info(f"端口: {args.port}")
    logger.info(f"调试模式: {args.debug}")
    logger.info(f"日志级别: {args.log_level}")

    # 显示模型信息
    try:
        from models.behavior_model import BehaviorClassifier
        from features.feature_extractor import FeatureExtractor

        # 初始化模型
        behavior_classifier = BehaviorClassifier()
        feature_extractor = FeatureExtractor()

        logger.info("模型初始化完成")

    except Exception as e:
        logger.error(f"模型初始化失败: {e}")

    # 启动Flask应用
    app.run(
        host=args.host,
        port=args.port,
        debug=args.debug,
        threaded=True
    )


if __name__ == '__main__':
    main()