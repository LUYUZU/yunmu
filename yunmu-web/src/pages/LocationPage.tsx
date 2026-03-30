import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMapMarkerAlt, faSync, faEraser } from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

// 修复 Leaflet 默认图标路径问题
import iconRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png';
import iconUrl from 'leaflet/dist/images/marker-icon.png';
import shadowUrl from 'leaflet/dist/images/marker-shadow.png';
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl;
L.Icon.Default.mergeOptions({ iconRetinaUrl, iconUrl, shadowUrl });

// 创建自定义 div 图标
const createAnimalIcon = (type: 'cow' | 'sheep', isAlert: boolean) =>
  L.divIcon({
    html: `<div style="
      background: ${isAlert ? '#ef4444' : '#3b82f6'};
      width: 36px; height: 36px;
      border-radius: 50%;
      border: 2px solid white;
      display: flex; align-items: center; justify-content: center;
      font-size: 18px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.2);
      cursor: pointer;
    ">${type === 'cow' ? '🐄' : '🐑'}</div>`,
    iconSize: [40, 40],
    className: '',
    popupAnchor: [0, -20],
  });

// 自动调整地图视野
function FitBounds({ positions }: { positions: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (positions.length > 0) {
      map.fitBounds(positions, { padding: [40, 40] });
    }
  }, [positions, map]);
  return null;
}

export default function LocationPage() {
  const { animals, locationData, loading, loadLocationData, clearLocationTrace } = useAppStore();

  useEffect(() => { loadLocationData(); }, []);

  const positions = locationData.map(d => [d.latitude, d.longitude] as [number, number]);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faMapMarkerAlt} /> 北斗定位</h2>
      </div>

      <div className="filter-panel" style={{ marginBottom: 'var(--gap-lg)' }}>
        <div className="filter-row">
          <button className={`btn btn-primary${loading.location ? ' loading' : ''}`} onClick={loadLocationData}>
            <FontAwesomeIcon icon={faMapMarkerAlt} /> 加载位置
          </button>
          <button className="btn btn-ghost" onClick={clearLocationTrace}>
            <FontAwesomeIcon icon={faEraser} /> 清除轨迹
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h3><FontAwesomeIcon icon={faMapMarkerAlt} /> 实时位置</h3>
          <button className="btn btn-sm btn-ghost" onClick={loadLocationData}>
            <FontAwesomeIcon icon={faSync} /> 刷新
          </button>
        </div>
        <div style={{ height: 520, borderRadius: '0 0 var(--border-radius-lg) var(--border-radius-lg)', overflow: 'hidden' }}>
          {locationData.length === 0
            ? <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', background: 'var(--color-gray-50)' }}>
                <div style={{ textAlign: 'center' }}>
                  <FontAwesomeIcon icon={faMapMarkerAlt} style={{ fontSize: 40, marginBottom: 12, color: 'var(--color-gray-300)' }} />
                  <p>暂无位置数据，请先生成模拟数据</p>
                </div>
              </div>
            : <MapContainer
                center={[30.0, 90.0]}
                zoom={6}
                style={{ width: '100%', height: '100%' }}
                scrollWheelZoom
              >
                <TileLayer
                  url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                  maxZoom={19}
                />
                {positions.length > 0 && <FitBounds positions={positions} />}
                {locationData.map(loc => {
                  const animal = animals.find(a => a.id === loc.id);
                  const isAlert = animal?.status === 'alert';
                  return (
                    <Marker
                      key={loc.id}
                      position={[loc.latitude, loc.longitude]}
                      icon={createAnimalIcon(animal?.type ?? 'cow', isAlert)}
                    >
                      <Popup>
                        <strong>{animal?.type === 'cow' ? '🐄' : '🐑'} {loc.id}</strong><br />
                        📍 {loc.latitude.toFixed(5)}°N, {loc.longitude.toFixed(5)}°E<br />
                        💚 状态: {isAlert ? '⚠️ 异常' : '✅ 正常'}<br />
                        🌡️ 体温: {animal?.temperature}°C<br />
                        💓 心率: {animal?.heartRate} bpm<br />
                        👣 步数: {animal?.steps}<br />
                        🕐 {loc.timestamp}
                      </Popup>
                    </Marker>
                  );
                })}
              </MapContainer>
          }
        </div>
      </div>
    </div>
  );
}
