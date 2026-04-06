import { useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faDatabase, faFilter, faFileExport } from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function DataPage() {
  const {
    historyData, dataFilter, loading,
    setDataFilter, filterData, exportAllData, exportFilteredData,
  } = useAppStore();

  useEffect(() => { filterData(); }, []);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faDatabase} /> 数据管理</h2>
      </div>

      <div className="filter-panel" style={{ marginBottom: 'var(--gap-lg)' }}>
        <div className="filter-row">
          <div className="filter-item">
            <label>时间范围</label>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input
                type="date"
                value={dataFilter.startDate}
                onChange={e => setDataFilter({ startDate: e.target.value })}
              />
              <span style={{ color: 'var(--text-muted)' }}>至</span>
              <input
                type="date"
                value={dataFilter.endDate}
                onChange={e => setDataFilter({ endDate: e.target.value })}
              />
            </div>
          </div>
          <div className="filter-item">
            <label>数据类型</label>
            <select
              value={dataFilter.dataType}
              onChange={e => setDataFilter({ dataType: e.target.value })}
            >
              <option value="">全部</option>
              <option value="behavior">行为数据</option>
              <option value="location">定位数据</option>
              <option value="posture">姿态数据</option>
              <option value="step">步数数据</option>
            </select>
          </div>
          <div className="filter-actions">
            <button className={`btn btn-primary${loading.data ? ' loading' : ''}`} onClick={filterData}>
              <FontAwesomeIcon icon={faFilter} /> 筛选
            </button>
            <button className={`btn btn-success${loading.data ? ' loading' : ''}`} onClick={exportAllData}>
              <FontAwesomeIcon icon={faFileExport} /> 全部导出
            </button>
            <button className={`btn btn-ghost${loading.data ? ' loading' : ''}`} onClick={exportFilteredData}>
              <FontAwesomeIcon icon={faFileExport} /> 导出筛选结果
            </button>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="table-wrap" style={{ maxHeight: 520, overflowY: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr><th>时间</th><th>动物ID</th><th>行为</th><th>步数</th><th>位置</th><th>状态</th></tr>
            </thead>
            <tbody>
              {historyData.length === 0
                ? <tr><td className="table-empty" colSpan={6}>暂无数据，请先生成模拟数据</td></tr>
                : historyData.map((r, i) => (
                  <tr key={i} className={r.status === '异常' ? 'row-alert' : ''}>
                    <td>{r.time}</td>
                    <td>{r.animalId}</td>
                    <td>{r.behavior}</td>
                    <td>{r.steps}</td>
                    <td style={{ fontSize: 'var(--font-size-sm)', color: 'var(--text-secondary)' }}>{r.location}</td>
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
