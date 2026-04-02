# models/step_alert.py - 步数异常预警模块
import numpy as np
from typing import Dict, List  # 确保导入
import logging

logger = logging.getLogger(__name__)


class StepAlert:
    """步数异常预警 - 基于IQR算法"""

    def __init__(self, history_days: int = 10):
        self.step_history = []
        self.alert_history = []
        self.history_days = history_days
        self.q1 = self.q3 = self.iqr = self.lower_bound = self.upper_bound = 0

    def update_baseline(self, steps: List[int]):
        if len(steps) < 10:
            return
        steps_array = np.array(steps)
        self.q1 = np.percentile(steps_array, 25)
        self.q3 = np.percentile(steps_array, 75)
        self.iqr = self.q3 - self.q1
        self.lower_bound = self.q1 - 1.5 * self.iqr
        self.upper_bound = self.q3 + 1.5 * self.iqr

    def check_anomaly(self, current_steps: int, timestamp: float = None,
                      animal_id: str = None) -> Dict:
        if self.iqr == 0:
            return {'is_anomaly': False, 'reason': '基线未建立'}

        is_anomaly = bool(current_steps < self.lower_bound or current_steps > self.upper_bound)
        severity = self._calculate_severity(current_steps)

        result = {
            'is_anomaly': is_anomaly,
            'current_steps': int(current_steps),
            'normal_range': [float(self.lower_bound), float(self.upper_bound)],
            'severity': severity,
            'timestamp': timestamp,
            'animal_id': animal_id
        }

        if is_anomaly:
            self.alert_history.append(result)

        return result

    def _calculate_severity(self, steps: int) -> str:
        if steps < self.lower_bound:
            deviation = (self.lower_bound - steps) / (self.q1 or 1)
        else:
            deviation = (steps - self.upper_bound) / (self.q3 or 1)

        if deviation > 2.0:
            return 'critical'
        elif deviation > 1.0:
            return 'warning'
        else:
            return 'info'

    def get_alert_summary(self) -> Dict:
        severity_counts = {'critical': 0, 'warning': 0, 'info': 0}
        for alert in self.alert_history:
            severity_counts[alert.get('severity', 'info')] += 1

        return {
            'total_alerts': len(self.alert_history),
            'severity_distribution': severity_counts,
            'current_normal_range': [float(self.lower_bound), float(self.upper_bound)],
            'latest_alerts': self.alert_history[-5:] if self.alert_history else []
        }

    def get_frontend_alert_data(self) -> Dict:
        return {
            'has_anomaly': len(self.alert_history) > 0,
            'current_bound': {'lower': float(self.lower_bound), 'upper': float(self.upper_bound)},
            'alert_count': len(self.alert_history)
        }