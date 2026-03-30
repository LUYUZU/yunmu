# models/step_counter.py
import numpy as np
from typing import Dict, List, Any, Tuple
from collections import deque
import logging

logger = logging.getLogger(__name__)


class StepCounter:
    """步数计数器 - 双参数融合计步算法"""

    def __init__(self, accel_threshold: float = 0.3, gyro_threshold: float = 0.5,
                 min_step_interval: float = 0.3, filter_window: int = 5):
        """
        初始化步数计数器
        :param accel_threshold: 线性加速度阈值 (g)
        :param gyro_threshold: 角加速度阈值 (rad/s²)
        :param min_step_interval: 最小步间隔 (秒)
        :param filter_window: 均值滤波窗口大小
        """
        self.accel_threshold = accel_threshold
        self.gyro_threshold = gyro_threshold
        self.min_step_interval = min_step_interval
        self.filter_window = filter_window

        self.total_steps = 0
        self.last_step_time = 0
        self.step_history = deque(maxlen=1000)

        # 异常晃动检测
        self.consecutive_small_cycles = 0
        self.last_cycle_periods = deque(maxlen=3)

        # 姿态联动状态
        self.current_posture = 'standing'  # 当前姿态
        self.sampling_rate = 50  # 默认采样率
        self.low_power_mode = False

        self.avg_step_length = 0.6  # 平均步长 (米)

    def set_posture(self, posture: str):
        """设置当前姿态，用于联动"""
        self.current_posture = posture

        # 姿态联动控制
        if posture == 'lying':
            self.low_power_mode = True
        else:
            self.low_power_mode = False

    def get_sampling_rate(self) -> int:
        """根据姿态获取采样率"""
        if self.current_posture == 'standing' or self.current_posture == 'feeding':
            return 25  # 低频率采样
        elif self.current_posture == 'lying':
            return 5  # 超低功耗模式
        else:
            return 50  # 正常采样率

    def detect_step(self, accel_y: float, gyro_angular_accel: float,
                    timestamp: float) -> Tuple[bool, int]:
        """
        双参数融合计步检测
        :param accel_y: Y轴加速度 (g)
        :param gyro_angular_accel: 俯仰角角加速度 (rad/s²)
        :param timestamp: 时间戳
        :return: (是否检测到步数, 累计步数)
        """
        # 姿态联动：躺卧时暂停计步
        if self.current_posture == 'lying':
            return False, self.total_steps

        try:
            # 线性加速度条件
            accel_condition = abs(accel_y) >= self.accel_threshold

            # 角加速度条件
            gyro_condition = abs(gyro_angular_accel) >= self.gyro_threshold

            # 双参数融合：两个条件在相同时段内同时满足
            if accel_condition and gyro_condition:
                # 检查时间间隔
                if timestamp - self.last_step_time >= self.min_step_interval:

                    # 异常晃动过滤
                    if self._is_valid_step(timestamp):
                        self.total_steps += 1
                        self.last_step_time = timestamp

                        # 记录步数历史
                        self.step_history.append({
                            'timestamp': timestamp,
                            'accel_y': accel_y,
                            'gyro_angular_accel': gyro_angular_accel
                        })

                        return True, self.total_steps

            return False, self.total_steps

        except Exception as e:
            logger.error(f"步数检测失败: {e}")
            return False, self.total_steps

    def _is_valid_step(self, timestamp: float) -> bool:
        """异常晃动过滤"""
        if len(self.step_history) < 3:
            return True

        # 计算最近3次步数的周期
        recent_steps = list(self.step_history)[-3:]
        periods = []

        for i in range(len(recent_steps) - 1):
            periods.append(recent_steps[i + 1]['timestamp'] - recent_steps[i]['timestamp'])

        if len(periods) < 2:
            return True

        # 连续3次周期内波动小于阈值则不计步
        period_std = np.std(periods)
        period_mean = np.mean(periods)

        if period_mean > 0 and period_std / period_mean < 0.1:
            self.consecutive_small_cycles += 1
            if self.consecutive_small_cycles >= 3:
                return False
        else:
            self.consecutive_small_cycles = 0

        return True

    def count_steps_from_data(self, accel_data: List[float],
                              timestamps: List[float]) -> Dict[str, Any]:
        """基于单轴加速度数据统计步数"""
        if not accel_data or not timestamps:
            return {'success': False, 'error': '数据为空'}

        try:
            self.total_steps = 0
            self.last_step_time = 0
            self.step_history.clear()

            # 获取采样率
            self.sampling_rate = self.get_sampling_rate()

            # 均值滤波
            accel_filtered = self._moving_average(accel_data, self.filter_window)

            # 计算角加速度（从加速度估算）
            gyro_angular_accel = self._estimate_angular_accel(accel_filtered, timestamps)

            step_times = []

            for i, (accel, ts, ang_accel) in enumerate(zip(accel_filtered, timestamps, gyro_angular_accel)):
                detected, _ = self.detect_step(accel, ang_accel, ts)
                if detected:
                    step_times.append(ts)

            # 计算步频
            step_frequency = self._calculate_step_frequency(step_times)

            # 估算行走距离
            walking_distance = self.total_steps * self.avg_step_length

            # 计算活动时长
            active_duration = len(step_times) * self.min_step_interval if step_times else 0

            # 评估活动水平
            activity_level = self._determine_activity_level(self.total_steps)

            return {
                'success': True,
                'total_steps': self.total_steps,
                'step_frequency': step_frequency,
                'walking_distance': walking_distance,
                'active_duration': active_duration,
                'activity_level': activity_level,
                'step_times': step_times,
                'current_posture': self.current_posture,
                'low_power_mode': self.low_power_mode
            }

        except Exception as e:
            logger.error(f"步数统计失败: {e}")
            return {'success': False, 'error': str(e)}

    def count_steps_from_accel_xyz(self, accel_x: List[float], accel_y: List[float],
                                   accel_z: List[float], timestamps: List[float],
                                   gyro_pitch_rate: List[float] = None) -> Dict[str, Any]:
        """基于三轴加速度和角速度统计步数"""
        if not accel_y or not timestamps:
            return {'success': False, 'error': '数据为空'}

        try:
            self.total_steps = 0
            self.last_step_time = 0
            self.step_history.clear()

            # 获取采样率
            self.sampling_rate = self.get_sampling_rate()

            # 计算合加速度（用于参考）
            accel_magnitude = [
                np.sqrt(x ** 2 + y ** 2 + z ** 2)
                for x, y, z in zip(accel_x, accel_y, accel_z)
            ]

            # 均值滤波
            accel_y_filtered = self._moving_average(accel_y, self.filter_window)

            # 计算角加速度
            if gyro_pitch_rate is None:
                gyro_angular_accel = self._estimate_angular_accel(accel_y_filtered, timestamps)
            else:
                gyro_angular_accel = self._calculate_angular_accel(gyro_pitch_rate, timestamps)

            step_times = []

            for i, (accel, ts, ang_accel) in enumerate(zip(accel_y_filtered, timestamps, gyro_angular_accel)):
                detected, _ = self.detect_step(accel, ang_accel, ts)
                if detected:
                    step_times.append(ts)

            # 计算步频
            step_frequency = self._calculate_step_frequency(step_times)

            # 估算行走距离
            walking_distance = self.total_steps * self.avg_step_length

            # 计算活动时长
            active_duration = len(step_times) * self.min_step_interval if step_times else 0

            # 评估活动水平
            activity_level = self._determine_activity_level(self.total_steps)

            return {
                'success': True,
                'total_steps': self.total_steps,
                'step_frequency': step_frequency,
                'walking_distance': walking_distance,
                'active_duration': active_duration,
                'activity_level': activity_level,
                'step_times': step_times,
                'current_posture': self.current_posture,
                'low_power_mode': self.low_power_mode
            }

        except Exception as e:
            logger.error(f"步数统计失败: {e}")
            return {'success': False, 'error': str(e)}

    def _moving_average(self, data: List[float], window_size: int) -> List[float]:
        """均值滤波"""
        if window_size <= 1 or len(data) < window_size:
            return data

        result = []
        window = deque(maxlen=window_size)

        for val in data:
            window.append(val)
            if len(window) == window_size:
                result.append(np.mean(window))
            else:
                result.append(val)

        return result

    def _calculate_angular_accel(self, angular_velocity: List[float],
                                 timestamps: List[float]) -> List[float]:
        """计算角加速度"""
        angular_accel = []

        for i in range(len(angular_velocity) - 1):
            dt = timestamps[i + 1] - timestamps[i]
            if dt > 0:
                accel = (angular_velocity[i + 1] - angular_velocity[i]) / dt
                angular_accel.append(abs(accel))
            else:
                angular_accel.append(0)

        # 补齐最后一个点
        if len(angular_accel) < len(angular_velocity):
            angular_accel.append(angular_accel[-1] if angular_accel else 0)

        return angular_accel

    def _estimate_angular_accel(self, accel: List[float],
                                timestamps: List[float]) -> List[float]:
        """从加速度估算角加速度"""
        angular_accel = []

        for i in range(len(accel) - 1):
            dt = timestamps[i + 1] - timestamps[i]
            if dt > 0:
                accel_rate = (accel[i + 1] - accel[i]) / dt
                angular_accel.append(abs(accel_rate) * 0.5)
            else:
                angular_accel.append(0)

        if len(angular_accel) < len(accel):
            angular_accel.append(angular_accel[-1] if angular_accel else 0)

        return angular_accel

    def _calculate_step_frequency(self, step_times: List[float]) -> float:
        """计算步频 (步/分钟)"""
        if len(step_times) < 2:
            return 0.0

        intervals = [step_times[i + 1] - step_times[i] for i in range(len(step_times) - 1)]
        avg_interval = np.mean(intervals) if intervals else 1.0

        if avg_interval > 0:
            return 60.0 / avg_interval
        return 0.0

    def _determine_activity_level(self, step_count: int) -> str:
        """确定活动水平"""
        if step_count < 500:
            return 'low'
        elif step_count < 2000:
            return 'medium'
        else:
            return 'high'

    def reset(self):
        """重置计步器"""
        self.total_steps = 0
        self.last_step_time = 0
        self.step_history.clear()
        self.consecutive_small_cycles = 0

    def get_step_statistics(self, recent_steps: int = 100) -> Dict[str, Any]:
        """获取步数统计"""
        recent = list(self.step_history)[-recent_steps:] if self.step_history else []

        if not recent:
            return {
                'total_steps': self.total_steps,
                'recent_steps': 0,
                'average_accel': 0.0
            }

        avg_accel = np.mean([s['accel_y'] for s in recent])

        return {
            'total_steps': self.total_steps,
            'recent_steps': len(recent),
            'average_accel': float(avg_accel),
            'is_walking': self.total_steps > 0,
            'current_posture': self.current_posture,
            'low_power_mode': self.low_power_mode
        }


class DailyStepTracker:
    """每日步数追踪器"""

    def __init__(self):
        self.daily_steps = {}
        self.current_date = None
        self.current_steps = 0

    def add_steps(self, step_count: int, timestamp: float = None) -> Dict[str, Any]:
        """添加步数"""
        import datetime

        today = datetime.datetime.now().strftime('%Y-%m-%d')

        if self.current_date != today:
            if self.current_date:
                self.daily_steps[self.current_date] = self.current_steps
            self.current_date = today
            self.current_steps = 0

        self.current_steps += step_count

        return {
            'date': today,
            'steps_today': self.current_steps,
            'total_steps': self.current_steps
        }

    def get_daily_summary(self, date: str = None) -> Dict[str, Any]:
        """获取每日摘要"""
        import datetime
        if date is None:
            date = datetime.datetime.now().strftime('%Y-%m-%d')

        if date == self.current_date:
            steps = self.current_steps
        else:
            steps = self.daily_steps.get(date, 0)

        # 目标步数（可根据需要调整）
        goal = 10000
        completion = min(steps / goal * 100, 100)

        return {
            'date': date,
            'steps': steps,
            'goal': goal,
            'completion': completion,
            'status': 'achieved' if steps >= goal else 'in_progress'
        }

    def get_weekly_summary(self) -> Dict[str, Any]:
        """获取周摘要"""
        import datetime

        weekly_data = {}
        today = datetime.datetime.now()

        for i in range(7):
            date = (today - datetime.timedelta(days=i)).strftime('%Y-%m-%d')
            steps = self.daily_steps.get(date, 0)
            if date == self.current_date:
                steps = self.current_steps
            weekly_data[date] = steps

        total_steps = sum(weekly_data.values())

        return {
            'weekly_data': weekly_data,
            'total_steps': total_steps,
            'average_daily': total_steps / 7 if total_steps > 0 else 0,
            'best_day': max(weekly_data.items(), key=lambda x: x[1]) if weekly_data else None
        }

    def reset_daily(self):
        """重置每日步数"""
        if self.current_date:
            self.daily_steps[self.current_date] = self.current_steps
        self.current_steps = 0