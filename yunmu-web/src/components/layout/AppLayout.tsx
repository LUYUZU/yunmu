import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCow, faTachometerAlt, faPaw, faMapMarkerAlt,
  faWalking, faShoePrints, faDatabase, faChartBar,
  faClock, faCheckCircle,
  faExclamationCircle, faExclamationTriangle, faInfoCircle, faTimes,
} from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../../store/appStore';
import '@/styles/layout.css';

const NAV_ITEMS = [
  { path: '/', icon: faTachometerAlt, label: '系统概览' },
  { path: '/animals', icon: faPaw, label: '动物监测' },
  { path: '/location', icon: faMapMarkerAlt, label: '北斗定位' },
  { path: '/posture', icon: faWalking, label: '姿态识别' },
  { path: '/steps', icon: faShoePrints, label: '步数统计' },
  { path: '/data', icon: faDatabase, label: '数据管理' },
  { path: '/analysis', icon: faChartBar, label: '分析报告' },
];

const NOTIF_ICONS = {
  success: faCheckCircle,
  error: faExclamationCircle,
  warning: faExclamationTriangle,
  info: faInfoCircle,
} as const;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    isConnected, connectionStatus, stats, lastUpdate,
    notification, closeNotification,
  } = useAppStore();

  return (
    <div className="app-root">
      {/* ===== 顶栏 ===== */}
      <header className="header">
        <div className="header-inner">
          <div className="header-brand">
            <div className="logo-icon">
              <FontAwesomeIcon icon={faCow} />
            </div>
            <div>
              <h1>云牧智感</h1>
              <p>高原牛羊行为监测系统</p>
            </div>
          </div>
          <div className="header-right">
            <div className="status-chip status-chip--conn">
              <span className={`status-dot${isConnected ? ' connected' : ''}`} />
              <span>{connectionStatus}</span>
            </div>
            <div className="status-chip">
              <FontAwesomeIcon icon={faDatabase} />
              <span>数据: {stats.dataReceived}</span>
            </div>
            <div className="status-chip">
              <FontAwesomeIcon icon={faClock} />
              <span>更新: {lastUpdate}</span>
            </div>
          </div>
        </div>
      </header>

      {/* ===== 主体 ===== */}
      <div className="app-body">
        {/* 侧边栏 */}
        <nav className="sidebar">
          <div className="sidebar-header">功能菜单</div>
          {NAV_ITEMS.map(item => (
            <div
              key={item.path}
              className={`nav-item${location.pathname === item.path ? ' active' : ''}`}
              onClick={() => navigate(item.path)}
            >
              <FontAwesomeIcon icon={item.icon} />
              <span>{item.label}</span>
            </div>
          ))}
        </nav>

        {/* 内容 */}
        <main className="content">
          <Outlet />
        </main>
      </div>

      {/* ===== 通知 ===== */}
      <div className="notification-wrap">
        {notification.show && (
          <div className={`notification ${notification.type}`}>
            <span className="notif-icon">
              <FontAwesomeIcon icon={NOTIF_ICONS[notification.type]} />
            </span>
            <div className="notif-body">
              <div className="notif-title">{notification.title}</div>
              <div className="notif-msg">{notification.message}</div>
            </div>
            <button className="notif-close" onClick={closeNotification}>
              <FontAwesomeIcon icon={faTimes} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
