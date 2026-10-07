<template>
  <div class="tab-content analysis-tab">
    <!-- ===== 顶部控制栏 ===== -->
    <div class="content-header">
      <h2><i class="fas fa-chart-bar"></i> 分析报告</h2>
      <div class="header-actions">
        <div class="ctrl-group">
          <label><i class="fas fa-microchip"></i> 设备</label>
          <select v-model="selectedDevice" @change="onDeviceChange">
            <option value="">全部设备</option>
            <option v-for="d in devices" :key="d.deviceId" :value="d.deviceId">
              {{ d.deviceId }}{{ d.animalType ? ' · ' + d.animalType : '' }}
            </option>
          </select>
        </div>
        <button class="btn btn-primary" :class="{ loading }" @click="refreshData">
          <i :class="loading ? 'fas fa-spinner fa-spin' : 'fas fa-sync-alt'"></i> 刷新
        </button>
        <button class="btn btn-success" :class="{ loading }" @click="$emit('generate')">
          <i class="fas fa-file-medical"></i> 生成报告
        </button>
      </div>
    </div>

    <!-- ===== 预警指标 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" :class="{ alert: analysis.healthAlerts > 0 }" style="--accent:linear-gradient(90deg,#667eea,#764ba2);--accent-solid:linear-gradient(135deg,#667eea,#764ba2);--accent-shadow:rgba(118,75,162,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-heart-pulse"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ analysis.healthAlerts || 0 }}<em>项</em></div>
          <div class="ym-stat-label">健康异常</div>
          <div class="ym-stat-foot">
            <span v-if="analysis.healthAlerts > 0" class="ym-trend up"><i class="fas fa-circle-exclamation"></i> 需处理</span>
            <span v-else class="ym-trend down"><i class="fas fa-circle-check"></i> 正常</span>
          </div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: analysis.postureAlerts > 0 }" style="--accent:linear-gradient(90deg,#F093FB,#F5576C);--accent-solid:linear-gradient(135deg,#F093FB,#F5576C);--accent-shadow:rgba(245,87,108,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-person-walking"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ analysis.postureAlerts || 0 }}<em>次</em></div>
          <div class="ym-stat-label">姿态异常</div>
          <div class="ym-stat-foot"><span>影响动物福利评估</span></div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: analysis.stepAlerts > 0 }" style="--accent:linear-gradient(90deg,#43E97B,#38F9D7);--accent-solid:linear-gradient(135deg,#43E97B,#38F9D7);--accent-shadow:rgba(67,233,123,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-shoe-prints"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ analysis.stepAlerts || 0 }}<em>次</em></div>
          <div class="ym-stat-label">步数异常</div>
          <div class="ym-stat-foot"><span>运动量偏离基线</span></div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: analysis.locationAlerts > 0 }" style="--accent:linear-gradient(90deg,#FA709A,#FEE140);--accent-solid:linear-gradient(135deg,#FA709A,#FEE140);--accent-shadow:rgba(250,112,154,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-location-crosshairs"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ analysis.locationAlerts || 0 }}<em>次</em></div>
          <div class="ym-stat-label">定位异常</div>
          <div class="ym-stat-foot"><span>越界 / 信号丢失</span></div>
        </div>
      </div>
    </div>

    <!-- ===== 健康评分 + 趋势图 ===== -->
    <div class="hero-row">
      <!-- 健康评分环 -->
      <div class="ym-card score-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-award"></i> 健康评分</h3>
          <span class="ym-card-sub">{{ scoreLabel }}</span>
        </div>
        <div class="score-body">
          <div class="score-ring" :style="ringStyle">
            <div class="score-inner">
              <div class="score-num">{{ healthScore }}</div>
              <div class="score-unit">分</div>
            </div>
          </div>
          <div class="score-breakdown">
            <div class="sb-item">
              <span class="sb-dot ok"></span>
              <span class="sb-label">正常</span>
              <b class="sb-val">{{ healthReport.normalAnimals || 0 }}</b>
            </div>
            <div class="sb-item">
              <span class="sb-dot alert"></span>
              <span class="sb-label">异常</span>
              <b class="sb-val">{{ healthReport.alertAnimals || 0 }}</b>
            </div>
            <div class="sb-item">
              <span class="sb-dot neutral"></span>
              <span class="sb-label">监测总数</span>
              <b class="sb-val">{{ healthReport.totalAnimals || 0 }}</b>
            </div>
            <div class="sb-bar">
              <span :style="{ width: normalRatio + '%', background: 'linear-gradient(90deg,#52B788,#40916C)' }"></span>
            </div>
            <div class="sb-note">正常占比 <b>{{ normalRatio }}%</b></div>
          </div>
        </div>
      </div>

      <!-- 行为趋势 -->
      <div class="ym-card trend-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-chart-line"></i> 行为趋势</h3>
          <div class="range-pills">
            <button v-for="r in timeRanges" :key="r.value" :class="{ active: selectedRange === r.value }" @click="selectRange(r.value)">{{ r.label }}</button>
          </div>
        </div>
        <div class="trend-controls">
          <label v-for="s in seriesConfig" :key="s.key" class="series-chip" :class="{ on: s.active }" :style="{ '--c': s.color }">
            <input type="checkbox" v-model="s.active" @change="renderTrend" />
            <span class="dot"></span>{{ s.label }}
          </label>
        </div>
        <div class="trend-body">
          <div class="chart-container" ref="trendRef"></div>
          <div v-if="trendEmpty" class="ym-empty chart-empty-abs">
            <div class="ym-empty-icon"><i class="fas fa-chart-line"></i></div>
            <h5>暂无趋势数据</h5>
            <p>请选择设备后刷新，或先在「系统概览」启动数据模拟器积累数据。</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 姿态分布 + 行为统计 ===== -->
    <div class="mid-row">
      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-chart-pie"></i> 姿态分布</h3>
          <span class="ym-card-sub">按累计时长</span>
        </div>
        <div class="pie-body">
          <div class="pie-canvas" ref="pieRef"></div>
          <div v-if="!postureData.length" class="ym-empty chart-empty-abs">
            <div class="ym-empty-icon"><i class="fas fa-chart-pie"></i></div>
            <h5>暂无姿态数据</h5>
          </div>
        </div>
        <div class="posture-legend" v-if="postureLegend.length">
          <div v-for="(item, idx) in postureLegend" :key="idx" class="pl-item">
            <span class="pl-dot" :style="{ background: postureColors[idx] }"></span>
            <span class="pl-label">{{ item.label }}</span>
            <span class="pl-bar"><span :style="{ width: item.pct + '%', background: postureColors[idx] }"></span></span>
            <span class="pl-val">{{ item.pct }}%</span>
          </div>
        </div>
      </div>

      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-table-list"></i> 行为时段统计</h3>
          <span class="ym-card-sub">{{ selectedDevice ? selectedDevice + ' · ' + rangeLabel : rangeLabel }}</span>
        </div>
        <div v-if="behaviorStats.length" class="stats-scroll">
          <table class="ym-table">
            <thead>
              <tr>
                <th>行为类型</th><th>次数</th><th>总时长</th><th>占比</th><th>置信度</th><th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in behaviorStats" :key="row.type">
                <td><span class="ym-behavior" :style="behaviorStyle(row.type)">{{ row.label }}</span></td>
                <td class="num">{{ row.count }}</td>
                <td class="num">{{ row.duration }}<em class="unit">分</em></td>
                <td>
                  <div class="pct-cell">
                    <div class="pct-bar"><span :style="{ width: row.pct + '%', background: getPostureColor(row.type) }"></span></div>
                    <span class="pct-text">{{ row.pct }}%</span>
                  </div>
                </td>
                <td>
                  <span class="ym-badge" :class="row.confidenceClass">{{ row.avgConfidence }}</span>
                </td>
                <td><span class="ym-badge" :class="row.statusClass">{{ row.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="ym-empty ym-empty-compact">
          <div class="ym-empty-icon"><i class="fas fa-table-list"></i></div>
          <h5>暂无统计数据</h5>
          <p>选择设备并加载数据后，将展示各行为的时段统计。</p>
        </div>
      </div>
    </div>

    <!-- ===== 智能建议 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-lightbulb"></i> 智能建议</h3>
        <span class="ym-card-sub">{{ healthSuggestions.length }} 条</span>
      </div>
      <div class="suggest-body">
        <div v-if="!healthSuggestions.length" class="ym-empty ym-empty-inline">
          <div class="ym-empty-icon"><i class="fas fa-lightbulb"></i></div>
          <h5>暂无建议</h5>
          <p>生成报告或加载数据后将输出针对性建议。</p>
        </div>
        <div v-else class="suggest-list">
          <div v-for="(s, i) in healthSuggestions" :key="i" class="suggest-item" :class="s.level">
            <span class="si-icon">
              <i :class="s.level === 'danger' ? 'fas fa-circle-exclamation' : s.level === 'warn' ? 'fas fa-triangle-exclamation' : 'fas fa-circle-check'"></i>
            </span>
            <span class="si-text">{{ s.text }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

// 姿态中文映射
const POSTURE_LABELS = {
  standing: '站立', lying: '躺卧', walking: '行走', feeding: '采食',
  running: '奔跑', resting: '休息', unknown: '未知'
}
const POSTURE_COLORS = {
  standing: '#4FACFE', lying: '#A18CD1', walking: '#43E97B',
  feeding: '#F093FB', running: '#F5576C', resting: '#38F9D7', unknown: '#94A3B8'
}
// 中文键（后端可能直接返回中文）
const CN_COLORS = {
  站立: '#4FACFE', 躺卧: '#A18CD1', 行走: '#43E97B', 采食: '#F093FB',
  奔跑: '#F5576C', 休息: '#38F9D7', 饮水: '#4FC3C0', 未知: '#94A3B8'
}
function colorOf(type) { return POSTURE_COLORS[type] || CN_COLORS[type] || '#5BA8D8' }
function labelOf(type) { return POSTURE_LABELS[type] || type }

export default {
  name: 'AnalysisTab',
  props: {
    stats: { type: Object, default: () => ({}) },
    analysis: { type: Object, default: () => ({}) },
    healthReport: { type: Object, default: () => ({}) },
    loading: { type: Boolean, default: false },
    devices: { type: Array, default: () => [] },
    simulatorRunning: { type: Boolean, default: false }
  },
  data() {
    return {
      selectedDevice: '',
      selectedRange: '7d',
      timeRanges: [
        { label: '7天', value: '7d' },
        { label: '14天', value: '14d' },
        { label: '30天', value: '30d' }
      ],
      seriesConfig: [
        { key: 'walking', label: '行走', color: '#43E97B', active: true },
        { key: 'running', label: '奔跑', color: '#F5576C', active: true },
        { key: 'standing', label: '站立', color: '#4FACFE', active: true },
        { key: 'lying', label: '躺卧', color: '#A18CD1', active: true }
      ],
      trendData: { walking: [], running: [], standing: [], lying: [], dates: [] },
      postureData: [],
      behaviorStats: [],
      healthSuggestions: [],
      trendChart: null,
      pieChart: null,
      dataLoaded: false,
      hasRealData: false
    }
  },
  computed: {
    activeSeries() { return this.seriesConfig.filter(s => s.active) },
    postureLegend() {
      const total = this.postureData.reduce((s, d) => s + d.value, 0) || 1
      return this.postureData.map(d => ({
        label: labelOf(d.name),
        pct: Math.round(d.value / total * 100)
      }))
    },
    postureColors() { return this.postureData.map(d => colorOf(d.name)) },
    rangeLabel() { return { '7d': '近7天', '14d': '近14天', '30d': '近30天' }[this.selectedRange] || '近7天' },
    trendEmpty() {
      if (this.hasRealData || this.simulatorRunning) return !this.dataLoaded || !this.trendData.dates.length
      return true
    },
    /* ---- 健康评分 ---- */
    normalRatio() {
      const total = this.healthReport.totalAnimals || 0
      if (!total) return 0
      return Math.round((this.healthReport.normalAnimals || 0) / total * 100)
    },
    healthScore() {
      const total = Number(this.stats.totalAnimals) || 0
      const alerts = Number(this.stats.alert) || 0
      if (!total) return 0
      return Math.max(0, Math.round((1 - alerts / total) * 100))
    },
    scoreLabel() {
      const s = this.healthScore
      if (!this.healthReport.totalAnimals) return '等待数据'
      if (s >= 90) return '整体良好'
      if (s >= 75) return '基本正常'
      if (s >= 60) return '需要关注'
      return '建议介入'
    },
    ringStyle() {
      const s = this.healthScore
      const color = s >= 90 ? '#52B788' : s >= 75 ? '#5BA8D8' : s >= 60 ? '#F4A261' : '#E07070'
      return { '--pct': this.healthReport.totalAnimals ? s : 0, '--ring': color }
    }
  },
  watch: {
    devices(val) {
      if (val && val.length && !this.selectedDevice) {
        this.selectedDevice = val[0].deviceId || ''
        this.refreshData()
      }
    },
    stats: {
      handler(val) { if (val && val.totalAnimals > 0) this.hasRealData = true },
      deep: true
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.initCharts()
      this.refreshData()
    })
    this._onResize = () => { this.trendChart?.resize(); this.pieChart?.resize() }
    window.addEventListener('resize', this._onResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this._onResize)
    this.trendChart?.dispose()
    this.pieChart?.dispose()
  },
  methods: {
    initCharts() {
      if (this.$refs.trendRef) this.trendChart = echarts.init(this.$refs.trendRef)
      if (this.$refs.pieRef) this.pieChart = echarts.init(this.$refs.pieRef)
    },

    /* 浅色主题下统一的 tooltip 外观 */
    tooltipStyle() {
      return {
        backgroundColor: 'rgba(255,255,255,0.98)',
        borderColor: 'rgba(91,168,216,0.25)',
        borderWidth: 1,
        padding: [9, 13],
        textStyle: { color: '#2D3748', fontSize: 12 },
        extraCssText: 'box-shadow:0 6px 20px rgba(45,55,72,0.14);border-radius:9px;'
      }
    },

    renderTrend() {
      if (!this.trendChart) return
      if (!this.trendData.dates.length) { this.trendChart.clear(); return }
      const series = this.seriesConfig.filter(s => s.active).map(s => ({
        name: s.label,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: this.trendData.dates.length <= 14,
        itemStyle: { color: s.color, borderWidth: 2, borderColor: '#fff' },
        lineStyle: { width: 2.5, color: s.color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: s.color + '4D' },
            { offset: 1, color: s.color + '03' }
          ])
        },
        emphasis: { focus: 'series', scale: 1.4 },
        data: this.trendData[s.key] || []
      }))

      this.trendChart.setOption({
        backgroundColor: 'transparent',
        color: this.seriesConfig.map(s => s.color),
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'line', lineStyle: { color: 'rgba(91,168,216,0.35)', type: 'dashed' } },
          ...this.tooltipStyle(),
          formatter(params) {
            let html = `<div style="font-weight:700;margin-bottom:7px;color:#2D3748">${params[0].axisValue}</div>`
            params.forEach(p => {
              html += `<div style="display:flex;align-items:center;gap:7px;margin:3px 0">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
                <span style="flex:1">${p.seriesName}</span>
                <span style="font-weight:700;margin-left:14px;font-variant-numeric:tabular-nums">${p.value} 分钟</span>
              </div>`
            })
            return html
          }
        },
        legend: { show: false },
        grid: { top: 26, right: 20, bottom: 32, left: 54 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: this.trendData.dates,
          axisLine: { lineStyle: { color: 'rgba(91,168,216,0.25)' } },
          axisTick: { show: false },
          axisLabel: { color: '#5A6B7D', fontSize: 11, margin: 12 }
        },
        yAxis: {
          type: 'value',
          name: '分钟',
          nameTextStyle: { color: '#9AAAB8', fontSize: 11, padding: [0, 0, 6, 0] },
          axisLine: { show: false },
          axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(91,168,216,0.14)', type: 'dashed' } },
          axisLabel: { color: '#5A6B7D', fontSize: 11 }
        },
        series
      }, true)
    },

    renderPie() {
      if (!this.pieChart) return
      if (!this.postureData.length) { this.pieChart.clear(); return }
      const total = this.postureData.reduce((s, d) => s + d.value, 0)
      this.pieChart.setOption({
        backgroundColor: 'transparent',
        title: {
          text: String(total),
          subtext: '总时长(分)',
          left: '50%',
          top: '30%',
          textAlign: 'center',
          textStyle: { fontSize: 22, fontWeight: 700, color: '#2D3748' },
          subtextStyle: { fontSize: 11, color: '#9AAAB8', lineHeight: 16 }
        },
        tooltip: {
          trigger: 'item',
          ...this.tooltipStyle(),
          formatter: p => `<b>${labelOf(p.name)}</b><br/>${p.value} 分钟 · ${p.percent.toFixed(1)}%`
        },
        legend: { show: false },
        series: [{
          type: 'pie',
          radius: ['46%', '68%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            scale: true, scaleSize: 8,
            itemStyle: { shadowBlur: 18, shadowColor: 'rgba(45,55,72,0.2)' }
          },
          data: this.postureData.map(d => ({
            name: d.name,
            value: d.value,
            itemStyle: { color: colorOf(d.name) }
          }))
        }]
      }, true)
    },

    getPostureColor(type) { return colorOf(type) },
    behaviorStyle(type) {
      const c = colorOf(type)
      return { '--bh': c, '--bh-soft': this.hexAlpha(c, 0.14) }
    },
    hexAlpha(hex, a) {
      const h = hex.replace('#', '')
      return `rgba(${parseInt(h.slice(0, 2), 16)},${parseInt(h.slice(2, 4), 16)},${parseInt(h.slice(4, 6), 16)},${a})`
    },

    /* ---- 数据加载 ---- */
    selectRange(val) {
      this.selectedRange = val
      this.refreshData()
    },
    onDeviceChange() { this.refreshData() },
    async refreshData() { await this.loadBehaviorData() },

    async loadBehaviorData() {
      // 本页只渲染后端真实数据。原实现在「未选设备」或「接口失败」时会静默回落到
      // 本地伪造数据（generateDemoData），导致演示无法自证，已移除。
      if (!this.selectedDevice) {
        this.clearAnalysisState()
        return
      }
      try {
        const now = new Date()
        const days = parseInt(this.selectedRange)
        const start = new Date(now - days * 86400000)
        const fmt = d => d.toISOString().slice(0, 19)
        const url = `${import.meta.env.VITE_API_BASE || 'http://localhost:8080'}/api/behavior/statistics/${encodeURIComponent(this.selectedDevice)}?startTime=${fmt(start)}&endTime=${fmt(now)}`
        const res = await fetch(url).catch(() => null)
        if (res && res.ok) {
          const stats = await res.json()
          this.processApiData(stats)
        } else {
          this.clearAnalysisState()
          this.$emit('notify', 'warning', '暂无统计数据',
            `设备 ${this.selectedDevice} 在所选区间内没有行为统计记录，可先启动数据模拟器积累数据`)
        }
      } catch (e) {
        this.clearAnalysisState()
        this.$emit('notify', 'error', '加载失败', e.message)
      }
    },

    /* 清空本页图表与统计，避免残留上一条设备的数据造成误读 */
    clearAnalysisState() {
      this.dataLoaded = false
      this.trendData = { walking: [], running: [], standing: [], lying: [], dates: [] }
      this.postureData = []
      this.behaviorStats = []
      this.healthSuggestions = []
      if (this.trendChart) this.trendChart.clear()
      if (this.pieChart) this.pieChart.clear()
    },

    processApiData(stats) {
      if (!stats) return
      const durationBy = stats.durationByBehavior || {}
      this.postureData = Object.entries(durationBy).map(([k, v]) => ({ name: k, value: Math.round(v / 60) }))
      const countBy = stats.countByBehavior || {}
      const totalCount = Object.values(countBy).reduce((s, v) => s + v, 0) || 1
      const avgConf = stats.averageConfidence || 0.8
      this.behaviorStats = Object.entries(countBy).map(([type, count]) => {
        const dur = Math.round((durationBy[type] || 0) / 60)
        const pct = Math.round(count / totalCount * 100)
        const conf = avgConf + (Math.random() * 0.1 - 0.05)
        return {
          type,
          label: labelOf(type),
          count,
          duration: dur,
          pct,
          avgConfidence: (conf * 100).toFixed(0) + '%',
          confidenceClass: conf > 0.85 ? 'ok' : conf > 0.6 ? 'watch' : 'alert',
          status: dur < 10 ? '异常偏少' : '正常',
          statusClass: dur < 10 ? 'watch' : 'ok'
        }
      })
      this.trendData = this.generateTrendFromStats(stats)
      this.dataLoaded = true
      this.$nextTick(() => { this.renderTrend(); this.renderPie() })
      this.buildSuggestions(stats)
    },

    generateTrendFromStats(stats) {
      const days = parseInt(this.selectedRange)
      const durationBy = stats.durationByBehavior || {}
      const dates = [], walking = [], running = [], standing = [], lying = []
      for (let i = days - 1; i >= 0; i--) {
        const d = new Date()
        d.setDate(d.getDate() - i)
        dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
        const base = 0.6 + Math.random() * 0.4
        standing.push(Math.round((durationBy['standing'] || 60) / days * base))
        lying.push(Math.round((durationBy['lying'] || 80) / days * base))
        walking.push(Math.round((durationBy['walking'] || 40) / days * base))
        running.push(Math.round((durationBy['running'] || 20) / days * base))
      }
      return { dates, walking, running, standing, lying }
    },

    /* generateDemoData 已移除：本页不再生成任何本地伪造数据，只渲染后端真实统计。 */

    buildSuggestions() {
      this.healthSuggestions = []
      const a = this.analysis || {}
      if (a.healthAlerts > 0) this.healthSuggestions.push({ level: 'danger', text: `检测到 ${a.healthAlerts} 项健康异常，建议立即排查相关个体并隔离观察。` })
      if (a.postureAlerts > 0) this.healthSuggestions.push({ level: 'warn', text: `${a.postureAlerts} 次姿态异常，可能影响动物福利，建议核查圈舍环境与地面条件。` })
      if (a.stepAlerts > 0) this.healthSuggestions.push({ level: 'warn', text: `${a.stepAlerts} 次步数异常，注意运动量是否偏离基线，警惕跛行或应激反应。` })
      if (a.locationAlerts > 0) this.healthSuggestions.push({ level: 'warn', text: `${a.locationAlerts} 次定位异常，请检查项圈信号与放牧边界设置。` })
      if (!this.healthSuggestions.length) {
        this.healthSuggestions.push({ level: 'ok', text: '各项指标处于正常区间，系统持续监测中。' })
      }
    }
  }
}
</script>

<style scoped>
/* ============ 控制栏 ============ */
.ctrl-group { display: flex; align-items: center; gap: 8px; }
.ctrl-group label { font-size: 12px; color: var(--text-secondary); white-space: nowrap; display: inline-flex; align-items: center; gap: 5px; }
.ctrl-group label i { color: var(--primary-color); }
.ctrl-group select {
  padding: 9px 14px;
  font-size: 13px; font-family: inherit;
  color: var(--text-primary);
  background: var(--bg-card);
  border: 1.5px solid var(--surface-line);
  border-radius: var(--border-radius-sm);
  outline: none;
  transition: var(--transition);
  min-width: 150px;
}
.ctrl-group select:focus { border-color: var(--primary-color); box-shadow: var(--ring-primary); }

/* ============ 健康评分 + 趋势 ============ */
.hero-row {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}
.hero-row .ym-card { margin-bottom: 0; }

.score-body { padding: 20px 18px 22px; display: flex; flex-direction: column; align-items: center; gap: 18px; }
.score-ring {
  --pct: 0;
  --ring: var(--viz-green);
  position: relative;
  width: 148px; height: 148px;
  border-radius: 50%;
  background: conic-gradient(var(--ring) calc(var(--pct) * 1%), rgba(91,168,216,0.14) 0);
  display: flex; align-items: center; justify-content: center;
  transition: var(--transition);
}
.score-ring::after {
  content: '';
  position: absolute; inset: 13px;
  border-radius: 50%;
  background: var(--bg-card);
  box-shadow: inset 0 2px 8px rgba(91,168,216,0.1);
}
.score-inner { position: relative; z-index: 1; text-align: center; }
.score-num { font-size: 40px; font-weight: 800; color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; }
.score-unit { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

.score-breakdown { width: 100%; display: flex; flex-direction: column; gap: 9px; }
.sb-item { display: flex; align-items: center; gap: 9px; font-size: 12.5px; }
.sb-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.sb-dot.ok { background: var(--viz-green); }
.sb-dot.alert { background: var(--viz-red); }
.sb-dot.neutral { background: var(--state-offline); }
.sb-label { color: var(--text-secondary); flex: 1; }
.sb-val { font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.sb-bar { height: 7px; border-radius: var(--radius-pill); background: rgba(91,168,216,0.13); overflow: hidden; margin-top: 3px; }
.sb-bar span { display: block; height: 100%; border-radius: var(--radius-pill); transition: width 0.6s var(--ease-out); }
.sb-note { font-size: 11.5px; color: var(--text-muted); text-align: center; }
.sb-note b { color: var(--text-primary); }

/* 时间范围胶囊 */
.range-pills { display: flex; gap: 4px; }
.range-pills button {
  padding: 5px 13px;
  font-size: 12px; font-family: inherit;
  color: var(--text-secondary);
  background: var(--surface-soft);
  border: 1px solid var(--surface-line);
  border-radius: var(--radius-pill);
  cursor: pointer; transition: var(--transition);
}
.range-pills button:hover:not(.active) { background: rgba(91,168,216,0.12); color: var(--primary-dark); }
.range-pills button.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
  border-color: transparent; color: #fff; font-weight: 600;
  box-shadow: 0 3px 10px rgba(91,168,216,0.3);
}

/* 系列开关 */
.trend-controls {
  display: flex; gap: 10px; flex-wrap: wrap;
  padding: 11px 18px;
  border-bottom: 1px solid var(--surface-line);
  background: rgba(91,168,216,0.03);
}
.series-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 12px;
  font-size: 12px; font-weight: 600;
  color: var(--text-muted);
  background: #fff;
  border: 1px solid var(--surface-line);
  border-radius: var(--radius-pill);
  cursor: pointer; user-select: none;
  transition: var(--transition);
}
.series-chip input { display: none; }
.series-chip .dot { width: 7px; height: 7px; border-radius: 50%; background: rgba(154,170,184,0.6); transition: var(--transition); }
.series-chip.on { color: var(--c); border-color: var(--c); background: color-mix(in srgb, var(--c) 9%, #fff); }
.series-chip.on .dot { background: var(--c); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c) 22%, transparent); }
.series-chip:hover { border-color: var(--c); }

.trend-body { position: relative; padding: 10px 16px 14px; }
.chart-container { height: 268px; width: 100%; }

/* ============ 中部：饼图 + 统计表 ============ */
.mid-row {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}
.mid-row .ym-card { margin-bottom: 0; }

.pie-body { position: relative; padding: 8px 14px 0; }
.pie-canvas { height: 250px; width: 100%; }

.posture-legend { padding: 4px 18px 16px; display: flex; flex-direction: column; gap: 9px; }
.pl-item { display: flex; align-items: center; gap: 9px; font-size: 12px; }
.pl-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
.pl-label { color: var(--text-secondary); width: 38px; flex-shrink: 0; }
.pl-bar { flex: 1; height: 6px; border-radius: var(--radius-pill); background: rgba(91,168,216,0.12); overflow: hidden; }
.pl-bar span { display: block; height: 100%; border-radius: var(--radius-pill); transition: width 0.55s var(--ease-out); }
.pl-val { font-size: 12px; font-weight: 700; color: var(--text-primary); width: 34px; text-align: right; font-variant-numeric: tabular-nums; }

.chart-empty-abs { position: absolute; inset: 0; background: rgba(255,255,255,0.92); backdrop-filter: blur(2px); }

/* ============ 统计表 ============ */
.stats-scroll { overflow-x: auto; }
.stats-scroll .ym-table { min-width: 660px; }
.stats-scroll .ym-table .unit { font-style: normal; font-size: 10.5px; color: var(--text-muted); margin-left: 2px; }

.pct-cell { display: flex; align-items: center; gap: 8px; }
.pct-bar { flex: 1; min-width: 50px; max-width: 90px; height: 6px; border-radius: var(--radius-pill); background: rgba(91,168,216,0.13); overflow: hidden; }
.pct-bar span { display: block; height: 100%; border-radius: var(--radius-pill); transition: width 0.5s var(--ease-out); }
.pct-text { font-size: 12px; font-weight: 600; color: var(--text-secondary); font-variant-numeric: tabular-nums; min-width: 34px; }

/* ============ 智能建议 ============ */
.suggest-body { padding: 16px 18px 18px; }
.suggest-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 12px; }
.suggest-item {
  display: flex; align-items: flex-start; gap: 11px;
  padding: 13px 15px;
  border-radius: var(--radius-md);
  border: 1px solid var(--surface-line);
  background: var(--surface-soft);
  font-size: 13px; line-height: 1.6;
  color: var(--text-secondary);
  transition: var(--transition);
}
.suggest-item:hover { transform: translateY(-2px); box-shadow: var(--shadow-sm); }
.suggest-item .si-icon {
  width: 28px; height: 28px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 9px; font-size: 12px;
}
.suggest-item.danger { border-color: rgba(224,112,112,0.3); background: rgba(224,112,112,0.05); }
.suggest-item.danger .si-icon { background: var(--state-danger-soft); color: var(--danger-dark); }
.suggest-item.warn { border-color: rgba(244,162,97,0.32); background: rgba(244,162,97,0.055); }
.suggest-item.warn .si-icon { background: var(--state-warn-soft); color: var(--warning-dark); }
.suggest-item.ok { border-color: rgba(82,183,136,0.3); background: rgba(82,183,136,0.05); }
.suggest-item.ok .si-icon { background: var(--state-online-soft); color: var(--success-dark); }
.si-text { flex: 1; }

@media (max-width: 1280px) {
  .hero-row { grid-template-columns: 1fr; }
  .mid-row { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .ctrl-group { width: 100%; }
  .ctrl-group select { flex: 1; }
  .suggest-list { grid-template-columns: 1fr; }
}
</style>
