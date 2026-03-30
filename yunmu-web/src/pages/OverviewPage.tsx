import { useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faTachometerAlt, faPlug, faBolt, faSync, faPaw, faCheckCircle,
  faExclamationTriangle, faDatabase, faStream, faTrashAlt,
  faHeartbeat, faCow, faThermometerHalf, faHeart, faRunning, faWalking,
} from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function OverviewPage() {
  const {
    stats, animals, dataStreamLogs, loading,
    checkBackendStatus, generateMockData, refreshOverview,
    addLog, clearLogs, showNotification,
  } = useAppStore();

  useEffect(() => { refreshOverview(); }, []);

  const handleRefreshStream = () => {
    addLog('SYSTEM', '数据流刷新');
    showNotification('success', '刷新成功', '数据流已刷新');
  };

  return (
    <div className="page-enter">
      {/* 标题栏 */}
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faTachometerAlt} /> 系统概览</h2>
        <div className="header-actions">
          <button className={`btn btn-ghost${loading.overview ? ' loading' : ''}`} onClick={checkBackendStatus}>
            <FontAwesomeIcon icon={faPlug} /> 检查连接
          </button>
          <button className={`btn btn-primary${loading.overview ? ' loading' : ''}`} onClick={generateMockData}>
            <FontAwesomeIcon icon={faBolt} /> 生成模拟数据
          </button>
          <button className={`btn btn-ghost${loading.overview ? ' loading' : ''}`} onClick={refreshOverview}>
            <FontAwesomeIcon icon={faSync} /> 刷新
          </button>
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-purple)' }}>
            <FontAwesomeIcon icon={faPaw} />
          </div>
          <div>
            <div className="stat-value">{stats.totalAnimals}</div>
            <div className="stat-label">监测动物总数</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-green)' }}>
            <FontAwesomeIcon icon={faCheckCircle} />
          </div>
          <div>
            <div className="stat-value">{stats.normal}</div>
            <div className="stat-label">正常状态</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-red)' }}>
            <FontAwesomeIcon icon={faExclamationTriangle} />
          </div>
          <div>
            <div className="stat-value">{stats.alert}</div>
            <div className="stat-label">异常预警</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-cyan)' }}>
            <FontAwesomeIcon icon={faDatabase} />
          </div>
          <div>
            <div className="stat-value">{stats.dataReceived}</div>
            <div className="stat-label">数据接收数</div>
          </div>
        </div>
      </div>

      {/* 实时数据流 */}
      <div className="card" style={{ marginBottom: 'var(--gap-lg)' }}>
        <div className="card-header">
          <h3><FontAwesomeIcon icon={faStream} /> 实时数据流</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-sm btn-ghost" onClick={handleRefreshStream}>
              <FontAwesomeIcon icon={faSync} /> 刷新
            </button>
            <button className="btn btn-sm btn-ghost" onClick={clearLogs}>
              <FontAwesomeIcon icon={faTrashAlt} /> 清空
            </button>
          </div>
        </div>
        <div className="card-body" style={{ padding: '12px 16px' }}>
          <div className="data-stream">
            {dataStreamLogs.length === 0
              ? <div className="stream-empty">暂无数据流</div>
              : dataStreamLogs.map(log => (
                <div key={log.id} className="stream-log">
                  <span className="log-time">{log.time}</span>
                  <span className="log-source">[{log.source}]</span>
                  <span className="log-message">{log.message}</span>
                </div>
              ))
            }
          </div>
        </div>
      </div>

      {/* 实时监测卡片 */}
      <div className="card">
        <div className="card-header">
          <h3><FontAwesomeIcon icon={faHeartbeat} /> 实时监测</h3>
        </div>
        <div className="card-body">
          {animals.length === 0
            ? <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px 0' }}>
                暂无动物数据，请先生成模拟数据
              </div>
            : <div className="animal-grid">
                {animals.map(animal => (
                  <div key={animal.id} className={`animal-card${animal.status === 'alert' ? ' alert' : ''}`}>
                    <div className="animal-head">
                      <span className="animal-id">
                        <FontAwesomeIcon icon={animal.type === 'cow' ? faCow : faPaw} />
                        {animal.id}
                      </span>
                      <span className={`badge badge-${animal.status}`}>
                        {animal.status === 'normal' ? '正常' : '异常'}
                      </span>
                    </div>
                    <div className="animal-data">
                      <div className="data-item">
                        <span className="dl"><FontAwesomeIcon icon={faThermometerHalf} /> 体温</span>
                        <span className={parseFloat(animal.temperature) > 39 ? 'text-danger' : ''}>
                          {animal.temperature}°C
                        </span>
                      </div>
                      <div className="data-item">
                        <span className="dl"><FontAwesomeIcon icon={faHeart} /> 心率</span>
                        <span className={animal.heartRate > 90 || animal.heartRate < 40 ? 'text-danger' : ''}>
                          {animal.heartRate} bpm
                        </span>
                      </div>
                      <div className="data-item">
                        <span className="dl"><FontAwesomeIcon icon={faRunning} /> 步数</span>
                        <span>{animal.steps}</span>
                      </div>
                      <div className="data-item">
                        <span className="dl"><FontAwesomeIcon icon={faWalking} /> 行为</span>
                        <span>{animal.behavior}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
          }
        </div>
      </div>
    </div>
  );
}
