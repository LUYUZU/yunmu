import { useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPaw, faDownload, faFileExport } from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function AnimalsPage() {
  const {
    animals, animalRecords, animalFilter, lastUpdate, loading,
    setAnimalFilter, loadAnimalData, exportAnimalData,
  } = useAppStore();

  useEffect(() => { loadAnimalData(); }, []);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faPaw} /> 动物监测</h2>
      </div>

      <div className="filter-panel" style={{ marginBottom: 'var(--gap-lg)' }}>
        <div className="filter-row">
          <div className="filter-item">
            <label>动物 ID</label>
            <select
              value={animalFilter.animalId}
              onChange={e => { setAnimalFilter(e.target.value); loadAnimalData(); }}
            >
              <option value="">全部</option>
              {animals.map(a => <option key={a.id} value={a.id}>{a.id}</option>)}
            </select>
          </div>
          <div className="filter-actions">
            <button className={`btn btn-primary${loading.animals ? ' loading' : ''}`} onClick={loadAnimalData}>
              <FontAwesomeIcon icon={faDownload} /> 加载数据
            </button>
            <button className={`btn btn-success${loading.animals ? ' loading' : ''}`} onClick={exportAnimalData}>
              <FontAwesomeIcon icon={faFileExport} /> 导出
            </button>
          </div>
        </div>
      </div>

      <div className="data-info-bar">
        <span>记录数: {animalRecords.length}</span>
        <span>最近更新: {lastUpdate}</span>
      </div>

      <div className="card">
        <div className="table-wrap" style={{ maxHeight: 500, overflowY: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>时间</th><th>动物ID</th><th>行为</th>
                <th>体温</th><th>心率</th><th>状态</th>
              </tr>
            </thead>
            <tbody>
              {animalRecords.length === 0
                ? <tr><td className="table-empty" colSpan={6}>暂无数据</td></tr>
                : animalRecords.map((r, i) => (
                  <tr key={i} className={r.status === '异常' ? 'row-alert' : ''}>
                    <td>{r.time}</td>
                    <td>{r.animalId}</td>
                    <td>{r.behavior}</td>
                    <td className={parseFloat(r.temperature) > 39 ? 'text-danger' : ''}>{r.temperature}°C</td>
                    <td className={r.heartRate > 90 || r.heartRate < 40 ? 'text-danger' : ''}>{r.heartRate} bpm</td>
                    <td>
                      <span className={`badge badge-${r.status === '正常' ? 'normal' : 'alert'}`}>
                        {r.status}
                      </span>
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
