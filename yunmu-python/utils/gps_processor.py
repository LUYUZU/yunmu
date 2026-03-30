# utils/gps_processor.py - 北斗定位模块
import numpy as np
import math
import logging
from typing import Dict, List, Tuple, Optional

logger = logging.getLogger(__name__)


class GPSProcessor:
    """北斗定位处理器"""

    def __init__(self):
        self.current_position = {
            'latitude': None, 'longitude': None, 'altitude': None,
            'speed': None, 'utc_time': None, 'satellites': 0, 'fix_quality': 0
        }
        self.anchor_positions = {}
        self.path_loss_exponent = 2.5
        self.calibrated = False
        self.use_wsn = False
        self.beidou_fail_count = 0
        self.max_fail_count = 3

    def parse_nmea_sentence(self, sentence: str) -> Optional[Dict]:
        if not sentence or not sentence.startswith('$'):
            return None

        if sentence.startswith('$GNGGA') or sentence.startswith('$BDGGA'):
            return self._parse_gga(sentence)
        return None

    def _parse_gga(self, sentence: str) -> Optional[Dict]:
        parts = sentence.split(',')
        if len(parts) < 15:
            return None
        try:
            lat_raw, lat_dir = parts[2], parts[3]
            lon_raw, lon_dir = parts[4], parts[5]

            if lat_raw and lon_raw:
                lat_deg = float(lat_raw[:2]) + float(lat_raw[2:]) / 60
                lon_deg = float(lon_raw[:3]) + float(lon_raw[3:]) / 60
                latitude = -lat_deg if lat_dir == 'S' else lat_deg
                longitude = -lon_deg if lon_dir == 'W' else lon_deg

                fix_quality = int(parts[6]) if parts[6] else 0
                satellites = int(parts[7]) if parts[7] else 0
                altitude = float(parts[9]) if parts[9] else None

                self.current_position.update({
                    'latitude': latitude, 'longitude': longitude,
                    'altitude': altitude, 'satellites': satellites,
                    'fix_quality': fix_quality
                })

                if fix_quality == 0:
                    self.beidou_fail_count += 1
                    if self.beidou_fail_count >= self.max_fail_count:
                        self.use_wsn = True
                else:
                    self.beidou_fail_count = 0
                    self.use_wsn = False

                return {
                    'latitude': latitude, 'longitude': longitude,
                    'altitude': altitude, 'fix_quality': fix_quality,
                    'satellites': satellites, 'use_wsn': self.use_wsn
                }
        except Exception as e:
            logger.error(f"GGA解析失败: {e}")
        return None

    def set_anchor_positions(self, anchors: Dict):
        self.anchor_positions = anchors

    def calculate_rssi_distance(self, rssi: float, ref_rssi: float = -50, ref_dist: float = 1) -> float:
        return ref_dist * 10 ** ((ref_rssi - rssi) / (10 * self.path_loss_exponent))

    def calibrate_path_loss(self, known_distance: float, rssi: float, ref_rssi: float = -50):
        if known_distance <= 0:
            return
        ratio = known_distance / 1.0
        if ratio > 0:
            n = (ref_rssi - rssi) / (10 * math.log10(ratio))
            self.path_loss_exponent = max(1.5, min(4.0, n))
            self.calibrated = True

    def wsn_localization(self, rssi_readings: Dict) -> Optional[Dict]:
        if not self.anchor_positions:
            return None

        distances = {}
        for anchor_id, rssi in rssi_readings.items():
            if anchor_id in self.anchor_positions:
                distances[anchor_id] = self.calculate_rssi_distance(rssi)

        if len(distances) >= 3:
            positions = [self.anchor_positions[aid] for aid in list(distances.keys())[:3]]
            x = sum(p[0] for p in positions) / len(positions)
            y = sum(p[1] for p in positions) / len(positions)
            return {'latitude': y, 'longitude': x, 'method': 'centroid', 'distances': distances}
        return None

    def get_current_position(self) -> Dict:
        return {'source': 'WSN' if self.use_wsn else 'BeiDou', **self.current_position}