<template>
  <div class="tab-content">
    <div class="content-header">
      <h2><i class="fas fa-chart-bar"></i> 分析报告</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{loading}" @click="$emit('load')"><i class="fas fa-chart-line"></i> 加载统计</button>
        <button class="btn btn-success" :class="{loading}" @click="$emit('generate')"><i class="fas fa-file-medical"></i> 生成报告</button>
      </div>
    </div>
    <div class="stats-cards">
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#667eea,#764ba2)"><i class="fas fa-heartbeat"></i></div><div class="stat-info"><div class="stat-value">{{ analysis.healthAlerts }}</div><div class="stat-label">健康异常</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#f093fb,#f5576c)"><i class="fas fa-walking"></i></div><div class="stat-info"><div class="stat-value">{{ analysis.postureAlerts }}</div><div class="stat-label">姿态异常</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#43e97b,#38f9d7)"><i class="fas fa-shoe-prints"></i></div><div class="stat-info"><div class="stat-value">{{ analysis.stepAlerts }}</div><div class="stat-label">步数异常</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#fa709a,#fee140)"><i class="fas fa-map-marker-alt"></i></div><div class="stat-info"><div class="stat-value">{{ analysis.locationAlerts }}</div><div class="stat-label">定位异常</div></div></div>
    </div>
    <div class="dashboard-card"><div class="card-header"><h3><i class="fas fa-chart-line"></i> 行为趋势分析</h3></div><div class="chart-container" ref="trendRef"></div></div>
    <div class="health-report">
      <div class="report-header"><h3><i class="fas fa-file-medical-alt"></i> 健康报告</h3></div>
      <div class="report-metrics">
        <div class="metric-item"><div class="metric-icon"><i class="fas fa-paw"></i></div><div><div class="metric-value">{{ healthReport.totalAnimals }}</div><div class="metric-label">监测动物</div></div></div>
        <div class="metric-item"><div class="metric-icon" style="background:linear-gradient(135deg,#11998e,#38ef7d)"><i class="fas fa-check"></i></div><div><div class="metric-value">{{ healthReport.normalAnimals }}</div><div class="metric-label">正常动物</div></div></div>
        <div class="metric-item"><div class="metric-icon" style="background:linear-gradient(135deg,#eb3349,#f45c43)"><i class="fas fa-exclamation"></i></div><div><div class="metric-value">{{ healthReport.alertAnimals }}</div><div class="metric-label">异常动物</div></div></div>
      </div>
      <div class="report-suggestions">
        <h4><i class="fas fa-lightbulb"></i> 建议</h4>
        <ul>
          <li>持续监测动物体温变化，及时发现异常情况</li>
          <li>保持适当的运动量对牛羊健康至关重要</li>
          <li>定期检查定位设备确保数据准确性</li>
          <li>建议定期进行健康体检，预防潜在疾病</li>
        </ul>
      </div>
    </div>
  </div>
</template>
<script>
import * as echarts from 'echarts'
export default {
  name: 'AnalysisTab',
  props: { stats: Object, analysis: Object, healthReport: Object, loading: Boolean },
  mounted() { this.$nextTick(() => this.renderTrend()) },
  beforeDestroy() { this.trendChart?.dispose() },
  methods: {
    renderTrend() {
      if (!this.$refs.trendRef) return
      if (this.trendChart) this.trendChart.dispose()
      this.trendChart = echarts.init(this.$refs.trendRef)
      const days=[], data=[]
      for (let i=6; i>=0; i--) { const d=new Date(); d.setDate(d.getDate()-i); days.push(`${d.getMonth()+1}/${d.getDate()}`); data.push(Math.floor(Math.random()*100)+50) }
      this.trendChart.setOption({ tooltip:{trigger:'axis'},
        xAxis:{type:'category',data:days,axisLabel:{color:'#5A6B7D'}},
        yAxis:{type:'value',axisLabel:{color:'#5A6B7D'}},
        series:[{data,type:'line',smooth:true,itemStyle:{color:'#4facfe'}}] })
    }
  }
}
</script>