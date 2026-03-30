# app.py - 云牧智感机器学习服务
from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import joblib
from models.behavior_model import BehaviorClassifier
from features.feature_extractor import FeatureExtractor
import traceback
import logging

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# 初始化模型
logger.info("正在初始化模型和特征提取器...")
behavior_classifier = BehaviorClassifier()
feature_extractor = FeatureExtractor()
logger.info("模型和特征提取器初始化完成")


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        'status': 'healthy',
        'service': 'yunmu-ml-service',
        'version': '1.0.0'
    })


@app.route('/api/predict/behavior', methods=['POST'])
def predict_behavior():
    """行为预测接口"""
    try:
        data = request.get_json()
        logger.info(f"收到行为预测请求：{data.get('animal_id', 'unknown')}")

        # 提取特征
        features = feature_extractor.extract_features(data)

        # 预测行为
        prediction = behavior_classifier.predict(features)

        # 计算置信度
        confidence = behavior_classifier.get_confidence(features)

        return jsonify({
            'success': True,
            'animal_id': data.get('animal_id'),
            'behavior': prediction,
            'confidence': float(confidence),
            'features': features.tolist() if isinstance(features, np.ndarray) else features,
            'timestamp': data.get('timestamp')
        })

    except Exception as e:
        logger.error(f"行为预测失败：{str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '行为预测失败'
        }), 500


@app.route('/api/train', methods=['POST'])
def train_model():
    """在线训练模型接口"""
    try:
        from train import train_models
        
        logger.info("收到模型训练请求")
        train_models()
        
        return jsonify({
            'success': True,
            'message': '模型训练完成'
        })
    except Exception as e:
        logger.error(f"模型训练失败：{str(e)}")
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '模型训练失败'
        }), 500


# app.py 中的 predict_rumination 函数修复

@app.route('/api/predict/rumination', methods=['POST'])
def predict_rumination():
    """反刍行为预测"""
    try:
        data = request.get_json()
        logger.info(f"反刍预测请求: {data.get('animal_id', 'unknown')}")

        # 提取声学特征
        acoustic_features = feature_extractor.extract_acoustic_features(
            data.get('audio_data', [])
        )

        # 修复：predict_rumination需要两个参数，这里传入空字典
        # 或者修改 behavior_classifier.predict_rumination 方法
        prediction = behavior_classifier.predict_rumination(
            acoustic_features,  # 声学特征
            {}  # 加速度特征，暂时为空
        )

        return jsonify({
            'success': True,
            'animal_id': data.get('animal_id'),
            'is_ruminating': prediction[0] if isinstance(prediction, tuple) else prediction.get('is_ruminating', False),
            'confidence': prediction[1] if isinstance(prediction, tuple) else prediction.get('confidence', 0),
            'rumination_duration': prediction.get('rumination_duration', 0) if isinstance(prediction, dict) else 0,
            'timestamp': data.get('timestamp')
        })

    except Exception as e:
        logger.error(f"反刍预测失败：{str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '反刍预测失败'
        }), 500


# 导入步数统计和姿态识别模块
from models.step_counter import StepCounter, DailyStepTracker
from models.posture_model import PostureClassifier

# 初始化步数计数器和姿态分类器
step_counter = StepCounter()
daily_tracker = DailyStepTracker()
posture_classifier = PostureClassifier()


@app.route('/api/count/steps', methods=['POST'])
def count_steps():
    """步数统计接口"""
    try:
        data = request.get_json()
        animal_id = data.get('animal_id', 'unknown')
        logger.info(f"收到步数统计请求：{animal_id}")

        # 获取加速度数据
        accel_x = data.get('accel_x', [])
        accel_y = data.get('accel_y', [])
        accel_z = data.get('accel_z', [])
        timestamps = data.get('timestamps', [])

        if not accel_x or not timestamps:
            return jsonify({
                'success': False,
                'error': '缺少加速度数据'
            }), 400

        # 统计步数
        result = step_counter.count_steps_from_accel_xyz(
            accel_x, accel_y, accel_z, timestamps
        )

        # 更新每日统计
        daily_result = daily_tracker.add_steps(result.get('total_steps', 0))

        return jsonify({
            'success': True,
            'animal_id': animal_id,
            'steps': result.get('total_steps', 0),
            'step_frequency': result.get('step_frequency', 0),
            'walking_distance': result.get('walking_distance', 0),
            'activity_level': result.get('activity_level', 'low'),
            'daily_summary': daily_result,
            'timestamp': data.get('timestamp')
        })

    except Exception as e:
        logger.error(f"步数统计失败：{str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '步数统计失败'
        }), 500


@app.route('/api/predict/posture', methods=['POST'])
def predict_posture():
    """姿态识别接口"""
    try:
        data = request.get_json()
        animal_id = data.get('animal_id', 'unknown')
        logger.info(f"收到姿态识别请求：{animal_id}")

        # 预测姿态
        result = posture_classifier.predict_posture(
            accel_x=data.get('accel_x', 0),
            accel_y=data.get('accel_y', 0),
            accel_z=data.get('accel_z', 9.8),
            gyro_x=data.get('gyro_x', 0),
            gyro_y=data.get('gyro_y', 0),
            gyro_z=data.get('gyro_z', 0)
        )

        return jsonify({
            'success': True,
            'animal_id': animal_id,
            'posture_type': result.get('posture_type', 'unknown'),
            'confidence': result.get('confidence', 0),
            'features': result.get('features', {}),
            'timestamp': data.get('timestamp')
        })

    except Exception as e:
        logger.error(f"姿态识别失败：{str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '姿态识别失败'
        }), 500


@app.route('/api/batch/predict', methods=['POST'])
def batch_predict():
    """批量预测接口"""
    try:
        data = request.get_json()
        items = data.get('data', [])
        logger.info(f"收到批量预测请求，共 {len(items)} 条数据")

        results = []
        for item in items:
            # 行为预测
            features = feature_extractor.extract_features(item)
            behavior = behavior_classifier.predict(features)
            confidence = behavior_classifier.get_confidence(features)

            # 姿态识别
            posture_result = posture_classifier.predict_posture(
                accel_x=item.get('accel_x', 0),
                accel_y=item.get('accel_y', 0),
                accel_z=item.get('accel_z', 9.8),
                gyro_x=item.get('gyro_x', 0),
                gyro_y=item.get('gyro_y', 0),
                gyro_z=item.get('gyro_z', 0)
            )

            results.append({
                'animal_id': item.get('animal_id'),
                'behavior': behavior,
                'confidence': float(confidence),
                'posture': posture_result.get('posture_type'),
                'posture_confidence': posture_result.get('confidence')
            })

        return jsonify({
            'success': True,
            'count': len(results),
            'data': results
        })

    except Exception as e:
        logger.error(f"批量预测失败：{str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({
            'success': False,
            'error': str(e),
            'message': '批量预测失败'
        }), 500


# app.py - 更新部分

from models.step_alert import StepAlert
from utils.gps_processor import GPSProcessor

# 初始化新模块
step_alert = StepAlert()
gps_processor = GPSProcessor()


# 姿态联动计步
@app.route('/api/step/count', methods=['POST'])
def step_count_with_posture():
    """带姿态联动的步数统计"""
    try:
        data = request.get_json()
        animal_id = data.get('animal_id', 'unknown')

        # 获取姿态
        posture = data.get('posture', 'standing')
        step_counter.set_posture(posture)

        # 统计步数
        result = step_counter.count_steps_from_accel_xyz(
            data.get('accel_x', []),
            data.get('accel_y', []),
            data.get('accel_z', []),
            data.get('timestamps', []),
            data.get('gyro_pitch_rate', [])
        )

        # 步数异常检测
        if result.get('success'):
            anomaly = step_alert.check_anomaly(
                result['total_steps'],
                data.get('timestamp', 0),
                animal_id
            )
            result['anomaly_check'] = anomaly

            # 触发预警
            if anomaly['is_anomaly'] and anomaly['severity'] == 'critical':
                step_alert.send_alert(anomaly, methods=['email', 'platform'])

        return jsonify({
            'success': True,
            'animal_id': animal_id,
            'steps': result.get('total_steps', 0),
            'step_frequency': result.get('step_frequency', 0),
            'walking_distance': result.get('walking_distance', 0),
            'activity_level': result.get('activity_level', 'low'),
            'current_posture': posture,
            'anomaly': result.get('anomaly_check', {}),
            'timestamp': data.get('timestamp')
        })

    except Exception as e:
        logger.error(f"步数统计失败: {str(e)}")
        return jsonify({'success': False, 'error': str(e)}), 500


# GPS定位接口
@app.route('/api/gps/parse', methods=['POST'])
def parse_gps():
    """解析GPS数据"""
    try:
        data = request.get_json()
        nmea_sentence = data.get('nmea_sentence', '')

        parsed = gps_processor.parse_nmea_sentence(nmea_sentence)

        return jsonify({
            'success': True,
            'parsed_data': parsed,
            'current_position': gps_processor.get_current_position()
        })

    except Exception as e:
        logger.error(f"GPS解析失败: {str(e)}")
        return jsonify({'success': False, 'error': str(e)}), 500


# WSN定位接口
@app.route('/api/gps/wsn', methods=['POST'])
def wsn_localization():
    """WSN辅助定位"""
    try:
        data = request.get_json()
        rssi_readings = data.get('rssi_readings', {})

        # 设置锚节点位置
        if 'anchors' in data:
            gps_processor.set_anchor_positions(data['anchors'])

        # 校准路径损耗因子
        if 'calibration' in data:
            gps_processor.calibrate_path_loss(
                data['calibration']['distance'],
                data['calibration']['rssi']
            )

        # 定位
        position = gps_processor.wsn_localization(rssi_readings)

        return jsonify({
            'success': True,
            'position': position,
            'path_loss_exponent': gps_processor.path_loss_exponent
        })

    except Exception as e:
        logger.error(f"WSN定位失败: {str(e)}")
        return jsonify({'success': False, 'error': str(e)}), 500


# 步数预警接口
@app.route('/api/step/alert', methods=['GET'])
def get_step_alert():
    """获取步数预警信息"""
    try:
        summary = step_alert.get_alert_summary()
        frontend_data = step_alert.get_frontend_alert_data()

        return jsonify({
            'success': True,
            'summary': summary,
            'frontend_data': frontend_data,
            'current_normal_range': [step_alert.lower_bound, step_alert.upper_bound]
        })

    except Exception as e:
        logger.error(f"获取预警信息失败: {str(e)}")
        return jsonify({'success': False, 'error': str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
