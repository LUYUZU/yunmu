<template>
  <div class="tab-content">
    <div class="content-header">
      <h2><i class="fas fa-walking"></i> 姿态识别</h2>
      <div class="header-actions">
        <select v-model="postureFilter.animalId" @change="$emit('load')">
          <option value="">全部动物</option>
          <option v-for="a in animals" :key="a.id" :value="a.id">{{ a.id }}</option>
        </select>
        <button class="btn btn-primary" :class="{loading: loading.posture}" @click="$emit('load')"><i class="fas fa-download"></i> 加载数据</button>
        <button class="btn btn-success" :class="{loading: loading.posture}" @click="$emit('analyze')"><i class="fas fa-search"></i> 分析姿态</button>
      </div>
    </div>
    <div class="charts-row">
      <div class="dashboard-card"><div class="card-header"><h3><i class="fas fa-chart-pie"></i> 姿态分布</h3></div><div class="chart-container" ref="pieRef"></div></div>
      <div class="dashboard-card"><div class="card-header"><h3><i class="fas fa-chart-bar"></i> 姿态时长统计</h3></div><div class="chart-container" ref="barRef"></div></div>
    </div>
    <div class="table-container">
      <table class="data-table">
        <thead><tr><th>时间</th><th>动物ID</th><th>姿态</th><th>置信度</th><th>持续时长</th></tr></thead>
        <tbody>
          <tr v-for="(r,i) in postureRecords" :key="i">
            <td>{{ r.time }}</td><td>{{ r.animalId }}</td><td>{{ r.posture }}</td>
            <td><span class="confidence-badge" :class="r.confidence>=90?'confidence-high':''">{{ r.confidence }}%</span></td>
            <td>{{ r.duration }}分钟</td>
          </tr>
          <tr v-if="!postureRecords.length"><td colspan="5" class="table-empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<script>
import * as echarts from 'echarts'
export default {
  name: 'PostureTab',
  props: { animals: Array, postureFilter: Object, postureRecords: Array, loading: Object },
  watch: { postureRecords() { this.$nextTick(() => this.render()) } },
  mounted() { this.$nextTick(() => this.render()) },
  beforeDestroy() { this.pieChart?.dispose(); this.barChart?.dispose() },
  methods: {
    render() { this.renderPie(); this.renderBar() },
    renderPie() {
      if (!this.$refs.pieRef) return
      if (this.pieChart) this.pieChart.dispose()
      this.pieChart = echarts.init(this.$refs.pieRef)
      const counts = {}
      this.postureRecords.forEach(r => { counts[r.posture] = (counts[r.posture]||0)+1 })
      const data = Object.entries(counts).map(([name,value])=>({name,value}))
      if (!data.length) return
      this.pieChart.setOption({ tooltip:{trigger:'item'}, legend:{top:'5%',left:'center',textStyle:{color:'#5A6B7D'}},
        series:[{ name:'姿态分布', type:'pie', radius:['40%','70%'], avoidLabelOverlap:false,
          label:{show:true,position:'inside',formatter:'{b}: {d}%',color:'#fff',fontWeight:'bold',fontSize:12},
          itemStyle:{borderRadius:10,borderColor:'#fff',borderWidth:2}, data }] })
    },
    renderBar() {
      if (!this.$refs.barRef) return
      if (this.barChart) this.barChart.dispose()
      this.barChart = echarts.init(this.$refs.barRef)
      const dur = {}
      this.postureRecords.forEach(r => { dur[r.posture] = (dur[r.posture]||0)+r.duration })
      const entries = Object.entries(dur)
      if (!entries.length) return
      this.barChart.setOption({ tooltip:{trigger:'axis'},
        xAxis:{type:'category',data:entries.map(([n])=>n),axisLabel:{color:'#5A6B7D'}},
        yAxis:{type:'value',name:'分钟',axisLabel:{color:'#5A6B7D'}},
        series:[{data:entries.map(([,v])=>v),type:'bar',
          itemStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#5BA8D8'},{offset:1,color:'#3498DB'}])} }] })
    }
  }
}
</script>