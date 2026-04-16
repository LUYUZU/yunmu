<template>
  <div class="tab-content">
    <div class="content-header">
      <h2><i class="fas fa-shoe-prints"></i> 步数统计</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{loading: loading.steps}" @click="$emit('load')"><i class="fas fa-download"></i> 加载数据</button>
        <button class="btn btn-success" :class="{loading: loading.steps}" @click="$emit('refresh')"><i class="fas fa-sync-alt"></i> 刷新统计</button>
      </div>
    </div>
    <div class="stats-cards">
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#f093fb,#f5576c)"><i class="fas fa-shoe-prints"></i></div><div class="stat-info"><div class="stat-value">{{ stepStats.todaySteps }}</div><div class="stat-label">今日步数</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#4facfe,#00f2fe)"><i class="fas fa-walking"></i></div><div class="stat-info"><div class="stat-value">{{ stepStats.walkingDistance }} m</div><div class="stat-label">行走距离</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#43e97b,#38f9d7)"><i class="fas fa-clock"></i></div><div class="stat-info"><div class="stat-value">{{ stepStats.activeTime }} 分钟</div><div class="stat-label">活跃时长</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#fa709a,#fee140)"><i class="fas fa-tachometer-alt"></i></div><div class="stat-info"><div class="stat-value">{{ stepStats.stepFrequency }}</div><div class="stat-label">步频(步/分)</div></div></div>
    </div>
    <div class="dashboard-card"><div class="card-header"><h3><i class="fas fa-chart-line"></i> 24小时步数趋势</h3></div><div class="chart-container" ref="trendRef"></div></div>
    <div class="charts-row">
      <div class="dashboard-card"><div class="card-header"><h3><i class="fas fa-chart-pie"></i> 活跃程度分布</h3></div><div class="chart-container" ref="pieRef"></div></div>
      <div class="dashboard-card">
        <div class="card-header"><h3><i class="fas fa-list"></i> 小时级步数详情</h3></div>
        <div class="table-container">
          <table class="data-table">
            <thead><tr><th>时间</th><th>步数</th><th>距离</th><th>活跃时长</th></tr></thead>
            <tbody>
              <tr v-for="r in stepHourlyData" :key="r.hour"><td>{{ r.hour }}:00</td><td>{{ r.steps }}</td><td>{{ r.distance }}m</td><td>{{ r.activeTime }}分钟</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import * as echarts from 'echarts'
export default {
  name: 'StepsTab',
  props: { stepStats: Object, stepHourlyData: Array, loading: Object },
  watch: { stepHourlyData() { this.$nextTick(() => this.render()) } },
  mounted() { this.$nextTick(() => this.render()) },
  beforeDestroy() { this.trendChart?.dispose(); this.pieChart?.dispose() },
  methods: {
    render() { this.renderTrend(); this.renderPie() },
    renderTrend() {
      if (!this.$refs.trendRef || !this.stepHourlyData.length) return
      if (this.trendChart) this.trendChart.dispose()
      this.trendChart = echarts.init(this.$refs.trendRef)
      this.trendChart.setOption({ tooltip:{trigger:'axis'},
        xAxis:{type:'category',data:this.stepHourlyData.map(d=>`${d.hour}:00`),axisLabel:{color:'#5A6B7D'}},
        yAxis:{type:'value',name:'步数',axisLabel:{color:'#5A6B7D'}},
        series:[{data:this.stepHourlyData.map(d=>d.steps),type:'line',smooth:true,itemStyle:{color:'#f093fb'},
          areaStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(240,147,251,0.5)'},{offset:1,color:'rgba(240,147,251,0.1)'}])} }] })
    },
    renderPie() {
      if (!this.$refs.pieRef) return
      if (this.pieChart) this.pieChart.dispose()
      this.pieChart = echarts.init(this.$refs.pieRef)
      this.pieChart.setOption({ tooltip:{trigger:'item'}, legend:{top:'5%',left:'center',textStyle:{color:'#5A6B7D'}},
        series:[{ name:'活跃程度', type:'pie', radius:['40%','70%'], avoidLabelOverlap:false,
          itemStyle:{borderRadius:10,borderColor:'#fff',borderWidth:2},
          label:{show:false,position:'center'},
          data:[{value:Math.floor(Math.random()*30)+10,name:'低'},{value:Math.floor(Math.random()*40)+20,name:'中低'},{value:Math.floor(Math.random()*40)+30,name:'中高'},{value:Math.floor(Math.random()*30)+10,name:'高'}] }] })
    }
  }
}
</script>