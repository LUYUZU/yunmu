# mqtt_client.py - 修复重复消息问题
import paho.mqtt.client as mqtt
import json
import logging
from datetime import datetime
from typing import Dict, Optional

logger = logging.getLogger(__name__)


class MQTTDataReceiver:
    """MQTT数据接收器 - 修复重复消息问题"""

    def __init__(self, broker_host: str = "120.27.235.176", broker_port: int = 1883):
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.client = None
        self.connected = False
        self.username = "root"
        self.password = "root"

        # 存储最新的传感器数据
        self.latest_data: Dict = {}

        # 记录已处理的消息ID，避免重复处理
        self.processed_messages = set()
        self.max_processed_messages = 1000  # 最多保留1000条记录

        # 回调函数
        self.data_callback = None

    def on_connect(self, client, userdata, flags, rc):
        """MQTT连接回调"""
        if rc == 0:
            self.connected = True
            logger.info(f"MQTT连接成功: {self.broker_host}:{self.broker_port}")
            # 订阅所有设备Topic，清除保留消息标志
            client.subscribe("+", options=mqtt.SubscribeOptions(qos=1,
                                                                retainHandling=mqtt.RetainHandling.DONT_SEND_RETAINED))
            logger.info("已订阅所有Topic（不接收保留消息）")
        else:
            logger.error(f"MQTT连接失败，返回码: {rc}")

    def _get_message_id(self, topic: str, payload: str) -> str:
        """生成消息唯一ID（用于去重）"""
        # 使用 topic + payload 的哈希作为消息ID
        import hashlib
        combined = f"{topic}:{payload}"
        return hashlib.md5(combined.encode()).hexdigest()

    def _is_duplicate(self, message_id: str) -> bool:
        """检查是否为重复消息"""
        if message_id in self.processed_messages:
            return True
        # 添加到已处理集合
        self.processed_messages.add(message_id)
        # 限制集合大小
        if len(self.processed_messages) > self.max_processed_messages:
            # 移除旧的记录（保留最近的一半）
            old_size = len(self.processed_messages)
            self.processed_messages = set(list(self.processed_messages)[-self.max_processed_messages // 2:])
            logger.debug(f"清理消息缓存: {old_size} -> {len(self.processed_messages)}")
        return False

    def on_message(self, client, userdata, msg):
        """MQTT消息接收回调"""
        try:
            topic = msg.topic
            payload_str = msg.payload.decode('utf-8')

            # 生成消息ID用于去重
            msg_id = self._get_message_id(topic, payload_str)

            # 检查是否为重复消息
            if self._is_duplicate(msg_id):
                logger.debug(f"忽略重复消息 - Topic: {topic}")
                return

            logger.info(f"收到MQTT消息 - Topic: {topic}, Payload: {payload_str}")

            # 跳过非数据消息
            if topic == "heart_dev_ctrl":
                logger.info(f"心跳控制消息，忽略: {payload_str}")
                return

            # 解析JSON数据
            try:
                data = json.loads(payload_str)
            except json.JSONDecodeError:
                logger.warning(f"非JSON格式消息: {payload_str}")
                return

            # 解析数据
            parsed_data = self._parse_payload(data, topic)

            if parsed_data:
                # 存储最新数据
                self.latest_data[topic] = parsed_data

                # 调用回调函数
                if self.data_callback:
                    self.data_callback(topic, parsed_data)

                logger.info(f"数据解析成功 - 设备ID: {parsed_data.get('device_id')}, "
                            f"经度: {parsed_data.get('longitude')}, "
                            f"纬度: {parsed_data.get('latitude')}, "
                            f"移动状态: {parsed_data.get('move')}, "
                            f"步数: {parsed_data.get('steps')}")

        except Exception as e:
            logger.error(f"消息处理失败: {e}")

    def _parse_payload(self, data: Dict, topic: str) -> Optional[Dict]:
        """解析Payload数据"""
        try:
            # 设备ID优先使用topic
            device_id = topic
            if not device_id or device_id == 'heart_dev_ctrl':
                device_id = str(data.get('code', topic))

            # 提取经纬度（WGS84坐标系）
            longitude = data.get('JD')
            latitude = data.get('WD')

            # 转换字符串为浮点数
            if longitude and isinstance(longitude, str):
                longitude = float(longitude)
            if latitude and isinstance(latitude, str):
                latitude = float(latitude)

            # 移动状态: 0=静止, 1=移动, 2=跑
            move = data.get('move', 0)
            if move is None:
                move = 0

            # 步数
            steps = data.get('bushu', 0)
            if steps is None:
                steps = 0

            # 计数器
            counter = data.get('counter', 0)

            # RFID（如果有）
            rfid = data.get('rfid', None)

            # 温度（如果有）
            temp = data.get('temp', None)

            return {
                'device_id': device_id,
                'longitude': longitude,
                'latitude': latitude,
                'move': move,
                'steps': steps,
                'counter': counter,
                'rfid': rfid,
                'temp': temp,
                'topic': topic,
                'timestamp': datetime.now().timestamp(),
                'raw_data': data
            }
        except Exception as e:
            logger.error(f"数据解析失败: {e}, 原始数据: {data}")
            return None

    def start(self):
        """启动MQTT客户端"""
        try:
            self.client = mqtt.Client()
            self.client.on_connect = self.on_connect
            self.client.on_message = self.on_message

            # 设置用户名密码
            if self.username and self.password:
                self.client.username_pw_set(self.username, self.password)

            # 清除已处理消息缓存
            self.processed_messages.clear()

            self.client.connect(self.broker_host, self.broker_port, 60)
            self.client.loop_start()

            logger.info(f"MQTT客户端已启动，连接到 {self.broker_host}:{self.broker_port}")
            return True
        except Exception as e:
            logger.error(f"MQTT启动失败: {e}")
            return False

    def stop(self):
        """停止MQTT客户端"""
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            self.connected = False
            logger.info("MQTT客户端已停止")

    def set_data_callback(self, callback):
        """设置数据接收回调函数"""
        self.data_callback = callback

    def get_latest_data(self, device_id: str = None) -> Dict:
        """获取最新数据"""
        if device_id:
            return self.latest_data.get(device_id, {})
        return self.latest_data


# 全局MQTT实例
mqtt_receiver = None


def init_mqtt(broker_host: str = "120.27.235.176", broker_port: int = 1883):
    """初始化MQTT接收器"""
    global mqtt_receiver
    mqtt_receiver = MQTTDataReceiver(broker_host, broker_port)
    return mqtt_receiver