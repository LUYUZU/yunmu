# utils/gps_processor.py
import numpy as np
import re
import logging
from typing import Dict, List, Tuple, Optional
from datetime import datetime
import math

logger = logging.getLogger(__name__)


class GPSProcessor:
    """北斗定位处理器 - NMEA-0183协议解析"""

    def __init__(self):
        self.current_position = {
            'latitude': None,
            'longitude': None,
            'altitude': None,
            'speed': None,
            'utc_time': None,
            'satellites': 0,
            'fix_quality': 0
        }

        # WSN辅助定位
        self.anchor_positions = {}  # 锚节点位置
        self.rssi_history = {}  # RSSI历史记录
        self.path_loss_exponent = 2.5  # 路径损耗因子n，默认值
        self.calibrated = False

        # 双模切换
        self.beidou_fail_count = 0
        self.use_wsn = False
        self.max_fail_count = 3

    def parse_nmea_sentence(self, sentence: str) -> Dict:
        """解析NMEA-0183语句"""
        try:
            # 检查语句完整性
            if not sentence.startswith('$'):
                return None

            # 校验和验证
            if '*' in sentence:
                checksum_str = sentence.split('*')[1]
                sentence_body = sentence[1:sentence.find('*')]
                checksum = 0
                for char in sentence_body:
                    checksum ^= ord(char)

                if checksum != int(checksum_str, 16):
                    logger.warning(f"NMEA校验和错误: {sentence}")
                    return None

            # 解析语句类型
            if sentence.startswith('$GPGGA') or sentence.startswith('$BDGGA'):
                return self._parse_gga(sentence)
            elif sentence.startswith('$GPRMC') or sentence.startswith('$BDRMC'):
                return self._parse_rmc(sentence)
            elif sentence.startswith('$GPGSV') or sentence.startswith('$BDGSV'):
                return self._parse_gsv(sentence)

            return None

        except Exception as e:
            logger.error(f"NMEA解析失败: {e}")
            return None

    def _parse_gga(self, sentence: str) -> Dict:
        """解析GGA语句 (Global Positioning System Fix Data)"""
        parts = sentence.split(',')

        if len(parts) < 15:
            return None

        try:
            # UTC时间
            utc_time = parts[1]
            utc_hours = utc_time[:2]
            utc_minutes = utc_time[2:4]
            utc_seconds = utc_time[4:6]

            # 纬度
            lat_raw = parts[2]
            lat_dir = parts[3]
            if lat_raw:
                lat_degrees = float(lat_raw[:2])
                lat_minutes = float(lat_raw[2:]) / 60
                latitude = lat_degrees + lat_minutes
                if lat_dir == 'S':
                    latitude = -latitude

            # 经度
            lon_raw = parts[4]
            lon_dir = parts[5]
            if lon_raw:
                lon_degrees = float(lon_raw[:3])
                lon_minutes = float(lon_raw[3:]) / 60
                longitude = lon_degrees + lon_minutes
                if lon_dir == 'W':
                    longitude = -longitude

            # 定位质量
            fix_quality = int(parts[6]) if parts[6] else 0

            # 卫星数量
            satellites = int(parts[7]) if parts[7] else 0

            # 海拔
            altitude = float(parts[9]) if parts[9] else None

            # 更新当前位置
            self.current_position.update({
                'latitude': latitude,
                'longitude': longitude,
                'altitude': altitude,
                'utc_time': utc_time,
                'satellites': satellites,
                'fix_quality': fix_quality
            })

            # 北斗失效计数
            if fix_quality == 0:
                self.beidou_fail_count += 1
                if self.beidou_fail_count >= self.max_fail_count:
                    self.use_wsn = True
                    logger.warning("北斗失效3次，切换至WSN辅助定位")
            else:
                self.beidou_fail_count = 0
                self.use_wsn = False

            return {
                'type': 'GGA',
                'latitude': latitude,
                'longitude': longitude,
                'altitude': altitude,
                'fix_quality': fix_quality,
                'satellites': satellites,
                'utc_time': utc_time,
                'use_wsn': self.use_wsn
            }

        except Exception as e:
            logger.error(f"GGA解析失败: {e}")
            return None

    def _parse_rmc(self, sentence: str) -> Dict:
        """解析RMC语句 (Recommended Minimum Specific GNSS Data)"""
        parts = sentence.split(',')

        if len(parts) < 12:
            return None

        try:
            # UTC时间
            utc_time = parts[1]

            # 定位状态
            status = parts[2]  # A=有效, V=无效

            # 纬度
            lat_raw = parts[3]
            lat_dir = parts[4]
            if lat_raw:
                lat_degrees = float(lat_raw[:2])
                lat_minutes = float(lat_raw[2:]) / 60
                latitude = lat_degrees + lat_minutes
                if lat_dir == 'S':
                    latitude = -latitude

            # 经度
            lon_raw = parts[5]
            lon_dir = parts[6]
            if lon_raw:
                lon_degrees = float(lon_raw[:3])
                lon_minutes = float(lon_raw[3:]) / 60
                longitude = lon_degrees + lon_minutes
                if lon_dir == 'W':
                    longitude = -longitude

            # 速度 (节转米/秒)
            speed_knots = float(parts[7]) if parts[7] else 0
            speed = speed_knots * 0.514444

            # 航向
            course = float(parts[8]) if parts[8] else None

            # UTC日期
            utc_date = parts[9]

            self.current_position.update({
                'latitude': latitude,
                'longitude': longitude,
                'speed': speed,
                'course': course,
                'fix_valid': status == 'A'
            })

            return {
                'type': 'RMC',
                'latitude': latitude,
                'longitude': longitude,
                'speed': speed,
                'course': course,
                'fix_valid': status == 'A',
                'utc_time': utc_time,
                'utc_date': utc_date
            }

        except Exception as e:
            logger.error(f"RMC解析失败: {e}")
            return None

    def _parse_gsv(self, sentence: str) -> Dict:
        """解析GSV语句 (Satellites in View)"""
        parts = sentence.split(',')

        if len(parts) < 4:
            return None

        try:
            total_messages = int(parts[1])
            message_number = int(parts[2])
            satellites_in_view = int(parts[3])

            satellites = []
            for i in range(4, min(len(parts), 16), 4):
                if i + 3 < len(parts) and parts[i]:
                    satellite = {
                        'prn': parts[i],
                        'elevation': int(parts[i + 1]) if parts[i + 1] else 0,
                        'azimuth': int(parts[i + 2]) if parts[i + 2] else 0,
                        'snr': int(parts[i + 3]) if parts[i + 3] else 0
                    }
                    satellites.append(satellite)

            self.current_position['satellites'] = satellites_in_view

            return {
                'type': 'GSV',
                'total_messages': total_messages,
                'message_number': message_number,
                'satellites_in_view': satellites_in_view,
                'satellites': satellites
            }

        except Exception as e:
            logger.error(f"GSV解析失败: {e}")
            return None

    def set_anchor_positions(self, anchors: Dict):
        """设置WSN锚节点位置"""
        self.anchor_positions = anchors

    def calculate_rssi_distance(self, rssi: float, reference_rssi: float = -50,
                                reference_distance: float = 1) -> float:
        """RSSI测距"""
        # 渐变传播模型: d = d0 * 10^((P0 - Pr) / (10 * n))
        distance = reference_distance * 10 ** ((reference_rssi - rssi) / (10 * self.path_loss_exponent))
        return distance

    def calibrate_path_loss(self, known_distance: float, rssi: float, reference_rssi: float = -50):
        """校准路径损耗因子n"""
        if known_distance <= 0:
            return

        # n = (P0 - Pr) / (10 * log10(d/d0))
        ratio = known_distance / 1.0
        if ratio <= 0:
            return

        n = (reference_rssi - rssi) / (10 * math.log10(ratio))
        self.path_loss_exponent = max(1.5, min(4.0, n))  # 限制在合理范围
        self.calibrated = True
        logger.info(f"路径损耗因子校准: n={self.path_loss_exponent:.2f}")

    def trilateration(self, distances: Dict[str, float]) -> Tuple[float, float]:
        """三边测量法定位"""
        # 需要至少3个锚节点
        if len(distances) < 3:
            return None, None

        # 简化版三边测量
        anchors = list(self.anchor_positions.keys())
        d1 = distances.get(anchors[0], 0)
        d2 = distances.get(anchors[1], 0)
        d3 = distances.get(anchors[2], 0)

        x1, y1 = self.anchor_positions[anchors[0]]
        x2, y2 = self.anchor_positions[anchors[1]]
        x3, y3 = self.anchor_positions[anchors[2]]

        # 三边测量公式
        A = 2 * (x2 - x1)
        B = 2 * (y2 - y1)
        C = d1 ** 2 - d2 ** 2 - x1 ** 2 + x2 ** 2 - y1 ** 2 + y2 ** 2

        D = 2 * (x3 - x1)
        E = 2 * (y3 - y1)
        F = d1 ** 2 - d3 ** 2 - x1 ** 2 + x3 ** 2 - y1 ** 2 + y3 ** 2

        # 解方程组
        det = A * E - B * D
        if abs(det) < 1e-6:
            return None, None

        x = (C * E - B * F) / det
        y = (A * F - C * D) / det

        return x, y

    def centroid_localization(self, positions: List[Tuple[float, float]]) -> Tuple[float, float]:
        """质心定位算法"""
        if not positions:
            return None, None

        x_center = sum(p[0] for p in positions) / len(positions)
        y_center = sum(p[1] for p in positions) / len(positions)

        return x_center, y_center

    def wsn_localization(self, rssi_readings: Dict[str, float]) -> Optional[Dict]:
        """WSN辅助定位"""
        if not self.anchor_positions:
            logger.warning("锚节点位置未设置")
            return None

        # 计算到各锚节点的距离
        distances = {}
        for anchor_id, rssi in rssi_readings.items():
            if anchor_id in self.anchor_positions:
                distance = self.calculate_rssi_distance(rssi)
                distances[anchor_id] = distance

        if len(distances) >= 3:
            # 三边测量法
            x, y = self.trilateration(distances)
            if x is not None and y is not None:
                return {
                    'method': 'trilateration',
                    'latitude': y,  # 简化的坐标转换
                    'longitude': x,
                    'distances': distances,
                    'accuracy': np.std(list(distances.values()))
                }

        # 使用质心定位
        positions = [self.anchor_positions[aid] for aid in distances.keys()]
        x, y = self.centroid_localization(positions)

        return {
            'method': 'centroid',
            'latitude': y,
            'longitude': x,
            'distances': distances,
            'accuracy': np.std(list(distances.values())) if distances else None
        }

    def get_current_position(self) -> Dict:
        """获取当前位置"""
        if self.use_wsn:
            # 使用WSN定位
            return {
                'source': 'WSN',
                **self.current_position,
                'wsn_active': True,
                'beidou_fail_count': self.beidou_fail_count
            }
        else:
            # 使用北斗定位
            return {
                'source': 'BeiDou',
                **self.current_position,
                'wsn_active': False,
                'beidou_fail_count': self.beidou_fail_count
            }

    def calculate_distance(self, lat1: float, lon1: float,
                           lat2: float, lon2: float) -> float:
        """计算两点间距离 (Haversine公式)"""
        R = 6371000  # 地球半径 (米)

        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lambda = math.radians(lon2 - lon1)

        a = math.sin(delta_phi / 2) ** 2 + \
            math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

        return R * c

    def is_moving(self, threshold: float = 0.5) -> bool:
        """判断是否在移动"""
        speed = self.current_position.get('speed', 0)
        return speed > threshold

    def get_location_summary(self) -> Dict:
        """获取定位摘要"""
        return {
            'latitude': self.current_position.get('latitude'),
            'longitude': self.current_position.get('longitude'),
            'altitude': self.current_position.get('altitude'),
            'speed': self.current_position.get('speed'),
            'satellites': self.current_position.get('satellites'),
            'fix_quality': self.current_position.get('fix_quality'),
            'use_wsn': self.use_wsn,
            'timestamp': datetime.now().isoformat()
        }