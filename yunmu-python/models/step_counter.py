# models/step_counter.py - 步数统计模块
import numpy as np
from typing import Dict, List, Tuple  # 添加这行导入
import logging

logger = logging.getLogger(__name__)


class StepCounter:
    """双参数融合计步算法"""

    def __init__(self, accel_threshold: float = 0.3, gyro_threshold: float = 0.5,
                 min_step_interval: float = 0.3, filter_window: int = 5):
        self.accel_threshold = accel_threshold
        self.gyro_threshold = gyro_threshold
        self.min_step_interval = min_step_interval
        self.filter_window = filter_window
        self.total_steps = 0
        self.last_step_time = 0
        self.step_history = []
        self.current_posture = 'standing'
        self.consecutive_small_cycles = 0

    def set_posture(self, posture: str):
        """姿态联动"""
        self.current_posture = posture

    def detect_step(self, accel_y: float, gyro_angular_accel: float, timestamp: float) -> Tuple[bool, int]:
        """双参数融合计步检测"""
        if self.current_posture == 'lying':
            return False, self.total_steps

        accel_condition = abs(accel_y) >= self.accel_threshold
        gyro_condition = abs(gyro_angular_accel) >= self.gyro_threshold

        if accel_condition and gyro_condition:
            if timestamp - self.last_step_time >= self.min_step_interval:
                if self._is_valid_step(timestamp):
                    self.total_steps += 1
                    self.last_step_time = timestamp
                    self.step_history.append({'timestamp': timestamp, 'accel_y': accel_y})
                    return True, self.total_steps
        return False, self.total_steps

    def _is_valid_step(self, timestamp: float) -> bool:
        """异常晃动过滤"""
        if len(self.step_history) < 3:
            return True
        recent = self.step_history[-3:]
        periods = [recent[i + 1]['timestamp'] - recent[i]['timestamp'] for i in range(len(recent) - 1)]
        if len(periods) >= 2:
            period_std = np.std(periods)
            period_mean = np.mean(periods)
            if period_mean > 0 and period_std / period_mean < 0.1:
                self.consecutive_small_cycles += 1
                if self.consecutive_small_cycles >= 3:
                    return False
            else:
                self.consecutive_small_cycles = 0
        return True

    def count_steps(self, accel_y: List[float], timestamps: List[float]) -> Dict:
        """统计步数"""
        if not accel_y or not timestamps:
            return {
                'success': False,
                'error': '数据为空',
                'total_steps': 0,
                'step_frequency': 0.0,
                'walking_distance': 0.0,
                'activity_level': 'low',
                'current_posture': self.current_posture
            }

        self.total_steps = 0
        self.last_step_time = 0
        self.step_history = []

        accel_filtered = self._moving_average(accel_y, self.filter_window)
        gyro_accel = self._estimate_angular_accel(accel_filtered, timestamps)

        step_times = []
        for i, (accel, ts, ang_accel) in enumerate(zip(accel_filtered, timestamps, gyro_accel)):
            detected, _ = self.detect_step(accel, ang_accel, ts)
            if detected:
                step_times.append(ts)

        step_freq = self._calculate_step_frequency(step_times)
        walking_dist = self.total_steps * 0.6
        activity_level = self._determine_activity_level(self.total_steps)

        return {
            'success': True,
            'total_steps': self.total_steps,
            'step_frequency': step_freq,
            'walking_distance': walking_dist,
            'activity_level': activity_level,
            'current_posture': self.current_posture,
            'step_times': step_times
        }

    def _moving_average(self, data: List[float], window: int) -> List[float]:
        if window <= 1 or len(data) < window:
            return data
        result = []
        for i in range(len(data)):
            start = max(0, i - window + 1)
            result.append(float(np.mean(data[start:i + 1])))
        return result

    def _estimate_angular_accel(self, accel: List[float], timestamps: List[float]) -> List[float]:
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

    def _calculate_step_frequency(self, step_times: List[float]) -> float:
        if len(step_times) < 2:
            return 0.0
        intervals = [step_times[i + 1] - step_times[i] for i in range(len(step_times) - 1)]
        avg_interval = np.mean(intervals) if intervals else 1.0
        return 60.0 / avg_interval if avg_interval > 0 else 0.0

    def _determine_activity_level(self, step_count: int) -> str:
        if step_count < 500:
            return 'low'
        elif step_count < 2000:
            return 'medium'
        return 'high'


class DailyStepTracker:
    """每日步数追踪"""

    def __init__(self):
        self.daily_steps = {}
        self.current_date = None
        self.current_steps = 0

    def add_steps(self, steps: int) -> Dict:
        import datetime
        today = datetime.datetime.now().strftime('%Y-%m-%d')
        if self.current_date != today:
            if self.current_date:
                self.daily_steps[self.current_date] = self.current_steps
            self.current_date = today
            self.current_steps = 0
        self.current_steps += steps
        return {'date': today, 'steps_today': self.current_steps, 'total_steps': self.current_steps}

    def get_daily_summary(self) -> Dict:
        import datetime
        date = datetime.datetime.now().strftime('%Y-%m-%d')
        steps = self.current_steps if date == self.current_date else self.daily_steps.get(date, 0)
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