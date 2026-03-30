import { useEffect, useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faShoePrints, faDownload, faSync,
  faWalking, faClock, faTachometerAlt, faChartLine, faChartPie, faList,
} from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function StepsPage() {
  const { stepStats, stepHourlyData, loading, loadStepData } = useAppStore();

  useEffect(() => { loadStepData(); }, []);

  // 趋势线图
  const trendOption = useMemo(() => ({
    tooltip: { trigger: 'axis' },
    color: ['#3b82f6'],
    xAxis: {
      type: 'category',
      data: stepHourlyData.map(d => `${d.hour}:00`),
      axisLabel: { color: '#64748b', interval: 3 },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    yAxis: {
      type: 'value', name: '步数',
      axisLabel: { color: '#64748b' },
      nameTextStyle: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
    },
    series: [{
      data: stepHourlyData.map(d => d.steps),
      type: 'line', smooth: true,
      itemStyle: { color: '#3b82f6' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(59,130,246,0.3)' },
            { offset: 1, color: 'rgba(59,130,246,0.02)' },
          ],
        },
      },
    }],
  }), [stepHourlyData]);

  // 活跃饼图
  const activityOption = useMemo(() => ({
    tooltip: { trigger: 'item' },
    legend: { top: '5%', left: 'center', textStyle: { color: '#64748b' } },
    color: ['#bfdbfe', '#93c5fd', '#60a5fa', '#2563eb'],
    series: [{
      name: '活跃程度', type: 'pie', radius: ['38%', '65%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold', color: '#1e293b' } },
      data: [
        { value: Math.floor(Math.random() * 30) + 10, name: '低' },
        { value: Math.floor(Math.random() * 40) + 20, name: '中低' },
        { value: Math.floor(Math.random() * 40) + 30, name: '中高' },
        { value: Math.floor(Math.random() * 30) + 10, name: '高' },
      ],
    }],
  }), [stepStats]);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faShoePrints} /> 步数统计</h2>
        <div className="header-actions">
          <button className={`btn btn-primary${loading.steps ? ' loading' : ''}`} onClick={loadStepData}>
            <FontAwesomeIcon icon={faDownload} /> 加载数据
          </button>
          <button className={`btn btn-ghost${loading.steps ? ' loading' : ''}`} onClick={loadStepData}>
            <FontAwesomeIcon icon={faSync} /> 刷新统计
          </button>
        </div>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-pink)' }}>
            <FontAwesomeIcon icon={faShoePrints} />
          </div>
          <div>
            <div className="stat-value">{stepStats.todaySteps.toLocaleString()}</div>
            <div className="stat-label">今日步数</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-cyan)' }}>
            <FontAwesomeIcon icon={faWalking} />
          </div>
          <div>
            <div className="stat-value">{stepStats.walkingDistance.toLocaleString()} m</div>
            <div className="stat-label">行走距离</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-green)' }}>
            <FontAwesomeIcon icon={faClock} />
          </div>
          <div>
            <div className="stat-value">{stepStats.activeTime} 分钟</div>
            <div className="stat-label">活跃时长</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-orange)' }}>
            <FontAwesomeIcon icon={faTachometerAlt} />
          </div>
          <div>
            <div className="stat-value">{stepStats.stepFrequency}</div>
            <div className="stat-label">步频(步/分)</div>
          </div>
        </div>
      </div>

      {stepHourlyData.length > 0 && (
        <>
          <div className="card" style={{ marginBottom: 'var(--gap-lg)' }}>
            <div className="card-header"><h3><FontAwesomeIcon icon={faChartLine} /> 24小时步数趋势</h3></div>
            <div className="card-body" style={{ padding: '8px 12px' }}>
              <ReactECharts option={trendOption} style={{ height: 280 }} />
            </div>
          </div>

          <div className="charts-row">
            <div className="card">
              <div className="card-header"><h3><FontAwesomeIcon icon={faChartPie} /> 活跃程度分布</h3></div>
              <div className="card-body" style={{ padding: '8px 12px' }}>
                <ReactECharts option={activityOption} style={{ height: 260 }} />
              </div>
            </div>
            <div className="card">
              <div className="card-header"><h3><FontAwesomeIcon icon={faList} /> 小时级步数详情</h3></div>
              <div style={{ maxHeight: 320, overflowY: 'auto' }}>
                <table className="data-table">
                  <thead>
                    <tr><th>时间</th><th>步数</th><th>距离</th><th>活跃时长</th></tr>
                  </thead>
                  <tbody>
                    {stepHourlyData.map((r, i) => (
                      <tr key={i}>
                        <td>{r.hour}:00</td>
                        <td>{r.steps}</td>
                        <td>{r.distance} m</td>
                        <td>{r.activeTime} 分钟</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
