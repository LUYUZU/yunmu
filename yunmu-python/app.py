# app.py - 完整版，集成机器学习算法处理
import sys
import os
from pathlib import Path

sys.path.append(str(Path(__file__).parent))

from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
import traceback
import json
import threading
import urllib.request
import urllib.error

import numpy as np

from models.step_counter import StepCounter, DailyStepTracker
from models.posture_model import PostureClassifier
from models.step_alert import StepAlert
from utils.gps_processor import GPSProcessor

# 导入机器学习模块
try:
    from ml_models.data_collector import BehaviorDataCollector
    from ml_models.trainer import PostureTrainer, quick_train
    from ml_models.posture_predictor import MLPostureClassifier
    ML_AVAILABLE = True
except ImportError as e:
    ML_AVAILABLE = False
    print(f"机器学习模块加载失败: {e}")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# 初始化模块
step_counter = StepCounter()
daily_tracker = DailyStepTracker()
posture_classifier = PostureClassifier()  # 规则引擎（备用）
gps_processor = GPSProcessor()
step_alert = StepAlert()

# 尝试加载 ML 模型
ml_classifier = None
if ML_AVAILABLE:
    try:
        ml_classifier = MLPostureClassifier()
        logger.info(f"ML模型加载成功: {ml_classifier.get_model_info()}")
    except Exception as e:
        logger.warning(f"ML模型加载失败，使用规则引擎: {e}")
        ml_classifier = None
else:
    logger.warning("机器学习模块不可用，使用规则引擎")

# 数据收集器
data_collector = BehaviorDataCollector() if ML_AVAILABLE else None

# 存储设备数据
device_data = {}
# 存储设备的历史步数（用于异常检测）
device_step_history = {}


def to_json_serializable(obj):
    """递归转换 numpy 类型为 Python 原生类型，防止 JSON 序列化失败"""
    if isinstance(obj, dict):
        return {k: to_json_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, (list, tuple)):
        return [to_json_serializable(i) for i in obj]
    elif isinstance(obj, (np.integer,)):
        return int(obj)
    elif isinstance(obj, (np.floating,)):
        return float(obj)
    elif isinstance(obj, (np.bool_,)):
        return bool(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    return obj


# ========== 数据接收接口（Java调用） ==========
@app.route('/api/device/data', methods=['POST'])
def receive_device_data():
    """
    接收Java端转发的MQTT数据，并调用算法处理
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({'success': False, 'error': '请求体为空'}), 400

        logger.info(f"收到设备数据: {data}")

        device_id = data.get('device_id') or data.get('topic')

        if not device_id:
            return jsonify({'success': False, 'error': '缺少device_id'}), 400

        # 提取原始数据
        longitude = data.get('longitude')
        latitude = data.get('latitude')
        move = data.get('move', 0)  # 0=静止, 1=移动, 2=跑
        steps_from_device = data.get('steps', 0)  # 设备上报的步数
        counter = data.get('counter', 0)

        # ========== 1. 更新GPS位置 ==========
        if longitude and latitude:
            gps_processor.current_position['longitude'] = longitude
            gps_processor.current_position['latitude'] = latitude
            logger.info(f"设备 {device_id} GPS位置更新: ({latitude}, {longitude})")

        # ========== 2. 姿态识别算法 ==========
        # 根据move字段和设备上报的加速度数据判断姿态
        # 优先使用机器学习模型，备用规则引擎
        accel_x = data.get('accel_x')
        accel_y = data.get('accel_y')
        accel_z = data.get('accel_z')
        gyro_x = data.get('gyro_x', 0)
        gyro_y = data.get('gyro_y', 0)
        gyro_z = data.get('gyro_z', 0)

        if accel_x is not None and accel_y is not None and accel_z is not None:
            # 优先使用 ML 模型
            if ml_classifier is not None and ml_classifier.is_model_loaded():
                posture_result = ml_classifier.predict(
                    accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z
                )
                posture = posture_result.get('posture_type', 'standing')
                posture_confidence = posture_result.get('confidence', 0.7)
                
                # 记录模型类型
                model_type = posture_result.get('model_type', 'unknown')
                fallback = posture_result.get('fallback_used', False)
                
                logger.info(f"ML姿态识别: {posture}, 置信度: {posture_confidence:.3f}, 模型: {model_type}")
                if fallback:
                    logger.warning("ML置信度低，使用规则引擎备用")
                
                # 收集数据用于后续训练（如果未标注）
                if data_collector:
                    data_collector.add_sample(
                        accel_x, accel_y, accel_z,
                        gyro_x, gyro_y, gyro_z,
                        posture=posture, device_id=device_id
                    )
            else:
                # 使用规则引擎
                posture_result = posture_classifier.predict_posture(
                    accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z
                )
                posture = posture_result.get('posture_type', 'standing')
                posture_confidence = posture_result.get('confidence', 0.7)
                logger.info(f"规则引擎姿态识别: {posture}, 置信度: {posture_confidence}")
                
                # 收集数据
                if data_collector:
                    data_collector.add_sample(
                        accel_x, accel_y, accel_z,
                        gyro_x, gyro_y, gyro_z,
                        posture=posture, device_id=device_id
                    )
        else:
            # 根据move字段推断姿态
            if move == 0:
                posture = 'standing'
            elif move == 1:
                posture = 'walking'
            elif move == 2:
                posture = 'running'
            else:
                posture = 'standing'
            posture_confidence = 0.7
            logger.info(f"根据move推断姿态: {posture}")

        # ========== 3. 步数统计算法 ==========
        # 如果有加速度数据，使用步数统计算法重新计算步数
        accel_y_history = data.get('accel_y_history', [])
        timestamps = data.get('timestamps', [])

        if accel_y_history and timestamps:
            # 使用步数统计算法
            step_counter.set_posture(posture)
            step_result = step_counter.count_steps(accel_y_history, timestamps)
            calculated_steps = step_result.get('total_steps', 0)
            step_frequency = step_result.get('step_frequency', 0)
            activity_level = step_result.get('activity_level', 'low')

            logger.info(f"算法步数统计: 总步数={calculated_steps}, 步频={step_frequency}, 活动水平={activity_level}")

            # 更新每日步数
            if calculated_steps > 0:
                daily_tracker.add_steps(calculated_steps)
        else:
            # 使用设备上报的步数
            calculated_steps = steps_from_device
            step_frequency = 0
            activity_level = 'low'
            if calculated_steps > 0:
                daily_tracker.add_steps(calculated_steps)
            logger.info(f"使用设备上报步数: {calculated_steps}")

        # ========== 4. 步数异常检测 ==========
        # 获取设备历史步数
        if device_id not in device_step_history:
            device_step_history[device_id] = []

        device_step_history[device_id].append({
            'steps': calculated_steps,
            'timestamp': data.get('timestamp'),
            'posture': posture
        })
        # 保留最近100条记录
        if len(device_step_history[device_id]) > 100:
            device_step_history[device_id] = device_step_history[device_id][-100:]

        # 提取历史步数值用于基线计算
        history_steps = [h['steps'] for h in device_step_history[device_id][-30:]]
        if len(history_steps) >= 10:
            step_alert.update_baseline(history_steps)

        # 检测异常
        anomaly = step_alert.check_anomaly(calculated_steps, data.get('timestamp'), device_id)

        if anomaly.get('is_anomaly'):
            logger.warning(f"设备 {device_id} 步数异常: {calculated_steps}步, "
                           f"正常范围: {anomaly.get('normal_range')}, "
                           f"严重程度: {anomaly.get('severity')}")

        # ========== 5. 存储设备状态 ==========
        device_data[device_id] = {
            'device_id': device_id,
            'posture': posture,
            'posture_confidence': posture_confidence,
            'move': move,
            'steps_from_device': steps_from_device,
            'calculated_steps': calculated_steps,
            'step_frequency': step_frequency,
            'activity_level': activity_level,
            'location': {'lat': latitude, 'lng': longitude},
            'counter': counter,
            'anomaly': anomaly,
            'last_update': data.get('timestamp'),
            'raw_data': data
        }

        # ========== 6. 获取每日步数摘要 ==========
        daily_summary = daily_tracker.get_daily_summary()

        logger.info(f"设备 {device_id} 状态更新完成: "
                    f"姿态={posture}({posture_confidence:.2f}), "
                    f"步数={calculated_steps}, "
                    f"异常={anomaly.get('is_anomaly')}")

        # ========== 7. 回调通知 Java ==========
        callback_url = data.get('callback_url')
        ml_result = to_json_serializable({
            'device_id': device_id,
            'animal_id': data.get('animal_id', device_id),
            'posture': posture,
            'posture_confidence': posture_confidence,
            'calculated_steps': calculated_steps,
            'step_frequency': step_frequency,
            'activity_level': activity_level,
            'anomaly': anomaly,
            'daily_summary': daily_summary,
            'timestamp': data.get('timestamp'),
            'accel_x': accel_x,
            'accel_y': accel_y,
            'accel_z': accel_z,
            'gyro_x': gyro_x,
            'gyro_y': gyro_y,
            'gyro_z': gyro_z,
            'longitude': longitude,
            'latitude': latitude,
        })

        if callback_url:
            def _do_callback():
                try:
                    body = json.dumps(ml_result).encode('utf-8')
                    req = urllib.request.Request(
                        callback_url,
                        data=body,
                        headers={'Content-Type': 'application/json'}
                    )
                    with urllib.request.urlopen(req, timeout=10) as resp:
                        logger.info(f"Java 回调成功 [{resp.status}]: {callback_url}")
                except urllib.error.HTTPError as e:
                    logger.warning(f"Java 回调 HTTP 错误 {e.code}: {callback_url}")
                except urllib.error.URLError as e:
                    logger.warning(f"Java 回调连接失败: {callback_url} — {e.reason}")
                except Exception as e:
                    logger.error(f"Java 回调异常: {e}")

            threading.Thread(target=_do_callback, daemon=True).start()
            logger.info(f"已异步回调 Java: {callback_url}")
        else:
            logger.debug("无 callback_url，跳过 Java 回调")

        return jsonify(to_json_serializable({
            'success': True,
            'message': '数据接收成功',
            'device_id': device_id,
            'posture': posture,
            'posture_confidence': posture_confidence,
            'calculated_steps': calculated_steps,
            'step_frequency': step_frequency,
            'activity_level': activity_level,
            'anomaly': anomaly,
            'daily_summary': daily_summary
        }))

    except Exception as e:
        logger.error(f"处理设备数据失败: {e}")
        logger.error(traceback.format_exc())
        return jsonify({'success': False, 'error': str(e)}), 500


# ========== 查询接口 ==========
@app.route('/api/device/<device_id>', methods=['GET'])
def get_device(device_id):
    """获取指定设备状态"""
    data = device_data.get(device_id, {})
    history = device_step_history.get(device_id, [])
    return jsonify(to_json_serializable({
        'success': True,
        'device_id': device_id,
        'current': data,
        'history': history[-20:],  # 最近20条历史
        'history_count': len(history)
    }))


@app.route('/api/devices', methods=['GET'])
def get_devices():
    """获取所有设备状态"""
    return jsonify(to_json_serializable({
        'success': True,
        'devices': device_data,
        'count': len(device_data)
    }))


@app.route('/api/steps/daily', methods=['GET'])
def get_daily_steps():
    """获取每日步数"""
    daily = daily_tracker.get_daily_summary()
    weekly = daily_tracker.get_weekly_summary()
    return jsonify(to_json_serializable({
        'success': True,
        'daily': daily,
        'weekly': weekly,
        'alert_summary': step_alert.get_alert_summary()
    }))


@app.route('/api/steps/history/<device_id>', methods=['GET'])
def get_step_history(device_id):
    """获取设备步数历史"""
    history = device_step_history.get(device_id, [])
    return jsonify(to_json_serializable({
        'success': True,
        'device_id': device_id,
        'history': history,
        'count': len(history)
    }))


@app.route('/api/alert/summary', methods=['GET'])
def get_alert_summary():
    """获取预警摘要"""
    return jsonify(to_json_serializable({
        'success': True,
        'alert_summary': step_alert.get_alert_summary(),
        'frontend_data': step_alert.get_frontend_alert_data()
    }))


@app.route('/health', methods=['GET'])
def health_check():
    ml_info = {}
    if ml_classifier:
        ml_info = ml_classifier.get_model_info()
    
    data_stats = {}
    if data_collector:
        data_stats = data_collector.get_statistics()
    
    return jsonify({
        'status': 'healthy',
        'service': 'yunmu-ml-service',
        'version': '3.0.0',
        'devices_count': len(device_data),
        'algorithms': ['step_counter', 'posture_classifier', 'step_alert', 'gps_processor'],
        'ml_enabled': ML_AVAILABLE,
        'ml_model': ml_info,
        'data_stats': data_stats
    })


# ========== 机器学习相关接口 ==========
@app.route('/api/ml/generate-data', methods=['POST'])
def generate_training_data():
    """生成合成训练数据"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500
    
    try:
        num_samples = request.json.get('samples_per_class', 500) if request.json else 500
        result = data_collector.generate_synthetic_data(num_samples_per_class=num_samples)
        return jsonify({
            'success': True,
            'message': '训练数据生成完成',
            'result': result,
            'stats': data_collector.get_statistics()
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/ml/train', methods=['POST'])
def train_model():
    """训练机器学习模型"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500
    
    try:
        model_type = request.json.get('model_type', 'random_forest') if request.json else 'random_forest'
        
        X, y = data_collector.get_training_data()
        if X is None or len(X) < 50:
            return jsonify({
                'success': False, 
                'error': '训练数据不足，请先生成或收集数据'
            }), 400
        
        trainer = PostureTrainer()
        results = trainer.train(X, y, model_type=model_type)
        
        # 保存模型
        model_path = trainer.save_model(f"posture_{model_type}")
        
        # 重新加载模型
        global ml_classifier
        ml_classifier = MLPostureClassifier()
        
        return jsonify({
            'success': True,
            'message': f'{model_type} 模型训练完成',
            'results': results,
            'model_path': model_path
        })
    except Exception as e:
        logger.error(f"训练失败: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/ml/model/status', methods=['GET'])
def get_model_status():
    """获取模型状态"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500
    
    ml_info = ml_classifier.get_model_info() if ml_classifier else {}
    data_stats = data_collector.get_statistics() if data_collector else {}
    
    return jsonify({
        'success': True,
        'ml_available': ML_AVAILABLE,
        'model_info': ml_info,
        'data_stats': data_stats
    })


@app.route('/api/ml/predict', methods=['POST'])
def ml_predict():
    """测试ML预测"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500
    
    try:
        data = request.json
        result = ml_classifier.predict(
            accel_x=data.get('accel_x', 0),
            accel_y=data.get('accel_y', 0),
            accel_z=data.get('accel_z', 9.8),
            gyro_x=data.get('gyro_x', 0),
            gyro_y=data.get('gyro_y', 0),
            gyro_z=data.get('gyro_z', 0)
        )
        return jsonify({
            'success': True,
            'result': result
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)