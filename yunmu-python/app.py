# app.py - 完整版，集成算法处理
import sys
import os
from pathlib import Path

sys.path.append(str(Path(__file__).parent))

from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
import traceback

from models.step_counter import StepCounter, DailyStepTracker
from models.posture_model import PostureClassifier
from models.step_alert import StepAlert
from utils.gps_processor import GPSProcessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# 初始化模块
step_counter = StepCounter()
daily_tracker = DailyStepTracker()
posture_classifier = PostureClassifier()
gps_processor = GPSProcessor()
step_alert = StepAlert()

# 存储设备数据
device_data = {}
# 存储设备的历史步数（用于异常检测）
device_step_history = {}


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
        # 如果有加速度数据，使用姿态识别算法；否则根据move字段推断
        accel_x = data.get('accel_x')
        accel_y = data.get('accel_y')
        accel_z = data.get('accel_z')

        if accel_x is not None and accel_y is not None and accel_z is not None:
            # 使用姿态识别算法
            posture_result = posture_classifier.predict_posture(
                accel_x, accel_y, accel_z,
                data.get('gyro_x', 0), data.get('gyro_y', 0), data.get('gyro_z', 0)
            )
            posture = posture_result.get('posture_type', 'standing')
            posture_confidence = posture_result.get('confidence', 0.7)
            logger.info(f"算法姿态识别: {posture}, 置信度: {posture_confidence}")
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

        return jsonify({
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
        })

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
    return jsonify({
        'success': True,
        'device_id': device_id,
        'current': data,
        'history': history[-20:],  # 最近20条历史
        'history_count': len(history)
    })


@app.route('/api/devices', methods=['GET'])
def get_devices():
    """获取所有设备状态"""
    return jsonify({
        'success': True,
        'devices': device_data,
        'count': len(device_data)
    })


@app.route('/api/steps/daily', methods=['GET'])
def get_daily_steps():
    """获取每日步数"""
    daily = daily_tracker.get_daily_summary()
    weekly = daily_tracker.get_weekly_summary()
    return jsonify({
        'success': True,
        'daily': daily,
        'weekly': weekly,
        'alert_summary': step_alert.get_alert_summary()
    })


@app.route('/api/steps/history/<device_id>', methods=['GET'])
def get_step_history(device_id):
    """获取设备步数历史"""
    history = device_step_history.get(device_id, [])
    return jsonify({
        'success': True,
        'device_id': device_id,
        'history': history,
        'count': len(history)
    })


@app.route('/api/alert/summary', methods=['GET'])
def get_alert_summary():
    """获取预警摘要"""
    return jsonify({
        'success': True,
        'alert_summary': step_alert.get_alert_summary(),
        'frontend_data': step_alert.get_frontend_alert_data()
    })


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'service': 'yunmu-ml-service',
        'version': '2.0.0',
        'devices_count': len(device_data),
        'algorithms': ['step_counter', 'posture_classifier', 'step_alert', 'gps_processor']
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)