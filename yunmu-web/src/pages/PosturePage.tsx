import { useEffect, useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faWalking, faDownload, faSearch, faChartPie, faChartBar } from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function PosturePage() {
  const {
    animals, postureRecords, postureFilter, loading,
    setPostureFilter, loadPostureData,
  } = useAppStore();

  useEffect(() => { loadPostureData(); }, []);

  // 饼图数据
  const pieOption = useMemo(() => {
    const counts: Record<string, number> = {};
    postureRecords.forEach(r => { counts[r.posture] = (counts[r.posture] || 0) + 1; });
    return {
      tooltip: { trigger: 'item' },
      legend: { top: '5%', left: 'center', textStyle: { color: '#64748b' } },
      color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
      series: [{
        name: '姿态分布', type: 'pie', radius: ['38%', '65%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {d}%', color: '#334155', fontSize: 12 },
        data: Object.entries(counts).map(([name, value]) => ({ name, value })),
      }],
    };
  }, [postureRecords]);

  // 柱状图数据
  const barOption = useMemo(() => {
    const durations: Record<string, number> = {};
    postureRecords.forEach(r => { durations[r.posture] = (durations[r.posture] || 0) + r.duration; });
    const entries = Object.entries(durations);
    return {
      tooltip: { trigger: 'axis' },
      color: ['#3b82f6'],
      xAxis: {
        type: 'category',
        data: entries.map(([n]) => n),
        axisLabel: { color: '#64748b' },
        axisLine: { lineStyle: { color: '#e2e8f0' } },
      },
      yAxis: {
        type: 'value', name: '分钟',
        axisLabel: { color: '#64748b' },
        nameTextStyle: { color: '#64748b' },
        splitLine: { lineStyle: { color: '#f1f5f9' } },
      },
      series: [{
        data: entries.map(([, v]) => v),
        type: 'bar',
        barMaxWidth: 48,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#3b82f6' },
              { offset: 1, color: '#93c5fd' },
            ],
          },
        },
      }],
    };
  }, [postureRecords]);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faWalking} /> 姿态识别</h2>
        <div className="header-actions">
          <select
            value={postureFilter.animalId}
            onChange={e => { setPostureFilter(e.target.value); loadPostureData(); }}
            style={{ padding: '8px 12px', border: '1px solid var(--border-color)', borderRadius: 'var(--border-radius)', fontSize: 'var(--font-size-base)', color: 'var(--text-primary)', background: 'var(--bg-input)', fontFamily: 'var(--font-sans)' }}
          >
            <option value="">全部动物</option>
            {animals.map(a => <option key={a.id} value={a.id}>{a.id}</option>)}
          </select>
          <button className={`btn btn-primary${loading.posture ? ' loading' : ''}`} onClick={loadPostureData}>
            <FontAwesomeIcon icon={faDownload} /> 加载数据
          </button>
          <button className="btn btn-success" onClick={loadPostureData}>
            <FontAwesomeIcon icon={faSearch} /> 分析姿态
          </button>
        </div>
      </div>

      {postureRecords.length > 0 && (
        <div className="charts-row">
          <div className="card">
            <div className="card-header"><h3><FontAwesomeIcon icon={faChartPie} /> 姿态分布</h3></div>
            <div className="card-body" style={{ padding: '8px 12px' }}>
              <ReactECharts option={pieOption} style={{ height: 280 }} />
            </div>
          </div>
          <div className="card">
            <div className="card-header"><h3><FontAwesomeIcon icon={faChartBar} /> 姿态时长统计</h3></div>
            <div className="card-body" style={{ padding: '8px 12px' }}>
              <ReactECharts option={barOption} style={{ height: 280 }} />
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="table-wrap" style={{ maxHeight: 400, overflowY: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr><th>时间</th><th>动物ID</th><th>姿态</th><th>置信度</th><th>持续时长</th></tr>
            </thead>
            <tbody>
              {postureRecords.length === 0
                ? <tr><td className="table-empty" colSpan={5}>暂无数据</td></tr>
                : postureRecords.map((r, i) => (
                  <tr key={i}>
                    <td>{r.time}</td>
                    <td>{r.animalId}</td>
                    <td>{r.posture}</td>
                    <td>
                      <span className={`badge${r.confidence >= 90 ? ' badge-normal' : ' badge-info'}`}>
                        {r.confidence}%
                      </span>
                    </td>
                    <td>{r.duration} 分钟</td>
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
