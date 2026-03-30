# models/step_alert.py
import numpy as np
from typing import Dict, List, Any, Optional
from collections import deque
import logging
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import json
import requests

logger = logging.getLogger(__name__)


class StepAlert:
    """步数异常预警机制 - 基于IQR算法"""

    def __init__(self, config: Dict = None):
        self.config = config or {}
        self.step_history = deque(maxlen=1000)  # 历史步数记录

        # IQR参数
        self.q1 = 0
        self.q3 = 0
        self.iqr = 0
        self.lower_bound = 0
        self.upper_bound = 0

        # 预警记录
        self.alert_history = deque(maxlen=100)

        # 邮件配置
        self.smtp_config = self.config.get('smtp', {
            'server': 'smtp.gmail.com',
            'port': 587,
            'username': None,
            'password': None
        })

        # 推送配置
        self.webhook_url = self.config.get('webhook_url', None)

    def update_baseline(self, steps: List[int]):
        """更新基线步数统计"""
        if len(steps) < 10:
            return

        steps_array = np.array(steps)
        self.q1 = np.percentile(steps_array, 25)
        self.q3 = np.percentile(steps_array, 75)
        self.iqr = self.q3 - self.q1

        self.lower_bound = self.q1 - 1.5 * self.iqr
        self.upper_bound = self.q3 + 1.5 * self.iqr

        logger.info(f"步数基线更新: Q1={self.q1}, Q3={self.q3}, "
                    f"正常范围=[{self.lower_bound}, {self.upper_bound}]")

    def add_step_data(self, steps: int, timestamp: float, animal_id: str = None):
        """添加步数数据"""
        self.step_history.append({
            'steps': steps,
            'timestamp': timestamp,
            'animal_id': animal_id
        })

        # 更新基线
        step_values = [s['steps'] for s in self.step_history]
        self.update_baseline(step_values)

    def check_anomaly(self, current_steps: int, timestamp: float,
                      animal_id: str = None) -> Dict[str, Any]:
        """检测步数异常"""
        if self.iqr == 0:
            return {'is_anomaly': False, 'reason': '基线未建立'}

        is_anomaly = current_steps < self.lower_bound or current_steps > self.upper_bound

        result = {
            'is_anomaly': is_anomaly,
            'current_steps': current_steps,
            'normal_range': [self.lower_bound, self.upper_bound],
            'timestamp': timestamp,
            'animal_id': animal_id,
            'severity': self._calculate_severity(current_steps)
        }

        if is_anomaly:
            self.alert_history.append(result)
            logger.warning(f"步数异常: {current_steps} 步, 正常范围: [{self.lower_bound}, {self.upper_bound}]")

        return result

    def _calculate_severity(self, steps: int) -> str:
        """计算异常严重程度"""
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

    def send_email_alert(self, anomaly_data: Dict, recipient: str = None):
        """发送邮件预警"""
        if not self.smtp_config.get('username'):
            logger.warning("邮件配置未设置，跳过邮件发送")
            return False

        try:
            msg = MIMEMultipart()
            msg['From'] = self.smtp_config['username']
            msg['To'] = recipient or self.smtp_config.get('recipient', '')
            msg['Subject'] = f"【云牧智感】步数异常预警 - {anomaly_data.get('animal_id', '未知')}"

            body = f"""
            动物ID: {anomaly_data.get('animal_id', '未知')}
            当前步数: {anomaly_data.get('current_steps', 0)}
            正常范围: {anomaly_data.get('normal_range', [0, 0])}
            异常时间: {anomaly_data.get('timestamp', '未知')}
            严重程度: {anomaly_data.get('severity', 'unknown')}
            """

            msg.attach(MIMEText(body, 'plain'))

            server = smtplib.SMTP(self.smtp_config['server'], self.smtp_config['port'])
            server.starttls()
            server.login(self.smtp_config['username'], self.smtp_config['password'])
            server.send_message(msg)
            server.quit()

            logger.info(f"邮件预警已发送: {msg['To']}")
            return True

        except Exception as e:
            logger.error(f"邮件发送失败: {e}")
            return False

    def send_platform_alert(self, anomaly_data: Dict):
        """发送平台消息推送"""
        if not self.webhook_url:
            logger.warning("Webhook URL未设置，跳过平台推送")
            return False

        try:
            payload = {
                'type': 'step_anomaly',
                'animal_id': anomaly_data.get('animal_id'),
                'current_steps': anomaly_data.get('current_steps'),
                'normal_range': anomaly_data.get('normal_range'),
                'timestamp': anomaly_data.get('timestamp'),
                'severity': anomaly_data.get('severity')
            }

            response = requests.post(
                self.webhook_url,
                json=payload,
                timeout=5
            )

            if response.status_code == 200:
                logger.info(f"平台推送成功: {anomaly_data.get('animal_id')}")
                return True
            else:
                logger.warning(f"平台推送失败: {response.status_code}")
                return False

        except Exception as e:
            logger.error(f"平台推送失败: {e}")
            return False

    def send_alert(self, anomaly_data: Dict, methods: List[str] = None):
        """发送多级预警"""
        if methods is None:
            methods = ['email', 'platform']

        results = {}

        if 'email' in methods:
            results['email'] = self.send_email_alert(anomaly_data)

        if 'platform' in methods:
            results['platform'] = self.send_platform_alert(anomaly_data)

        return results

    def get_alert_summary(self) -> Dict[str, Any]:
        """获取预警摘要"""
        alerts = list(self.alert_history)

        if not alerts:
            return {'total_alerts': 0, 'alerts': []}

        severity_counts = {'critical': 0, 'warning': 0, 'info': 0}
        for alert in alerts:
            severity_counts[alert.get('severity', 'info')] += 1

        return {
            'total_alerts': len(alerts),
            'severity_distribution': severity_counts,
            'latest_alerts': list(self.alert_history)[-10:],
            'current_normal_range': [self.lower_bound, self.upper_bound]
        }

    def get_frontend_alert_data(self) -> Dict:
        """获取前端展示的预警数据"""
        latest_alerts = list(self.alert_history)[-5:]

        return {
            'has_anomaly': len(self.alert_history) > 0,
            'current_bound': {
                'lower': self.lower_bound,
                'upper': self.upper_bound
            },
            'latest_alerts': [
                {
                    'steps': alert['current_steps'],
                    'time': alert['timestamp'],
                    'severity': alert['severity']
                }
                for alert in latest_alerts
            ],
            'alert_count': len(self.alert_history)
        }