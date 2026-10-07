# app.py - 完整版，集成机器学习算法处理
# 修复版本：SSRF防护 / numpy完整序列化 / 设备级步数隔离 / GPS集成 / API认证 / 优雅退出
import sys
import os
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

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


def _load_dotenv(path=None):
    """轻量 .env 加载器（零依赖），须在任何 os.environ.get 读取前调用"""
    if path is None:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.env')
    if not os.path.exists(path):
        print(f"[config] .env 不存在: {path}")
        return
    with open(path, 'r', encoding='utf-8-sig') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            k = k.strip()
            v = v.strip()
            if len(v) >= 2 and v[0] == v[-1] and v[0] in ('"', "'"):
                v = v[1:-1]
            if k and k not in os.environ:
                os.environ[k] = v


_load_dotenv()

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

# LLM 网关
try:
    from llm_gateway import get_llm_gateway, LLMGateway
    LLM_AVAILABLE_GW = True
except ImportError:
    LLM_AVAILABLE_GW = False
    get_llm_gateway = None

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# =========================================================================
# 全局配置
# =========================================================================

# 允许回调的域名/IP白名单（防止SSRF）
# Java后端地址是本机或内网，配置在这里
ALLOWED_CALLBACK_HOSTS = {
    'localhost', '127.0.0.1',
    '::1', '[::1]',
    # 可按需追加：'10.0.0.1', '192.168.1.100', 'your-java-server.local'
}
# 如果需要支持更多主机，可在环境变量中配置（逗号分隔）
_extra = os.environ.get('ALLOWED_CALLBACK_HOSTS', '')
if _extra:
    for h in _extra.split(','):
        h = h.strip()
        if h:
            ALLOWED_CALLBACK_HOSTS.add(h)

# API 认证令牌（生产环境建议改为更安全的机制，如JWT）
API_TOKEN = os.environ.get('YUNMU_API_TOKEN', 'yunmu-ml-secret-token-2024')
# 回调 Java 时携带的令牌（与 Java 端 yunmu.ml.callback.token 保持一致）
CALLBACK_TOKEN = os.environ.get('YUNMU_ML_CALLBACK_TOKEN', 'yunmu-callback-token-2024')
# 允许无认证访问的路径
_PUBLIC_PATHS = {'/health', '/api/ml/predict'}

# =========================================================================
# 初始化模块
# =========================================================================

step_counters: dict = {}   # 每个设备独立的步数计数器（修复：设备隔离）
daily_tracker = DailyStepTracker()
posture_classifier = PostureClassifier()
gps_processor = GPSProcessor()
step_alert = StepAlert()

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

data_collector = BehaviorDataCollector() if ML_AVAILABLE else None

# 全局设备状态存储（线程安全）
device_data: dict = {}
device_step_history: dict = {}
# 设备「累计步数」基线：协议字段 bushu 是通电后累计值，需按设备求差得到本次增量
device_last_cumulative_steps: dict = {}
_state_lock = threading.RLock()

# =========================================================================
# 工具函数
# =========================================================================


def _check_callback_host(url: str) -> bool:
    """
    SSRF防护：验证回调URL的host是否在白名单内
    返回 True=允许，False=拒绝
    """
    try:
        parsed = urlparse(url)
        host = parsed.hostname or ''
        port = parsed.port

        # 只允许 http/https
        if parsed.scheme not in ('http', 'https'):
            return False

        # localhost 变体
        if host in ('localhost', '127.0.0.1', '::1'):
            return True

        # 白名单匹配
        if host in ALLOWED_CALLBACK_HOSTS:
            return True

        # 支持通配符后缀（*.example.com）
        for allowed in ALLOWED_CALLBACK_HOSTS:
            if allowed.startswith('*.') and host.endswith(allowed[2:]):
                return True

        return False
    except Exception:
        return False


def _get_step_counter(device_id: str) -> StepCounter:
    """获取或创建设备专属的步数计数器"""
    with _state_lock:
        if device_id not in step_counters:
            step_counters[device_id] = StepCounter()
        return step_counters[device_id]


def to_json_serializable(obj):
    """
    递归转换 numpy + 其他不可序列化类型为 Python 原生类型
    覆盖所有常见 numpy 标量类型
    """
    if isinstance(obj, dict):
        return {k: to_json_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, (list, tuple)):
        return [to_json_serializable(i) for i in obj]
    # numpy 整数类型（完整覆盖）
    elif isinstance(obj, np.integer):
        return int(obj)
    # numpy 浮点类型
    elif isinstance(obj, np.floating):
        return float(obj)
    # numpy 布尔类型
    elif isinstance(obj, np.bool_):
        return bool(obj)
    # numpy 复数
    elif isinstance(obj, np.complexfloating):
        return complex(obj)
    # numpy 数组
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    # numpy void/record
    elif isinstance(obj, np.void):
        return str(obj)
    return obj


def _require_auth(f):
    """装饰器：验证 API Token"""
    from functools import wraps

    @wraps(f)
    def decorated(*args, **kwargs):
        # 公开路径免认证
        if request.path in _PUBLIC_PATHS:
            return f(*args, **kwargs)

        # 兼容两种传递方式：Authorization: Bearer <token>（或直接 token）
        # 以及 Java RestTemplate 统一注入的 X-API-Token 头
        token = request.headers.get('Authorization', '')
        if token.startswith('Bearer '):
            token = token[7:]
        if not token:
            token = request.headers.get('X-API-Token', '')

        if token != API_TOKEN:
            return jsonify({'success': False, 'error': '未授权，请检查Token'}), 401

        return f(*args, **kwargs)
    return decorated


# =========================================================================
# 核心数据处理接口（Java调用）
# =========================================================================


@app.route('/api/device/data', methods=['POST'])
@_require_auth
def receive_device_data():
    """
    接收Java端转发的MQTT数据，调用算法处理后返回结果
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({'success': False, 'error': '请求体为空'}), 400

        device_id = data.get('device_id') or data.get('topic')

        if not device_id:
            return jsonify({'success': False, 'error': '缺少device_id'}), 400

        # 提取原始数据
        longitude = data.get('longitude')
        latitude = data.get('latitude')
        move = data.get('move', 0)  # 0=静止, 1=移动, 2=跑
        steps_from_device = data.get('steps', 0)
        counter = data.get('counter', 0)

        # ========== 1. GPS 位置处理（使用 GPSProcessor） ==========
        gps_result = None
        if longitude and latitude:
            # 直接使用已解析的十进制度坐标更新位置。
            # 说明：原实现会拼造一条 NMEA 报文再交回解析器（且校验和硬编码为 *47），
            # 而真实北斗/GNSS 报文校验和并不等于 *47，导致解析几乎必然失败（缺陷 N-10）。
            # Java 端已完成度分→十进制度转换，这里直接写入即可。
            gps_processor.current_position['longitude'] = float(longitude)
            gps_processor.current_position['latitude'] = float(latitude)
            gps_processor.current_position['altitude'] = data.get('altitude', 0)
            gps_processor.current_position['fix_quality'] = 1
            gps_processor.current_position['source'] = 'MQTT'
            gps_result = dict(gps_processor.current_position)
            logger.info(
                f"设备 {device_id} GPS位置更新: ({latitude}, {longitude}), "
                f"来源: {gps_processor.get_current_position().get('source', 'unknown')}"
            )

        # ========== 2. 姿态识别（ML优先，规则兜底） ==========
        accel_x = data.get('accel_x')
        accel_y = data.get('accel_y')
        accel_z = data.get('accel_z')
        gyro_x = data.get('gyro_x', 0)
        gyro_y = data.get('gyro_y', 0)
        gyro_z = data.get('gyro_z', 0)

        # 来源标记：默认规则引擎 + 加速度模态；ML 分支内按实际来源覆盖
        model_type = 'rule_based'
        data_modality = 'accel'

        if accel_x is not None and accel_y is not None and accel_z is not None:
            if ml_classifier is not None and ml_classifier.is_model_loaded():
                posture_result = ml_classifier.predict(
                    float(accel_x), float(accel_y), float(accel_z),
                    float(gyro_x), float(gyro_y), float(gyro_z)
                )

                # 缺陷 A 修复：ML 低置信时必须真正采用规则引擎回退结果，
                # 不能再拿低置信的 ML 预测值当作最终结果（原实现结果层面失效）。
                if posture_result.get('fallback_used'):
                    posture = posture_result.get('fallback_posture') \
                        or posture_result.get('posture_type', 'standing')
                    posture_confidence = posture_result.get(
                        'fallback_confidence',
                        posture_result.get('confidence', 0.7))
                    model_type = 'rule_based'
                    logger.warning(
                        f"设备 {device_id} ML置信度低({posture_result.get('ml_confidence', 0):.3f})，"
                        f"回退到规则引擎，最终采用: {posture} "
                        f"(置信度 {posture_confidence:.3f})"
                    )
                else:
                    posture = posture_result.get('posture_type', 'standing')
                    posture_confidence = posture_result.get('confidence', 0.7)
                    model_type = posture_result.get('model_type', 'unknown')

                if data_collector:
                    # 缺陷 P0-2 修复：不再把模型自身的预测值当作标签回灌
                    # （原实现形成“合成数据 → 伪标签 → 再训练”的闭环，模型会自我强化错误）。
                    # 运行时样本以 posture=None 落盘，视为未标注样本，不作为监督标签。
                    data_collector.add_sample(
                        float(accel_x), float(accel_y), float(accel_z),
                        float(gyro_x), float(gyro_y), float(gyro_z),
                        posture=None, device_id=device_id
                    )
            else:
                posture_result = posture_classifier.predict_posture(
                    float(accel_x), float(accel_y), float(accel_z),
                    float(gyro_x), float(gyro_y), float(gyro_z)
                )
                posture = posture_result.get('posture_type', 'standing')
                posture_confidence = posture_result.get('confidence', 0.7)

                if data_collector:
                    # 同上：规则引擎结果也不回灌为标签
                    data_collector.add_sample(
                        float(accel_x), float(accel_y), float(accel_z),
                        float(gyro_x), float(gyro_y), float(gyro_z),
                        posture=None, device_id=device_id
                    )
        else:
            # 根据 move 字段推断姿态
            posture_map = {0: 'standing', 1: 'walking', 2: 'running'}
            posture = posture_map.get(move, 'standing')
            posture_confidence = 0.7

        # ========== 3. 步数统计算法（设备级隔离） ==========
        accel_y_history = data.get('accel_y_history', [])
        timestamps = data.get('timestamps', [])

        # 每次调用传入当前姿态，算法内部根据姿态决定是否计步
        sc = _get_step_counter(device_id)

        if accel_y_history and timestamps:
            step_result = sc.count_steps(
                accel_y_history, timestamps, posture=posture
            )
            calculated_steps = step_result.get('total_steps', 0)
            cumulative_steps = step_result.get('cumulative_steps', calculated_steps)
            step_frequency = step_result.get('step_frequency', 0)
            activity_level = step_result.get('activity_level', 'low')

            logger.info(
                f"设备 {device_id} 算法步数统计: "
                f"本次={calculated_steps}, 累计={cumulative_steps}, "
                f"步频={step_frequency}, 活动={activity_level}"
            )

            if calculated_steps > 0:
                daily_tracker.add_steps(calculated_steps)
        else:
            # 说明：设备字段 bushu 是「通电后累计步数」，而下游 daily_tracker 与
            # step_counts 表统计的是「本次增量」（StepCountRepository 用 SUM 汇总当日步数）。
            # 原实现把累计值当增量累加，会让当日步数被放大约 N 倍，故此处按设备保存
            # 上一次累计值并求差；首次上报只记基线、不计数。
            #
            # 关于「累计值回退」：当项圈重新通电（或模拟器换了新一轮状态）时，
            # 累计值会从 0 重新开始，此时它**小于**基线。
            # 早期实现把这种情况当作「整段视为新一轮增量」，即 delta = 整个累计值——
            # 这在回放场景下会炸：回放末尾累计值已达 1.9 万，于是单条记录被写成 1.4 万步，
            # 直接污染 step_counts（曾出现「17 时 721,859 步」这种离谱值）。
            # 正确做法是只重置基线、本次不计步：重启前后本来就不该把旧累计值算成新走的路。
            with _state_lock:
                last_cumulative = device_last_cumulative_steps.get(device_id)
                if last_cumulative is None:
                    delta_steps = 0          # 首次上报：仅建立基线
                elif steps_from_device >= last_cumulative:
                    delta_steps = steps_from_device - last_cumulative
                else:
                    delta_steps = 0          # 累计值回退（重通电）→ 仅重置基线，不计步
                device_last_cumulative_steps[device_id] = steps_from_device
                if len(device_last_cumulative_steps) > 5000:
                    device_last_cumulative_steps.clear()
                    device_last_cumulative_steps[device_id] = steps_from_device

            calculated_steps = delta_steps
            cumulative_steps = steps_from_device
            step_frequency = 0
            activity_level = 'low'

            if calculated_steps > 0:
                daily_tracker.add_steps(calculated_steps)

        # ========== 4. 步数异常检测（设备级历史） ==========
        with _state_lock:
            if device_id not in device_step_history:
                device_step_history[device_id] = []

            device_step_history[device_id].append({
                'steps': calculated_steps,
                'timestamp': data.get('timestamp'),
                'posture': posture
            })
            # 保留最近100条
            if len(device_step_history[device_id]) > 100:
                device_step_history[device_id] = device_step_history[device_id][-100:]

            history_steps = [h['steps'] for h in device_step_history[device_id][-30:]]

        if len(history_steps) >= 10:
            step_alert.update_baseline(history_steps)

        anomaly = step_alert.check_anomaly(
            calculated_steps, data.get('timestamp'), device_id)

        if anomaly.get('is_anomaly'):
            logger.warning(
                f"设备 {device_id} 步数异常: {calculated_steps}步, "
                f"正常范围: {anomaly.get('normal_range')}, "
                f"严重程度: {anomaly.get('severity')}"
            )

        # ========== 5. 存储设备状态 ==========
        with _state_lock:
            device_data[device_id] = {
                'device_id': device_id,
                'posture': posture,
                'posture_confidence': posture_confidence,
                'model_type': model_type,
                'data_modality': data_modality,
                'move': move,
                'steps_from_device': steps_from_device,
                'calculated_steps': calculated_steps,
                'cumulative_steps': cumulative_steps,
                'step_frequency': step_frequency,
                'activity_level': activity_level,
                'location': {
                    'lat': latitude,
                    'lng': longitude,
                    'source': gps_processor.get_current_position().get('source')
                },
                'counter': counter,
                'anomaly': anomaly,
                'last_update': data.get('timestamp'),
            }

        daily_summary = daily_tracker.get_daily_summary()

        logger.info(
            f"设备 {device_id} 状态: 姿态={posture}({posture_confidence:.2f}), "
            f"步数={calculated_steps}(累计{cumulative_steps}), "
            f"异常={anomaly.get('is_anomaly')}"
        )

        # ========== 6. 回调通知 Java（SSRF防护） ==========
        callback_url = data.get('callback_url')

        # 转换 Unix 时间戳为 ISO 格式（Java datetime2 列需要字符串格式）
        #
        # 当前协议：Java 侧 MqttMessageHandler 传过来的是 **epoch 秒**（毫秒已 ÷1000），
        # 因此 `datetime.fromtimestamp` 是正确用法。
        # 这里额外做了两点加固，避免以后换协议时静默出错：
        #   1) 兼容毫秒输入（> 1e11 时按毫秒处理）——一旦上游改成直传毫秒，
        #      原写法会把毫秒当秒 → 年份溢出 → 抛错 → 静默回退 datetime.now()，
        #      表现为「回放数据的时间戳全变成运行时刻」，极难排查；
        #   2) 扩大异常捕获范围（含 OSError / OverflowError），并在回退时打日志，
        #      而不是无声降级。
        raw_ts = data.get('timestamp')
        ts_iso = None
        if raw_ts is not None:
            try:
                epoch = float(raw_ts)
                if epoch > 1e11:          # 1e11 秒 ≈ 公元 5138 年，超过则必是毫秒
                    epoch = epoch / 1000.0
                ts_iso = datetime.fromtimestamp(epoch).isoformat()
            except (ValueError, TypeError, OSError, OverflowError) as e:
                logger.warning("解析上报时间戳失败，回退为当前时间: raw_ts=%s, err=%s", raw_ts, e)
        if not ts_iso:
            ts_iso = datetime.now().isoformat()

        ml_result = {
            'device_id': device_id,
            # animal_id 为 null 时用 device_id 兜底（data.get 返回 None 时才用默认值）
            'animal_id': data.get('animal_id') or device_id,
            'posture': posture,
            'posture_confidence': posture_confidence,
            'model_type': model_type,
            'data_modality': data_modality,
            'calculated_steps': calculated_steps,
            'cumulative_steps': cumulative_steps,
            'step_frequency': step_frequency,
            'activity_level': activity_level,
            'anomaly': anomaly,
            'daily_summary': daily_summary,
            # Java datetime2 列需要 ISO 字符串格式
            'timestamp': ts_iso,
            'accel_x': accel_x,
            'accel_y': accel_y,
            'accel_z': accel_z,
            'gyro_x': gyro_x,
            'gyro_y': gyro_y,
            'gyro_z': gyro_z,
            'longitude': longitude,
            'latitude': latitude,
        }

        if callback_url:
            if not _check_callback_host(callback_url):
                logger.error(
                    f"[SSRF拦截] 设备 {device_id} 尝试回调未授权地址: {callback_url}"
                )
            else:
                def _do_callback():
                    try:
                        body = json.dumps(
                            to_json_serializable(ml_result)
                        ).encode('utf-8')
                        req = urllib.request.Request(
                            callback_url,
                            data=body,
                            headers={
                                'Content-Type': 'application/json',
                                # 回调鉴权：Java 端 MlCallbackController 校验该令牌
                                'X-ML-Callback-Token': CALLBACK_TOKEN,
                            }
                        )
                        with urllib.request.urlopen(req, timeout=10) as resp:
                            logger.info(
                                f"Java 回调成功 [{resp.status}]: {callback_url}"
                            )
                    except urllib.error.HTTPError as e:
                        logger.warning(f"Java 回调 HTTP 错误 {e.code}: {callback_url}")
                    except urllib.error.URLError as e:
                        logger.warning(f"Java 回调连接失败: {callback_url} — {e.reason}")
                    except Exception as e:
                        logger.error(f"Java 回调异常: {e}")

                threading.Thread(target=_do_callback, daemon=True).start()

        return jsonify(to_json_serializable({
            'success': True,
            'message': '数据接收成功',
            'device_id': device_id,
            'posture': posture,
            'posture_confidence': posture_confidence,
            'calculated_steps': calculated_steps,
            'cumulative_steps': cumulative_steps,
            'step_frequency': step_frequency,
            'activity_level': activity_level,
            'anomaly': anomaly,
            'daily_summary': daily_summary
        }))

    except Exception as e:
        logger.error(f"处理设备数据失败: {e}")
        logger.error(traceback.format_exc())
        return jsonify({'success': False, 'error': str(e)}), 500


# =========================================================================
# 多源传感行为分析接口（Java BehaviorAnalysisServiceImpl 调用）
# =========================================================================


def _normalize_accel_samples(accel_data) -> list:
    """把 Java 传来的 accel_data 归一化为 [{ax,ay,az,gx,gy,gz}, ...]"""
    samples = []
    if not accel_data:
        return samples
    try:
        if isinstance(accel_data, dict):
            accel_data = [accel_data]
        for item in accel_data:
            if isinstance(item, dict):
                samples.append({
                    'ax': float(item.get('ax', item.get('accel_x', item.get('x', 0))) or 0),
                    'ay': float(item.get('ay', item.get('accel_y', item.get('y', 0))) or 0),
                    'az': float(item.get('az', item.get('accel_z', item.get('z', 9.8))) or 9.8),
                    'gx': float(item.get('gx', item.get('gyro_x', 0)) or 0),
                    'gy': float(item.get('gy', item.get('gyro_y', 0)) or 0),
                    'gz': float(item.get('gz', item.get('gyro_z', 0)) or 0),
                })
            elif isinstance(item, (list, tuple)) and len(item) >= 3:
                samples.append({
                    'ax': float(item[0]), 'ay': float(item[1]), 'az': float(item[2]),
                    'gx': float(item[3]) if len(item) > 3 else 0.0,
                    'gy': float(item[4]) if len(item) > 4 else 0.0,
                    'gz': float(item[5]) if len(item) > 5 else 0.0,
                })
            elif isinstance(item, (int, float)):
                samples.append({'ax': float(item), 'ay': 0.0, 'az': 9.8,
                                'gx': 0.0, 'gy': 0.0, 'gz': 0.0})
    except (TypeError, ValueError) as e:
        logger.warning(f"accel_data 解析失败: {e}")
    return samples


def _normalize_sound_samples(sound_data) -> list:
    """把 sound_data 归一化为 float 列表（支持 [1,2,3] / [{'level':x}] / {'level':x}）"""
    values = []
    if not sound_data:
        return values
    try:
        if isinstance(sound_data, dict):
            sound_data = [sound_data]
        for item in sound_data:
            if isinstance(item, dict):
                v = item.get('level', item.get('sound_level',
                        item.get('sound_intensity', item.get('db', 0))))
                values.append(abs(float(v or 0)))
            elif isinstance(item, (int, float)):
                values.append(abs(float(item)))
    except (TypeError, ValueError) as e:
        logger.warning(f"sound_data 解析失败: {e}")
    return values


def _estimate_chewing_count(sound_samples) -> int:
    """
    基于音频能量的峰值检测粗略估计咀嚼/反刍次数。
    说明：这是轻量启发式算法（非深度学习音频事件模型），用于提供音频通道的辅助特征；
    真实部署时应替换为专用的音频事件检测模型。
    """
    if len(sound_samples) < 3:
        return 0
    arr = np.asarray(sound_samples, dtype=float)
    mean = float(np.mean(arr))
    std = float(np.std(arr))
    if std == 0:
        return 0
    threshold = mean + 0.5 * std
    count = 0
    above = False
    for v in arr:
        if v > threshold and not above:
            count += 1
            above = True
        elif v <= threshold:
            above = False
    return count


def _fuse_behavior(avg_mag, avg_gyro, activity_level, sound_intensity, jaw_movement_rate):
    """
    多源融合判定：
      - 加速度模态 → 运动强度/姿态（站立/躺卧/行走/奔跑）
      - 音频模态   → 咀嚼/反刍节律（采食）
    返回 (behavior, confidence)，behavior ∈ standing/lying/walking/feeding/running
    """
    # 音频模态优先：明显的咀嚼节律 → 采食
    if sound_intensity > 0.3 and jaw_movement_rate > 0.05:
        return 'feeding', 0.8

    # 加速度模态判定（阈值与 posture_model 保持一致）
    if avg_gyro > 2.0 or activity_level > 3.5:
        return 'running', 0.85
    if avg_gyro > 0.6 or activity_level > 1.2:
        return 'walking', 0.8
    if avg_mag < 9.0 and avg_gyro < 0.3:
        return 'lying', 0.75
    return 'standing', 0.75


@app.route('/api/predict/behavior', methods=['POST'])
@_require_auth
def predict_behavior():
    """
    多源传感行为识别（加速度 + 可选音频）。

    请求: {animal_id, start_time, end_time, data_type, accel_data?, sound_data?}
    响应: {success, animal_id, behavior, confidence, timestamp,
           features:{jaw_movement_rate, chewing_count, sound_intensity, activity_level}}
    """
    try:
        req = request.get_json(silent=True) or {}
        animal_id = req.get('animal_id') or req.get('device_id') or 'unknown'

        accel_samples = _normalize_accel_samples(req.get('accel_data'))
        sound_samples = _normalize_sound_samples(req.get('sound_data'))

        if not accel_samples and not sound_samples:
            return jsonify({'success': False,
                            'error': '缺少 accel_data 或 sound_data'}), 400

        # ---- 加速度模态特征 ----
        magnitudes = [np.sqrt(s['ax'] ** 2 + s['ay'] ** 2 + s['az'] ** 2)
                      for s in accel_samples] or [0.0]
        gyro_mags = [np.sqrt(s['gx'] ** 2 + s['gy'] ** 2 + s['gz'] ** 2)
                     for s in accel_samples] or [0.0]
        avg_mag = float(np.mean(magnitudes))
        avg_gyro = float(np.mean(gyro_mags))
        activity_level = float(np.std(magnitudes))  # 加速度动态程度

        # ---- 音频模态特征（可选） ----
        if sound_samples:
            sound_intensity = float(np.mean(np.abs(sound_samples)))
            chewing_count = _estimate_chewing_count(sound_samples)
            jaw_movement_rate = chewing_count / max(len(sound_samples), 1)
        else:
            sound_intensity = 0.0
            chewing_count = 0
            jaw_movement_rate = 0.0

        # ---- 多源融合判定 ----
        behavior, confidence = _fuse_behavior(
            avg_mag, avg_gyro, activity_level, sound_intensity, jaw_movement_rate)

        logger.info(
            f"多源行为识别 - animal={animal_id}, behavior={behavior}, "
            f"accel_avg={avg_mag:.3f}, sound={sound_intensity:.4f}, chew={chewing_count}"
        )

        return jsonify(to_json_serializable({
            'success': True,
            'animal_id': animal_id,
            'behavior': behavior,
            'confidence': float(confidence),
            # 缺陷 B 配套：动态标注结果来源（当前融合判定为规则引擎）与数据模态
            'model_type': 'rule_based',
            'data_modality': 'combined' if (accel_samples and sound_samples)
                            else ('sound' if sound_samples else 'accel'),
            'timestamp': datetime.now().isoformat(),
            'features': {
                'jaw_movement_rate': float(jaw_movement_rate),
                'chewing_count': int(chewing_count),
                'sound_intensity': float(sound_intensity),
                'activity_level': float(activity_level),
            },
        }))

    except Exception as e:
        logger.error(f"行为识别失败: {e}")
        logger.error(traceback.format_exc())
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/count/steps', methods=['POST'])
@_require_auth
def count_steps_api():
    """
    独立步数统计接口（Java StepCountServiceImpl.callStepCountApi 调用）。
    请求: {animal_id, accel_data, timestamp}
    """
    try:
        req = request.get_json(silent=True) or {}
        animal_id = req.get('animal_id') or req.get('device_id') or 'unknown'
        samples = _normalize_accel_samples(req.get('accel_data'))

        if not samples:
            return jsonify({'success': False, 'error': '缺少 accel_data'}), 400

        accel_y_history = [s['ay'] for s in samples]
        timestamps = list(range(len(accel_y_history)))  # 无真实采样时间时按序号递推

        sc = _get_step_counter(animal_id)
        result = sc.count_steps(accel_y_history, timestamps, posture='walking')

        return jsonify(to_json_serializable({
            'success': True,
            'animal_id': animal_id,
            'steps': result.get('total_steps', 0),
            'calculated_steps': result.get('total_steps', 0),
            'cumulative_steps': result.get('cumulative_steps', 0),
            'step_frequency': result.get('step_frequency', 0),
            'activity_level': result.get('activity_level', 'low'),
        }))

    except Exception as e:
        logger.error(f"步数统计失败: {e}")
        logger.error(traceback.format_exc())
        return jsonify({'success': False, 'error': str(e)}), 500


# =========================================================================
# 查询接口
# =========================================================================


@app.route('/api/device/<device_id>', methods=['GET'])
@_require_auth
def get_device(device_id):
    """获取指定设备状态"""
    with _state_lock:
        data = device_data.get(device_id, {})
        history = list(device_step_history.get(device_id, []))
    return jsonify(to_json_serializable({
        'success': True,
        'device_id': device_id,
        'current': data,
        'history': history[-20:],
        'history_count': len(history)
    }))


@app.route('/api/devices', methods=['GET'])
@_require_auth
def get_devices():
    """获取所有设备状态"""
    with _state_lock:
        devices = dict(device_data)
        count = len(devices)
    return jsonify(to_json_serializable({
        'success': True,
        'devices': devices,
        'count': count
    }))


@app.route('/api/steps/daily', methods=['GET'])
@_require_auth
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
@_require_auth
def get_step_history(device_id):
    """获取设备步数历史"""
    with _state_lock:
        history = list(device_step_history.get(device_id, []))
    return jsonify(to_json_serializable({
        'success': True,
        'device_id': device_id,
        'history': history,
        'count': len(history)
    }))


@app.route('/api/alert/summary', methods=['GET'])
@_require_auth
def get_alert_summary():
    """获取预警摘要"""
    return jsonify(to_json_serializable({
        'success': True,
        'alert_summary': step_alert.get_alert_summary(),
        'frontend_data': step_alert.get_frontend_alert_data()
    }))


@app.route('/api/gps/current', methods=['GET'])
@_require_auth
def get_gps_current():
    """获取当前GPS状态"""
    return jsonify(to_json_serializable({
        'success': True,
        'position': gps_processor.get_current_position()
    }))


# =========================================================================
# 健康检查（无需认证）
# =========================================================================

@app.route('/health', methods=['GET'])
def health_check():
    ml_info = {}
    if ml_classifier:
        ml_info = ml_classifier.get_model_info()

    data_stats = {}
    if data_collector:
        data_stats = data_collector.get_statistics()

    with _state_lock:
        device_count = len(device_data)

    llm_info = {}
    if LLM_AVAILABLE_GW:
        gw = get_llm_gateway()
        if gw:
            llm_info = gw.get_capabilities()

    return jsonify({
        'status': 'healthy',
        'service': 'yunmu-python-ml',
        'version': '3.1.0',
        'devices_count': device_count,
        'algorithms': [
            'step_counter', 'posture_classifier',
            'step_alert', 'gps_processor'
        ],
        'ml_enabled': ML_AVAILABLE,
        'ml_model': ml_info,
        'llm_enabled': LLM_AVAILABLE_GW,
        'llm': llm_info,
        'data_stats': data_stats,
        'gps': gps_processor.get_current_position()
    })


# =========================================================================
# LLM 网关接口
# =========================================================================

# 网关失败原因 → (HTTP 状态码, 用户可读文案)
# 改造前所有失败一律返回 500 + 「LLM 生成失败，请稍后重试」，把「Key 失效」
# 「额度不足」「上游超时」「网络不通」压成同一句话，前端与排查者都拿不到信息。
_LLM_ERROR_MAP = {
    'not-configured': (503, 'LLM 服务未配置 API Key，请在 yunmu-python/.env 中配置 DEEPSEEK_API_KEY 或 OPENAI_API_KEY 后重启服务'),
    'auth': (503, 'LLM API Key 无效或已过期，请检查 .env 中的 DEEPSEEK_API_KEY'),
    'quota': (503, 'LLM 账户额度不足或已欠费，请充值后重试'),
    'rate-limit': (429, 'LLM 调用频率超限，请稍后重试'),
    'timeout': (504, 'LLM 上游响应超时，请稍后重试，或把问题描述得更简短'),
    'network': (502, '无法连接 LLM 上游服务，请检查服务器网络或 DNS'),
    'upstream': (502, 'LLM 上游服务返回错误'),
    'empty-response': (502, '模型未生成正文内容，请换一种问法重试'),
    'unknown': (500, 'LLM 调用失败，请稍后重试'),
}


def _llm_error_response(gateway):
    """把网关记录的结构化失败原因转换成 (HTTP 状态码, 响应体)。"""
    err = None
    if hasattr(gateway, 'get_last_error'):
        try:
            err = gateway.get_last_error()
        except Exception:
            err = None
    err = err or {}
    reason = err.get('reason') or 'unknown'
    status, message = _LLM_ERROR_MAP.get(reason, (500, 'LLM 调用失败，请稍后重试'))
    detail = str(err.get('detail') or '').strip()[:200]
    if detail:
        message = '%s（%s）' % (message, detail)
    return status, {
        'success': False,
        'error': message,
        'reason': reason,
        'detail': detail,
        'code': 'LLM_NOT_CONFIGURED' if reason == 'not-configured' else 'LLM_UPSTREAM_ERROR',
    }


@app.route('/api/llm/status', methods=['GET'])
def llm_status():
    """获取 LLM 网关状态"""
    if not LLM_AVAILABLE_GW:
        return jsonify({'success': False, 'error': 'LLM 模块不可用'})
    gateway = get_llm_gateway()
    if gateway is None:
        return jsonify({'success': False, 'error': 'LLM 网关初始化失败'})
    return jsonify(to_json_serializable({
        'success': True,
        'capabilities': gateway.get_capabilities()
    }))


@app.route('/api/llm/explain-anomaly', methods=['POST'])
@_require_auth
def llm_explain_anomaly():
    """LLM 异常解释接口"""
    if not LLM_AVAILABLE_GW:
        return jsonify({'success': False, 'error': 'LLM 模块不可用'}), 500

    gateway = get_llm_gateway()
    if gateway is None or not gateway.is_available():
        return jsonify({'success': False, 'error': 'LLM 服务未配置或 API Key 缺失'}), 503

    data = request.json or {}
    animal_id = data.get('animal_id', 'unknown')
    anomaly_type = data.get('anomaly_type', 'unknown')
    sensor_data = data.get('sensor_data', {})
    history_trend = data.get('history_trend')

    result = gateway.explain_anomaly(animal_id, anomaly_type, sensor_data, history_trend)

    if result is None:
        return jsonify({'success': False, 'error': 'LLM 生成失败，请稍后重试'}), 500

    return jsonify(to_json_serializable({
        'success': True,
        'animal_id': animal_id,
        'anomaly_type': anomaly_type,
        'explanation': result,
        'generated_at': datetime.now().isoformat()
    }))


@app.route('/api/llm/behavior-report', methods=['POST'])
@_require_auth
def llm_behavior_report():
    """LLM 行为报告生成接口"""
    if not LLM_AVAILABLE_GW:
        return jsonify({'success': False, 'error': 'LLM 模块不可用'}), 500

    gateway = get_llm_gateway()
    if gateway is None or not gateway.is_available():
        return jsonify({'success': False, 'error': 'LLM 服务未配置'}), 503

    data = request.json or {}
    animal_id = data.get('animal_id', 'unknown')
    date = data.get('date', datetime.now().strftime('%Y-%m-%d'))
    behavior_stats = data.get('behavior_stats', {})
    health_data = data.get('health_data')
    location_data = data.get('location_data')

    result = gateway.generate_behavior_report(
        animal_id, date, behavior_stats, health_data, location_data)

    if result is None:
        return jsonify({'success': False, 'error': 'LLM 生成失败'}), 500

    return jsonify(to_json_serializable({
        'success': True,
        'animal_id': animal_id,
        'report_date': date,
        'report': result,
        'generated_at': datetime.now().isoformat()
    }))


@app.route('/api/llm/behavior-summary', methods=['POST'])
@_require_auth
def llm_behavior_summary():
    """LLM 行为序列语义化接口"""
    if not LLM_AVAILABLE_GW:
        return jsonify({'success': False, 'error': 'LLM 模块不可用'}), 500

    gateway = get_llm_gateway()
    if gateway is None or not gateway.is_available():
        return jsonify({'success': False, 'error': 'LLM 服务未配置'}), 503

    data = request.json or {}
    animal_id = data.get('animal_id', 'unknown')
    posture_sequence = data.get('posture_sequence', [])
    step_summary = data.get('step_summary', {})

    result = gateway.summarize_behavior_sequence(animal_id, posture_sequence, step_summary)

    if result is None:
        return jsonify({'success': False, 'error': 'LLM 生成失败'}), 500

    return jsonify(to_json_serializable({
        'success': True,
        'animal_id': animal_id,
        'summary': result,
        'generated_at': datetime.now().isoformat()
    }))


@app.route('/api/llm/chat', methods=['POST'])
@_require_auth
def llm_chat():
    """AI 助手多轮对话接口（前端对话模块使用，非流式）"""
    if not LLM_AVAILABLE_GW:
        return jsonify({'success': False, 'error': 'LLM 模块不可用'}), 500

    gateway = get_llm_gateway()
    if gateway is None or not gateway.is_available():
        return jsonify({
            'success': False,
            'error': 'LLM 服务未配置或 API Key 缺失，请在 yunmu-python/.env 中设置 '
                     'DEEPSEEK_API_KEY（或 OPENAI_API_KEY）后重启服务'
        }), 503

    data = request.json or {}
    message = str(data.get('message') or '').strip()
    if not message:
        return jsonify({'success': False, 'error': '消息不能为空'}), 400

    history = data.get('history') or []
    if not isinstance(history, list):
        history = []

    result = gateway.chat(history, message)
    if result is None:
        status, body = _llm_error_response(gateway)
        logger.warning("AI 助手对话失败: reason=%s status=%s detail=%s",
                       body.get('reason'), status, body.get('detail'))
        return jsonify(body), status

    return jsonify(to_json_serializable({
        'success': True,
        'reply': result,
        'provider': gateway.provider,
        'model': getattr(gateway, '_model', ''),
        'generated_at': datetime.now().isoformat()
    }))


# =========================================================================
# 机器学习相关接口
# =========================================================================


@app.route('/api/ml/generate-data', methods=['POST'])
@_require_auth
def generate_training_data():
    """生成合成训练数据"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500

    try:
        num_samples = 500
        if request.json:
            num_samples = request.json.get('samples_per_class', 500)

        result = data_collector.generate_synthetic_data(
            num_samples_per_class=num_samples)
        data_collector.flush()  # 立即刷盘
        return jsonify(to_json_serializable({
            'success': True,
            'message': '训练数据生成完成',
            'result': result,
            'stats': data_collector.get_statistics()
        }))
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/ml/train', methods=['POST'])
@_require_auth
def train_model():
    """训练机器学习模型"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500

    try:
        model_type = 'random_forest'
        if request.json:
            model_type = request.json.get('model_type', 'random_forest')

        X, y = data_collector.get_training_data()
        if X is None or len(X) < 50:
            return jsonify({
                'success': False,
                'error': '训练数据不足，请先生成或收集数据'
            }), 400

        trainer = PostureTrainer()
        results = trainer.train(X, y, model_type=model_type)
        model_path = trainer.save_model(f"posture_{model_type}")

        # 重新加载模型
        global ml_classifier
        ml_classifier = MLPostureClassifier()

        return jsonify(to_json_serializable({
            'success': True,
            'message': f'{model_type} 模型训练完成',
            'results': results,
            'model_path': model_path
        }))
    except Exception as e:
        logger.error(f"训练失败: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/ml/model/status', methods=['GET'])
@_require_auth
def get_model_status():
    """获取模型状态"""
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500

    ml_info = {}
    if ml_classifier:
        ml_info = ml_classifier.get_model_info()

    data_stats = {}
    if data_collector:
        data_stats = data_collector.get_statistics()

    return jsonify(to_json_serializable({
        'success': True,
        'ml_available': ML_AVAILABLE,
        'model_info': ml_info,
        'data_stats': data_stats
    }))


@app.route('/api/ml/predict', methods=['POST'])
def ml_predict():
    """
    测试ML预测（公开接口，无需认证）
    用于外部快速测试推理
    """
    if not ML_AVAILABLE:
        return jsonify({'success': False, 'error': '机器学习模块不可用'}), 500

    try:
        data = request.json or {}
        result = ml_classifier.predict(
            accel_x=data.get('accel_x', 0.0),
            accel_y=data.get('accel_y', 0.0),
            accel_z=data.get('accel_z', 9.8),
            gyro_x=data.get('gyro_x', 0.0),
            gyro_y=data.get('gyro_y', 0.0),
            gyro_z=data.get('gyro_z', 0.0)
        )
        return jsonify(to_json_serializable({'success': True, 'result': result}))
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# =========================================================================
# 优雅退出（信号处理）
# =========================================================================


def _shutdown_handler(signum=None, frame=None):
    """进程退出时：确保数据落盘"""
    logger.info("收到退出信号，正在保存数据...")
    if data_collector:
        try:
            data_collector.flush()
            logger.info("数据已保存到磁盘")
        except Exception as e:
            logger.error(f"保存数据时出错: {e}")
    logger.info("服务退出完成")
    sys.exit(0)


import signal
signal.signal(signal.SIGINT, _shutdown_handler)
signal.signal(signal.SIGTERM, _shutdown_handler)


if __name__ == '__main__':
    logger.info("=" * 50)
    logger.info("云牧 ML 服务启动 (v3.1.0)")
    logger.info("API Token: " + ("已设置" if API_TOKEN else "未设置"))
    logger.info("=" * 50)
    app.run(host='0.0.0.0', port=5000, debug=False)
