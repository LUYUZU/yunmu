import { useEffect, useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChartBar, faChartLine, faFileMedical, faHeartbeat,
  faWalking, faShoePrints, faMapMarkerAlt, faPaw, faCheck,
  faExclamation, faLightbulb,
} from '@fortawesome/free-solid-svg-icons';
import { useAppStore } from '../store/appStore';

export default function AnalysisPage() {
  const {
    analysis, healthReport, loading,
    loadBehaviorStatistics,
    generateHealthReport: genReport,
  } = useAppStore();

  useEffect(() => { loadBehaviorStatistics(); }, []);

  const trendOption = useMemo(() => {
    const days: string[] = [];
    const data: number[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      days.push(`${d.getMonth() + 1}/${d.getDate()}`);
      data.push(Math.floor(Math.random() * 100) + 50);
    }
    return {
      tooltip: { trigger: 'axis' },
      color: ['#3b82f6'],
      xAxis: {
        type: 'category',
        data: days,
        axisLabel: { color: '#64748b' },
        axisLine: { lineStyle: { color: '#e2e8f0' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#64748b' },
        splitLine: { lineStyle: { color: '#f1f5f9' } },
      },
      series: [{
        data,
        type: 'line',
        smooth: true,
        itemStyle: { color: '#3b82f6' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(59,130,246,0.25)' },
              { offset: 1, color: 'rgba(59,130,246,0.02)' },
            ],
          },
        },
      }],
    };
  }, [healthReport.totalAnimals]);

  return (
    <div className="page-enter">
      <div className="page-header">
        <h2><FontAwesomeIcon icon={faChartBar} /> 分析报告</h2>
        <div className="header-actions">
          <button
            className={`btn btn-primary${loading.analysis ? ' loading' : ''}`}
            onClick={loadBehaviorStatistics}
          >
            <FontAwesomeIcon icon={faChartLine} /> 加载统计
          </button>
          <button
            className={`btn btn-success${loading.analysis ? ' loading' : ''}`}
            onClick={genReport}
          >
            <FontAwesomeIcon icon={faFileMedical} /> 生成报告
          </button>
        </div>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-purple)' }}>
            <FontAwesomeIcon icon={faHeartbeat} />
          </div>
          <div>
            <div className="stat-value">{analysis.healthAlerts}</div>
            <div className="stat-label">健康异常</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-pink)' }}>
            <FontAwesomeIcon icon={faWalking} />
          </div>
          <div>
            <div className="stat-value">{analysis.postureAlerts}</div>
            <div className="stat-label">姿态异常</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-green)' }}>
            <FontAwesomeIcon icon={faShoePrints} />
          </div>
          <div>
            <div className="stat-value">{analysis.stepAlerts}</div>
            <div className="stat-label">步数异常</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'var(--gradient-orange)' }}>
            <FontAwesomeIcon icon={faMapMarkerAlt} />
          </div>
          <div>
            <div className="stat-value">{analysis.locationAlerts}</div>
            <div className="stat-label">定位异常</div>
          </div>
        </div>
      </div>

      {healthReport.totalAnimals > 0 && (
        <div className="card" style={{ marginBottom: 'var(--gap-lg)' }}>
          <div className="card-header">
            <h3><FontAwesomeIcon icon={faChartLine} /> 行为趋势分析</h3>
          </div>
          <div className="card-body" style={{ padding: '8px 12px' }}>
            <ReactECharts option={trendOption} style={{ height: 280 }} />
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h3><FontAwesomeIcon icon={faFileMedical} /> 健康报告</h3>
        </div>
        <div className="card-body">
          {healthReport.totalAnimals === 0
            ? <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px 0' }}>
                暂无数据，请先生成模拟数据
              </div>
            : <>
                <div className="report-metrics">
                  <div className="metric-item">
                    <div className="metric-icon" style={{ background: 'var(--gradient-blue)' }}>
                      <FontAwesomeIcon icon={faPaw} />
                    </div>
                    <div>
                      <div className="metric-value">{healthReport.totalAnimals}</div>
                      <div className="metric-label">监测动物</div>
                    </div>
                  </div>
                  <div className="metric-item">
                    <div className="metric-icon" style={{ background: 'var(--gradient-green)' }}>
                      <FontAwesomeIcon icon={faCheck} />
                    </div>
                    <div>
                      <div className="metric-value">{healthReport.normalAnimals}</div>
                      <div className="metric-label">正常动物</div>
                    </div>
                  </div>
                  <div className="metric-item">
                    <div className="metric-icon" style={{ background: 'var(--gradient-red)' }}>
                      <FontAwesomeIcon icon={faExclamation} />
                    </div>
                    <div>
                      <div className="metric-value">{healthReport.alertAnimals}</div>
                      <div className="metric-label">异常动物</div>
                    </div>
                  </div>
                </div>

                <div className="report-suggestions">
                  <h4><FontAwesomeIcon icon={faLightbulb} /> 建议</h4>
                  <ul>
                    {[
                      '持续监测动物体温变化，及时发现异常情况',
                      '保持适当的运动量对牛羊健康至关重要',
                      '定期检查定位设备确保数据准确性',
                      '建议定期进行健康体检，预防潜在疾病',
                    ].map((s, i) => <li key={i}>{s}</li>)}
                  </ul>
                </div>
              </>
          }
        </div>
      </div>
    </div>
  );
}
