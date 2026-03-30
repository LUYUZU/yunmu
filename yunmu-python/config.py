# config.py
import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    # Flask配置
    SECRET_KEY = os.getenv('SECRET_KEY', 'yunmu-secret-key-2024')
    DEBUG = os.getenv('DEBUG', 'False').lower() == 'true'

    # 模型配置
    MODEL_PATH = os.getenv('MODEL_PATH', 'models/trained/')
    DATA_PATH = os.getenv('DATA_PATH', 'data/')

    # 算法参数
    RUMINATION_THRESHOLD = float(os.getenv('RUMINATION_THRESHOLD', '0.7'))
    FEEDING_THRESHOLD = float(os.getenv('FEEDING_THRESHOLD', '0.65'))
    HEALTH_ALERT_THRESHOLD = float(os.getenv('HEALTH_ALERT_THRESHOLD', '0.6'))

    # 特征提取参数
    ACCEL_SAMPLING_RATE = int(os.getenv('ACCEL_SAMPLING_RATE', '50'))
    SOUND_SAMPLING_RATE = int(os.getenv('SOUND_SAMPLING_RATE', '1000'))

    # 日志配置
    LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
    LOG_FILE = os.getenv('LOG_FILE', 'logs/ml_service.log')