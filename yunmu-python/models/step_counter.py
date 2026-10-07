# models/step_counter.py - 步数统计模块
import numpy as np
from typing import Dict, List, Tuple
import logging
import threading

logger = logging.getLogger(__name__)

# -----------------------------------------------------------------------------
# 工具函数（纯函数，无状态）
# -----------------------------------------------------------------------------


def _moving_average(data: List[float], window: int) -> List[float]:
    """滑动平均滤波"""
    if window <= 1 or len(data) < window:
        return data
    result = []
    for i in range(len(data)):
        start = max(0, i - window + 1)
        result.append(float(np.mean(data[start:i + 1])))
    return result


def _estimate_angular_accel(accel: List[float], timestamps: List[float]) -> List[float]:
    """从加速度序列估算角加速度"""
    angular_accel = []
    for i in range(len(accel) - 1):
        dt = timestamps[i + 1] - timestamps[i]
        if dt > 0:
            accel_rate = (accel[i + 1] - accel[i]) / dt
            angular_accel.append(abs(accel_rate) * 0.5)
        else:
            angular_accel.append(0.0)
    if len(angular_accel) < len(accel):
        angular_accel.append(angular_accel[-1] if angular_accel else 0.0)
    return angular_accel


def _is_valid_step_local(
    recent: List[Dict], consecutive_small: int
) -> Tuple[bool, int]:
    """
    异常晃动过滤（纯函数版本）
    返回 (是否有效, 新的consecutive_small_cycles计数)
    """
    if len(recent) < 3:
        return True, consecutive_small

    periods = [recent[i + 1]['timestamp'] - recent[i]['timestamp']
               for i in range(len(recent) - 1)]

    if len(periods) >= 2:
        period_std = np.std(periods)
        period_mean = np.mean(periods)
        if period_mean > 0 and period_std / period_mean < 0.1:
            consecutive_small += 1
            if consecutive_small >= 3:
                return False, consecutive_small
        else:
            consecutive_small = 0

    return True, consecutive_small


def _detect_steps_in_batch(
    accel_filtered: List[float],
    timestamps: List[float],
    gyro_accel: List[float],
    posture: str,
    accel_threshold: float = 0.3,
    gyro_threshold: float = 0.5,
    min_step_interval: float = 0.15,  # 修复：0.15s → 最大 400步/分钟
    filter_window: int = 5
) -> Tuple[int, List[float], float]:
    """
    在一批加速度数据中检测步数（完全无状态，不会污染外部状态）

    Returns:
        (步数, [每步时间戳列表], 步频)
    """
    if posture == 'lying':
        return 0, [], 0.0

    total_steps = 0
    step_times = []
    last_step_time = 0.0
    step_history = []
    consecutive_small = 0

    for i, (accel_y_val, ts, ang_accel) in enumerate(
            zip(accel_filtered, timestamps, gyro_accel)):

        accel_cond = abs(accel_y_val) >= accel_threshold
        gyro_cond = abs(ang_accel) >= gyro_threshold

        if accel_cond and gyro_cond:
            if ts - last_step_time >= min_step_interval:
                valid, consecutive_small = _is_valid_step_local(
                    step_history[-3:] if step_history else [], consecutive_small
                )
                if valid:
                    total_steps += 1
                    last_step_time = ts
                    step_history.append({'timestamp': ts, 'accel_y': accel_y_val})

    # 计算步频
    if len(step_times) < 2:
        step_freq = 0.0
    else:
        intervals = [step_times[i + 1] - step_times[i]
                     for i in range(len(step_times) - 1)]
        avg_interval = np.mean(intervals)
        step_freq = 60.0 / avg_interval if avg_interval > 0 else 0.0

    return total_steps, step_times, step_freq


# -----------------------------------------------------------------------------
# StepCounter — 线程安全的全局步数累加器
# -----------------------------------------------------------------------------


class StepCounter:
    """双参数融合计步算法（线程安全，无状态污染）"""

    def __init__(self, accel_threshold: float = 0.3, gyro_threshold: float = 0.5,
                 min_step_interval: float = 0.15, filter_window: int = 5):
        self.accel_threshold = accel_threshold
        self.gyro_threshold = gyro_threshold
        self.min_step_interval = min_step_interval
        self.filter_window = filter_window

        # 全局步数（跨多次调用累加）
        self._lock = threading.Lock()
        self.total_steps = 0
        self.last_processed_timestamp = 0.0

    def set_posture(self, posture: str):
        """姿态联动（留空，行为已整合到 count_steps）"""
        pass

    def count_steps(self, accel_y: List[float], timestamps: List[float],
                    posture: str = 'standing') -> Dict:
        """
        统计步数（无状态，不污染实例变量）

        Args:
            accel_y: 加速度Y轴历史序列
            timestamps: 对应时间戳
            posture: 当前姿态，lying 时直接返回0

        Returns:
            步数统计结果
        """
        if not accel_y or not timestamps:
            return {
                'success': False,
                'error': '数据为空',
                'total_steps': 0,
                'step_frequency': 0.0,
                'walking_distance': 0.0,
                'activity_level': 'low'
            }

        # 预处理
        accel_filtered = _moving_average(accel_y, self.filter_window)
        gyro_accel = _estimate_angular_accel(accel_filtered, timestamps)

        # 无状态计步
        batch_steps, step_times, step_freq = _detect_steps_in_batch(
            accel_filtered, timestamps, gyro_accel,
            posture=posture,
            accel_threshold=self.accel_threshold,
            gyro_threshold=self.gyro_threshold,
            min_step_interval=self.min_step_interval
        )

        # 安全地累加到全局计数器（去重：忽略时间戳不递增的数据包）
        if batch_steps > 0:
            with self._lock:
                if timestamps[-1] > self.last_processed_timestamp:
                    self.total_steps += batch_steps
                    self.last_processed_timestamp = timestamps[-1]

        walking_dist = batch_steps * 0.6
        activity_level = _determine_activity_level(batch_steps)

        return {
            'success': True,
            'total_steps': batch_steps,       # 本次检测步数
            'cumulative_steps': self.total_steps,  # 全局累计（需加锁读取）
            'step_frequency': step_freq,
            'walking_distance': walking_dist,
            'activity_level': activity_level,
            'step_times': step_times
        }

    def reset(self):
        """手动重置计数器（测试用）"""
        with self._lock:
            self.total_steps = 0
            self.last_processed_timestamp = 0.0


def _determine_activity_level(step_count: int) -> str:
    if step_count < 500:
        return 'low'
    elif step_count < 2000:
        return 'medium'
    return 'high'


# -----------------------------------------------------------------------------
# DailyStepTracker — 每日步数追踪
# -----------------------------------------------------------------------------


class DailyStepTracker:
    """每日步数追踪（线程安全）"""

    def __init__(self):
        self._lock = threading.Lock()
        self.daily_steps: Dict[str, int] = {}
        self.current_date: str = None
        self.current_steps: int = 0

    def add_steps(self, steps: int) -> Dict:
        import datetime
        today = datetime.datetime.now().strftime('%Y-%m-%d')

        with self._lock:
            if self.current_date != today:
                if self.current_date:
                    self.daily_steps[self.current_date] = self.current_steps
                self.current_date = today
                self.current_steps = 0

            if steps > 0:
                self.current_steps += steps

            return {
                'date': today,
                'steps_today': self.current_steps,
                'total_steps': self.current_steps
            }

    def get_daily_summary(self) -> Dict:
        import datetime
        date = datetime.datetime.now().strftime('%Y-%m-%d')

        with self._lock:
            steps = self.current_steps if date == self.current_date \
                else self.daily_steps.get(date, 0)

        goal = 10000
        return {
            'date': date,
            'steps': steps,
            'goal': goal,
            'completion': min(steps / goal * 100, 100),
            'status': 'achieved' if steps >= goal else 'in_progress'
        }

    def get_weekly_summary(self) -> Dict:
        import datetime
        weekly_data = {}
        today = datetime.datetime.now()

        with self._lock:
            for i in range(7):
                date = (today - datetime.timedelta(days=i)).strftime('%Y-%m-%d')
                steps = self.daily_steps.get(date, 0)
                if date == self.current_date:
                    steps = self.current_steps
                weekly_data[date] = steps
            total = sum(weekly_data.values())

        return {
            'weekly_data': weekly_data,
            'total_steps': total,
            'average_daily': total / 7 if total > 0 else 0
        }
