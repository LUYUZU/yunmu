<template>
  <div class="tab-content posture-tab">
    <div class="content-header">
      <h2><i class="fas fa-person-walking"></i> 姿态识别</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: busy }" @click="$emit('load')">
          <i class="fas fa-download"></i> 加载数据
        </button>
        <button class="btn btn-success" :class="{ loading: busy }" @click="$emit('analyze')">
          <i class="fas fa-wand-magic-sparkles"></i> 分析姿态
        </button>
      </div>
    </div>

    <!-- ===== 概览指标 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#5BA8D8,#7EC8E3);--accent-solid:linear-gradient(135deg,#5BA8D8,#3498DB);--accent-shadow:rgba(91,168,216,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-clipboard-check"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ records.length }}<em>条</em></div>
          <div class="ym-stat-label">姿态识别记录</div>
          <div class="ym-stat-foot"><span>{{ kindCount }} 种姿态</span></div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#9B8FD4,#7B6FC4);--accent-solid:linear-gradient(135deg,#9B8FD4,#7B6FC4);--accent-shadow:rgba(155,143,212,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-crown"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value dominant">{{ dominant.label }}</div>
          <div class="ym-stat-label">主导姿态</div>
          <div class="ym-stat-progress"><span :style="{ width: dominant.pct + '%', background: 'linear-gradient(90deg,#9B8FD4,#7B6FC4)' }"></span></div>
          <div class="ym-stat-foot"><span>占 <b class="pct">{{ dominant.pct }}%</b></span></div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#4FC3C0,#2C8C89);--accent-solid:linear-gradient(135deg,#4FC3C0,#2C8C89);--accent-shadow:rgba(79,195,192,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-clock"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ totalDuration }}<em>分钟</em></div>
          <div class="ym-stat-label">累计持续时长</div>
          <div class="ym-stat-foot"><span>平均 <b class="pct">{{ avgDuration }}</b> 分钟/次</span></div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: lowConfCount > 0 }" style="--accent:linear-gradient(90deg,#F4A261,#E76F51);--accent-solid:linear-gradient(135deg,#F4A261,#E76F51);--accent-shadow:rgba(244,162,97,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-bullseye"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ avgConfidence }}<em>%</em></div>
          <div class="ym-stat-label">平均识别置信度</div>
          <div class="ym-stat-progress"><span :style="{ width: avgConfidence + '%', background: confGradient }"></span></div>
          <div class="ym-stat-foot">
            <span v-if="lowConfCount" class="ym-trend up"><i class="fas fa-circle-exclamation"></i> {{ lowConfCount }} 条低于 75%</span>
            <span v-else class="ym-trend down"><i class="fas fa-circle-check"></i> 识别质量良好</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 图表区 ===== -->
    <div class="charts-row">
      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-chart-pie"></i> 姿态分布</h3>
          <span class="ym-card-sub">按识别次数</span>
        </div>
        <div class="chart-body">
          <div class="chart-canvas" ref="pieRef"></div>
          <div v-if="!records.length" class="ym-empty chart-empty-abs">
            <div class="ym-empty-icon"><i class="fas fa-chart-pie"></i></div>
            <h5>暂无姿态数据</h5>
            <p>请先加载姿态识别数据。</p>
          </div>
        </div>
      </div>

      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-chart-column"></i> 姿态时长统计</h3>
          <span class="ym-card-sub">单位：分钟</span>
        </div>
        <div class="chart-body">
          <div class="chart-canvas" ref="barRef"></div>
          <div v-if="!records.length" class="ym-empty chart-empty-abs">
            <div class="ym-empty-icon"><i class="fas fa-chart-column"></i></div>
            <h5>暂无时长数据</h5>
            <p>请先加载姿态识别数据。</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 记录表 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-table-list"></i> 姿态识别记录</h3>
        <div class="posture-tools">
          <div class="ym-search">
            <i class="fas fa-search"></i>
            <input v-model.trim="keyword" type="text" placeholder="动物 ID / 姿态" />
            <button v-if="keyword" @click="keyword = ''"><i class="fas fa-times-circle"></i></button>
          </div>
          <select v-model="postureFilterAnimal" class="mini-select">
            <option value="">全部姿态</option>
            <option v-for="p in postureKinds" :key="p" :value="p">{{ p }}</option>
          </select>
        </div>
      </div>

      <div v-if="busy && !records.length" class="table-skeleton">
        <div v-for="n in 5" :key="n" class="ym-skeleton sk-row"></div>
      </div>

      <div v-else-if="!records.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-person-walking"></i></div>
        <h5>暂无姿态识别记录</h5>
        <p>请先在「系统概览」启动数据模拟器，或接入后端姿态识别服务后加载。</p>
        <button class="btn btn-primary" @click="$emit('load')"><i class="fas fa-download"></i> 加载数据</button>
      </div>

      <div v-else-if="!filteredRecords.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-filter-circle-xmark"></i></div>
        <h5>没有符合条件的记录</h5>
        <p>尝试调整筛选条件或清空搜索关键词。</p>
      </div>

      <template v-else>
        <div class="table-scroll">
          <table class="ym-table">
            <thead>
              <tr>
                <th class="sortable" :class="{ sorted: sortKey === 'time' }" @click="toggleSort('time')">
                  时间<i class="fas sort-ind" :class="sortInd('time')"></i>
                </th>
                <th class="sortable" :class="{ sorted: sortKey === 'animalId' }" @click="toggleSort('animalId')">
                  动物 ID<i class="fas sort-ind" :class="sortInd('animalId')"></i>
                </th>
                <th>姿态</th>
                <th class="sortable" :class="{ sorted: sortKey === 'confidence' }" @click="toggleSort('confidence')">
                  置信度<i class="fas sort-ind" :class="sortInd('confidence')"></i>
                </th>
                <th class="sortable" :class="{ sorted: sortKey === 'duration' }" @click="toggleSort('duration')">
                  持续时长<i class="fas sort-ind" :class="sortInd('duration')"></i>
                </th>
                <th>强度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in pagedRecords" :key="i">
                <td class="cell-time">
                  <div class="time-main">{{ r.time }}</div>
                  <div class="time-rel">{{ relative(r.time) }}</div>
                </td>
                <td><span class="animal-chip"><i class="fas fa-paw"></i>{{ r.animalId }}</span></td>
                <td><span class="ym-behavior" :style="behaviorStyle(r.posture)">{{ r.posture || '未知' }}</span></td>
                <td>
                  <div class="ym-conf">
                    <span class="ym-conf-ring" :style="confStyle(r.confidence)"></span>
                    <span class="ym-conf-num">{{ r.confidence || '--' }}%</span>
                  </div>
                </td>
                <td>
                  <div class="ym-meter-cell">
                    <span class="val">{{ r.duration || 0 }}<em class="unit">分</em></span>
                    <div class="bar"><span :style="{ width: durPct(r.duration) + '%', background: durGradient }"></span></div>
                  </div>
                </td>
                <td>
                  <span class="ym-badge" :class="durLevel(r.duration).cls">
                    <i class="fas fa-circle"></i>{{ durLevel(r.duration).text }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="ym-pager">
          <div class="ym-pager-info">第 <b>{{ page }}</b> / {{ totalPages }} 页 · 共 <b>{{ filteredRecords.length }}</b> 条</div>
          <div class="ym-pager-ctrl">
            <button :disabled="page === 1" @click="page = 1"><i class="fas fa-angles-left"></i></button>
            <button :disabled="page === 1" @click="page--"><i class="fas fa-angle-left"></i></button>
            <button v-for="p in pageNumbers" :key="p" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
            <button :disabled="page === totalPages" @click="page++"><i class="fas fa-angle-right"></i></button>
            <button :disabled="page === totalPages" @click="page = totalPages"><i class="fas fa-angles-right"></i></button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

const POSTURE_COLORS = {
  采食: '#F093FB', 站立: '#4FACFE', 行走: '#43E97B', 躺卧: '#A18CD1',
  休息: '#38F9D7', 奔跑: '#F5576C', 饮水: '#4FC3C0'
}

export default {
  name: 'PostureTab',
  props: {
    animals: Array,
    postureFilter: Object,
    postureRecords: Array,
    /* 父组件可能传入 loading 对象或 loading.posture（Boolean） */
    loading: { type: [Object, Boolean], default: () => ({}) }
  },
  data() {
    return {
      keyword: '',
      postureFilterAnimal: '',
      sortKey: 'time',
      sortDir: 'desc',
      page: 1,
      pageSize: 10
    }
  },
  computed: {
    busy() {
      if (typeof this.loading === 'boolean') return this.loading
      return !!(this.loading && this.loading.posture)
    },
    records() { return this.postureRecords || [] },
    postureKinds() {
      return Array.from(new Set(this.records.map(r => r.posture).filter(Boolean)))
    },
    kindCount() { return this.postureKinds.length },
    totalDuration() { return this.records.reduce((s, r) => s + (Number(r.duration) || 0), 0) },
    avgDuration() {
      if (!this.records.length) return '0'
      return Math.round(this.totalDuration / this.records.length)
    },
    avgConfidence() {
      const valid = this.records.map(r => Number(r.confidence)).filter(n => isFinite(n))
      if (!valid.length) return 0
      return Math.round(valid.reduce((s, n) => s + n, 0) / valid.length)
    },
    confGradient() {
      const v = this.avgConfidence
      if (v >= 90) return 'linear-gradient(90deg,#52B788,#40916C)'
      if (v >= 75) return 'linear-gradient(90deg,#5BA8D8,#3498DB)'
      return 'linear-gradient(90deg,#F4A261,#E76F51)'
    },
    lowConfCount() { return this.records.filter(r => Number(r.confidence) < 75).length },
    durGradient() { return 'linear-gradient(90deg,#5BA8D8,#3498DB)' },
    dominant() {
      const counts = {}
      this.records.forEach(r => { const k = r.posture || '未知'; counts[k] = (counts[k] || 0) + 1 })
      const entries = Object.entries(counts)
      if (!entries.length) return { label: '暂无', pct: 0 }
      entries.sort((a, b) => b[1] - a[1])
      const [label, count] = entries[0]
      return { label, pct: Math.round(count / this.records.length * 100) }
    },
    maxDuration() {
      const d = this.records.map(r => Number(r.duration) || 0)
      return d.length ? Math.max(...d) : 0
    },
    filteredRecords() {
      let list = this.records.slice()
      const kw = this.keyword.toLowerCase()
      if (kw) {
        list = list.filter(r =>
          String(r.animalId || '').toLowerCase().includes(kw) ||
          String(r.posture || '').toLowerCase().includes(kw)
        )
      }
      if (this.postureFilterAnimal) list = list.filter(r => r.posture === this.postureFilterAnimal)
      const k = this.sortKey
      const dir = this.sortDir === 'asc' ? 1 : -1
      list.sort((a, b) => {
        if (k === 'confidence' || k === 'duration') return ((Number(a[k]) || 0) - (Number(b[k]) || 0)) * dir
        return String(a[k] ?? '').localeCompare(String(b[k] ?? '')) * dir
      })
      return list
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredRecords.length / this.pageSize)) },
    pagedRecords() {
      const start = (this.page - 1) * this.pageSize
      return this.filteredRecords.slice(start, start + this.pageSize)
    },
    pageNumbers() {
      const total = this.totalPages
      let start = Math.max(1, this.page - 2)
      const end = Math.min(total, start + 4)
      start = Math.max(1, end - 4)
      const out = []
      for (let i = start; i <= end; i++) out.push(i)
      return out
    }
  },
  watch: {
    postureRecords() {
      this.$nextTick(() => this.render())
    },
    keyword() { this.page = 1 },
    postureFilterAnimal() { this.page = 1 },
    filteredRecords() { if (this.page > this.totalPages) this.page = this.totalPages }
  },
  mounted() {
    this.$nextTick(() => this.render())
    this._onResize = () => { this.pieChart?.resize(); this.barChart?.resize() }
    window.addEventListener('resize', this._onResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this._onResize)
    this.pieChart?.dispose()
    this.barChart?.dispose()
  },
  methods: {
    render() { this.renderPie(); this.renderBar() },

    renderPie() {
      if (!this.$refs.pieRef) return
      if (!this.pieChart) this.pieChart = echarts.init(this.$refs.pieRef)
      const counts = {}
      this.records.forEach(r => { const k = r.posture || '未知'; counts[k] = (counts[k] || 0) + 1 })
      const entries = Object.entries(counts)
      const total = entries.reduce((s, [, v]) => s + v, 0)
      const data = entries.map(([name, value]) => ({
        name, value,
        itemStyle: { color: POSTURE_COLORS[name] || '#94A3B8' }
      }))
      this.pieChart.setOption({
        // 环心总量标签（浅色主题，与页面其余图表一致）
        title: total ? {
          text: String(total),
          subtext: '总次数',
          left: 'center',
          top: '40%',
          textStyle: { fontSize: 24, fontWeight: 700, color: '#2D3748' },
          subtextStyle: { fontSize: 11, color: '#9AAAB8' }
        } : undefined,
        tooltip: {
          trigger: 'item',
          backgroundColor: 'rgba(255,255,255,0.98)',
          borderColor: 'rgba(91,168,216,0.25)',
          borderWidth: 1,
          padding: [9, 13],
          textStyle: { color: '#2D3748', fontSize: 12 },
          extraCssText: 'box-shadow:0 6px 20px rgba(45,55,72,0.14);border-radius:9px;',
          formatter: p => `<b>${p.name}</b><br/>${p.value} 次 · ${p.percent.toFixed(1)}%`
        },
        legend: {
          bottom: 0, left: 'center',
          icon: 'circle', itemWidth: 8, itemHeight: 8, itemGap: 12,
          textStyle: { color: '#5A6B7D', fontSize: 11 }
        },
        series: [{
          name: '姿态分布',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            scale: true, scaleSize: 7,
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(45,55,72,0.2)' }
          },
          data: data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#E4EBF2' } }]
        }]
      }, true)
    },

    renderBar() {
      if (!this.$refs.barRef) return
      if (!this.barChart) this.barChart = echarts.init(this.$refs.barRef)
      const dur = {}
      this.records.forEach(r => { const k = r.posture || '未知'; dur[k] = (dur[k] || 0) + (Number(r.duration) || 0) })
      const entries = Object.entries(dur).sort((a, b) => b[1] - a[1])
      this.barChart.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(91,168,216,0.08)' } },
          backgroundColor: 'rgba(255,255,255,0.98)',
          borderColor: 'rgba(91,168,216,0.25)',
          borderWidth: 1,
          padding: [9, 13],
          textStyle: { color: '#2D3748', fontSize: 12 },
          extraCssText: 'box-shadow:0 6px 20px rgba(45,55,72,0.14);border-radius:9px;',
          formatter: p => `<b>${p[0].axisValue}</b><br/>累计 ${p[0].value} 分钟`
        },
        grid: { top: 24, right: 18, bottom: 30, left: 52 },
        xAxis: {
          type: 'category',
          data: entries.map(([n]) => n),
          axisLine: { lineStyle: { color: 'rgba(91,168,216,0.25)' } },
          axisTick: { show: false },
          axisLabel: { color: '#5A6B7D', fontSize: 11 }
        },
        yAxis: {
          type: 'value',
          name: '分钟',
          nameTextStyle: { color: '#9AAAB8', fontSize: 11, padding: [0, 0, 6, 0] },
          axisLine: { show: false },
          axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(91,168,216,0.12)', type: 'dashed' } },
          axisLabel: { color: '#5A6B7D', fontSize: 11 }
        },
        series: [{
          type: 'bar',
          barMaxWidth: 40,
          data: entries.map(([n, v]) => ({
            value: v,
            itemStyle: {
              borderRadius: [7, 7, 0, 0],
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: POSTURE_COLORS[n] || '#5BA8D8' },
                { offset: 1, color: (POSTURE_COLORS[n] || '#5BA8D8') + '66' }
              ])
            }
          })),
          label: {
            show: true, position: 'top',
            color: '#5A6B7D', fontSize: 11, fontWeight: 600
          }
        }]
      }, true)
    },

    /* ---- 记录表辅助 ---- */
    sortInd(key) {
      if (this.sortKey !== key) return 'fa-sort'
      return this.sortDir === 'asc' ? 'fa-arrow-up-short-wide' : 'fa-arrow-down-wide-short'
    },
    toggleSort(key) {
      if (this.sortKey === key) this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc'
      else { this.sortKey = key; this.sortDir = key === 'time' ? 'desc' : 'asc' }
    },
    relative(timeStr) {
      if (!timeStr) return ''
      const t = new Date(String(timeStr).replace(/-/g, '/')).getTime()
      if (isNaN(t)) return ''
      const diff = Date.now() - t
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
      return Math.floor(diff / 86400000) + ' 天前'
    },
    behaviorStyle(name) {
      const c = POSTURE_COLORS[name] || '#5BA8D8'
      return { '--bh': c, '--bh-soft': this.hexAlpha(c, 0.14) }
    },
    hexAlpha(hex, a) {
      const h = hex.replace('#', '')
      return `rgba(${parseInt(h.slice(0, 2), 16)},${parseInt(h.slice(2, 4), 16)},${parseInt(h.slice(4, 6), 16)},${a})`
    },
    confStyle(v) {
      const n = Number(v) || 0
      const ring = n >= 90 ? 'var(--viz-green)' : n >= 75 ? 'var(--viz-blue)' : 'var(--viz-amber)'
      return { '--pct': Math.max(0, Math.min(100, n)), '--ring': ring }
    },
    durPct(v) {
      if (!this.maxDuration) return 0
      return Math.max(4, Math.min(100, ((Number(v) || 0) / this.maxDuration) * 100))
    },
    durLevel(v) {
      const n = Number(v) || 0
      if (n >= 30) return { text: '高强度', cls: 'alert' }
      if (n >= 10) return { text: '中强度', cls: 'watch' }
      return { text: '低强度', cls: 'ok' }
    }
  }
}
</script>

<style scoped>
.charts-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; margin-bottom: 18px; }
.charts-row .ym-card { margin-bottom: 0; }

.chart-body { position: relative; padding: 10px 14px 14px; }
.chart-canvas { height: 288px; width: 100%; }
.chart-empty-abs { position: absolute; inset: 0; background: rgba(255,255,255,0.9); backdrop-filter: blur(2px); }

.table-scroll { overflow-x: auto; }
.table-scroll .ym-table { min-width: 820px; }
.cell-time .time-main { font-size: 13px; color: var(--text-secondary); font-variant-numeric: tabular-nums; white-space: nowrap; }
.cell-time .time-rel { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.animal-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: var(--font-mono);
  font-size: 12.5px; font-weight: 700;
  color: var(--primary-dark);
}
.animal-chip i { font-size: 11px; color: var(--primary-color); }

.ym-meter-cell .val .unit { font-style: normal; font-size: 10.5px; font-weight: 600; color: var(--text-muted); margin-left: 2px; }

.posture-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.posture-tools .ym-search { min-width: 190px; }
.mini-select {
  padding: 8px 13px;
  font-size: 12.5px;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-soft);
  border: 1.5px solid var(--surface-line);
  border-radius: var(--radius-pill);
  outline: none;
  cursor: pointer;
  transition: var(--transition);
}
.mini-select:focus { border-color: var(--primary-color); background: #fff; }

.table-skeleton { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.sk-row { height: 20px; }
.sk-row:nth-child(odd) { width: 92%; }

.ym-stat-value.dominant { font-size: 22px; }
.pct { color: var(--text-primary); font-weight: 700; }

@media (max-width: 992px) {
  .charts-row { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .posture-tools { width: 100%; }
  .posture-tools .ym-search { flex: 1 1 100%; }
}
</style>
