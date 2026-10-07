<template>
  <div class="tab-content steps-tab">
    <!-- ===== 头部 ===== -->
    <div class="content-header">
      <h2><i class="fas fa-shoe-prints"></i> 步数统计</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: busy }" @click="$emit('load')">
          <i class="fas fa-download"></i> 加载数据
        </button>
        <button class="btn btn-success" :class="{ loading: busy }" @click="$emit('refresh')">
          <i class="fas fa-sync-alt"></i> 刷新统计
        </button>
      </div>
    </div>

    <!-- ===== 工具栏 ===== -->
    <div class="ym-toolbar">
      <div class="ym-field">
        <label><i class="fas fa-microchip"></i> 设备</label>
        <select v-model="selectedDevice" @change="onDeviceChange">
          <option value="">全部设备</option>
          <option v-for="d in devices" :key="d.deviceId" :value="d.deviceId">
            {{ d.deviceId }}{{ d.animalType ? ` (${d.animalType})` : '' }}
          </option>
        </select>
      </div>

      <div class="ym-field">
        <label><i class="fas fa-calendar-alt"></i> 统计范围</label>
        <div class="ym-range">
          <button
            v-for="r in timeRanges" :key="r.value"
            :class="{ active: selectedRange === r.value }"
            @click="selectRange(r.value)">{{ r.label }}</button>
        </div>
      </div>

      <div class="ym-actions">
        <button class="ym-search-btn" @click="showAll = !showAll">
          <i :class="showAll ? 'fas fa-compress-alt' : 'fas fa-expand-alt'"></i>
          {{ showAll ? '仅看活跃时段' : '查看全部小时' }}
        </button>
      </div>
    </div>

    <!-- ===== 统计卡 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#F093FB,#F5576C);--accent-solid:linear-gradient(135deg,#F093FB,#F5576C);--accent-shadow:rgba(245,87,108,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-shoe-prints"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ fmtNum(cardSteps) }}<em>步</em></div>
          <div class="ym-stat-label">今日步数</div>
          <div class="ym-stat-progress"><span :style="{ width: stepsProgress + '%', background: 'linear-gradient(90deg,#F093FB,#F5576C)' }"></span></div>
          <div class="ym-stat-foot">完成 {{ stepsProgress }}% · 参考目标 {{ fmtNum(stepsGoal) }} 步</div>
        </div>
      </div>

      <div class="ym-stat" style="--accent:linear-gradient(90deg,#4FACFE,#00F2FE);--accent-solid:linear-gradient(135deg,#4FACFE,#00F2FE);--accent-shadow:rgba(79,172,254,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-route"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ fmtNum(cardDistance) }}<em>m</em></div>
          <div class="ym-stat-label">行走距离</div>
          <div class="ym-stat-foot"><i class="fas fa-gauge-high"></i> 活跃密度 {{ fmtNum(densityPerHour) }} 步/时</div>
        </div>
      </div>

      <div class="ym-stat" style="--accent:linear-gradient(90deg,#43E97B,#38F9D7);--accent-solid:linear-gradient(135deg,#43E97B,#38F9D7);--accent-shadow:rgba(67,233,123,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-clock"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ fmtNum(cardActiveTime) }}<em>分钟</em></div>
          <div class="ym-stat-label">活跃时长</div>
          <div class="ym-stat-foot"><i class="fas fa-bolt"></i> 活跃时段 {{ activeHours }} 小时</div>
        </div>
      </div>

      <div class="ym-stat" style="--accent:linear-gradient(90deg,#FA709A,#FEE140);--accent-solid:linear-gradient(135deg,#FA709A,#FEE140);--accent-shadow:rgba(250,112,154,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-tachometer-alt"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ cardFrequency % 1 === 0 ? cardFrequency : cardFrequency.toFixed(1) }}<em>步/分</em></div>
          <div class="ym-stat-label">平均步频</div>
          <div class="ym-stat-foot"><span class="ym-badge" :class="freqLevel.cls">{{ freqLevel.text }}</span></div>
        </div>
      </div>
    </div>

    <!-- ===== 24 小时趋势 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-chart-area"></i> 24 小时步数趋势</h3>
        <div class="ycm-meta">
          <span class="ycm-peak" v-if="peakHour">
            <i class="fas fa-arrow-trend-up"></i> 峰值 {{ peakHour.hourLabel }}:00 · {{ fmtNum(peakHour.steps) }} 步
          </span>
          <span class="ym-card-sub">共 {{ hourlyRows.length }} 个时段</span>
        </div>
      </div>
      <div class="ym-card-body">
        <div class="chart-container" ref="trendRef"></div>
        <div v-if="!hourlyRows.length" class="ym-empty ym-empty-compact chart-empty-abs">
          <div class="ym-empty-icon"><i class="fas fa-chart-area"></i></div>
          <h5>暂无步数数据</h5>
          <p>请选择设备并加载数据，或先在「系统概览」启动数据模拟器。</p>
        </div>
      </div>
    </div>

    <!-- ===== 分布 + 明细 ===== -->
    <div class="ym-charts-row">
      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-chart-pie"></i> 活跃程度分布</h3>
          <span class="ym-card-sub">按小时分档</span>
        </div>
        <div class="ym-card-body">
          <div class="chart-container pie-container" ref="pieRef"></div>
          <div v-if="!hasPieData" class="ym-empty ym-empty-compact chart-empty-abs">
            <div class="ym-empty-icon"><i class="fas fa-chart-pie"></i></div>
            <h5>暂无活跃分布</h5>
          </div>
        </div>
      </div>

      <div class="ym-card">
        <div class="ym-card-head">
          <h3><i class="fas fa-list"></i> 小时级步数详情</h3>
          <span class="ym-card-sub">{{ showAll ? '全部 24 小时' : '仅活跃时段' }}</span>
        </div>

        <div v-if="busy && !hourlyRows.length" class="table-skeleton">
          <div class="ym-skeleton" style="height:16px;width:100%"></div>
          <div class="ym-skeleton" style="height:16px;width:100%"></div>
          <div class="ym-skeleton" style="height:16px;width:100%"></div>
          <div class="ym-skeleton" style="height:16px;width:100%"></div>
          <div class="ym-skeleton" style="height:16px;width:100%"></div>
        </div>

        <div v-else-if="!hourlyRows.length" class="ym-empty ym-empty-compact">
          <div class="ym-empty-icon"><i class="fas fa-table-list"></i></div>
          <h5>暂无小时明细</h5>
          <p>加载数据后将展示每个时段的步数、距离与活跃时长。</p>
        </div>

        <div v-else class="ym-table-wrap">
          <table class="ym-table">
            <thead>
              <tr>
                <th class="sortable" :class="{ sorted: sortKey === 'hour' }" @click="toggleSort('hour')">
                  时间 <i :class="sortIcon('hour')"></i>
                </th>
                <th class="sortable num" :class="{ sorted: sortKey === 'steps' }" @click="toggleSort('steps')">
                  步数 <i :class="sortIcon('steps')"></i>
                </th>
                <th class="num">距离</th>
                <th class="num">活跃时长</th>
                <th>活跃程度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in pagedRows" :key="r.hour">
                <td class="td-time">
                  <span class="t-strong">{{ r.hourLabel }}:00</span>
                  <span class="t-sub">{{ r.hourLabel }}:59</span>
                </td>
                <td class="num t-strong">{{ fmtNum(r.steps) }}</td>
                <td class="num">{{ fmtNum(r.distance) }} m</td>
                <td class="num">
                  <div class="ym-meter-cell">
                    <span class="meter-val">{{ fmtNum(r.activeTime) }} 分</span>
                    <span class="meter-bar"><i :style="{ width: activePct(r) + '%', background: levelStyle(r).solid }"></i></span>
                  </div>
                </td>
                <td>
                  <span class="ym-badge" :class="levelStyle(r).cls">{{ levelStyle(r).text }}</span>
                </td>
              </tr>
            </tbody>
          </table>

          <div class="ym-pager">
            <span class="ym-pager-info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ hourlyRows.length }} 条</span>
            <div class="ym-pager-ctrl">
              <button :disabled="page <= 1" @click="page = 1"><i class="fas fa-angles-left"></i></button>
              <button :disabled="page <= 1" @click="page--"><i class="fas fa-angle-left"></i></button>
              <button v-for="p in pageList" :key="p" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
              <button :disabled="page >= totalPages" @click="page++"><i class="fas fa-angle-right"></i></button>
              <button :disabled="page >= totalPages" @click="page = totalPages"><i class="fas fa-angles-right"></i></button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

/**
 * 时段比较器。
 *
 * 背景：本项目有两条数据来源，`hour` 的类型并不一致——
 *   - 后端 `/steps/hourly/{animalId}` 返回的 `hour` 是 **number**（Spring 侧 `Map<Integer,Integer>` 的 key）；
 *   - 早期 mock 数据里是 `'08'` 这样的 **string**。
 *
 * 注意：数字没有 `.localeCompare` 方法，若直接写 `a.hour.localeCompare(b.hour)`，
 * 一旦数据非空、比较器被调用，就会抛 `TypeError`。
 * 该比较器位于 `hourlyRows` 计算属性内，异常会在 render 阶段冒泡，
 * 导致整个组件渲染失败 → **整页空白**（数据为空时比较器从不执行，所以"空状态能显示、一来数据就白屏"）。
 */
function cmpHour(a, b) {
  const na = Number(a.hour)
  const nb = Number(b.hour)
  if (!Number.isNaN(na) && !Number.isNaN(nb)) return na - nb
  return String(a.hour ?? '').localeCompare(String(b.hour ?? ''))
}

export default {
  name: 'StepsTab',
  props: {
    stepStats: { type: Object, default: () => ({}) },
    stepHourlyData: { type: Array, default: () => [] },
    /* 父组件传入的是 loading.steps（Boolean），兼容对象写法 */
    loading: { type: [Object, Boolean], default: () => ({}) },
    devices: { type: Array, default: () => [] },
    simulatorRunning: { type: Boolean, default: false }
  },
  data() {
    return {
      selectedDevice: '',
      selectedRange: '7d',
      showAll: false,
      sortKey: 'hour',
      sortDir: 'desc',
      page: 1,
      pageSize: 8,
      timeRanges: [
        { label: '7 天', value: '7d' },
        { label: '14 天', value: '14d' },
        { label: '30 天', value: '30d' }
      ]
    }
  },
  computed: {
    busy() {
      if (typeof this.loading === 'boolean') return this.loading
      return !!(this.loading && this.loading.steps)
    },
    /* 归一化：后端路径只有 {hour, steps}，需要补算 distance / activeTime
       hour 在后端是数字，这里统一转成数字并额外给出补零后的 hourLabel（8 → '08'） */
    normalized() {
      return (this.stepHourlyData || []).map(d => {
        const steps = Number(d.steps) || 0
        const raw = d.hour
        const num = Number(raw)
        const valid = raw !== null && raw !== undefined && raw !== '' && !Number.isNaN(num)
        const hour = valid ? num : raw
        return {
          hour,
          hourLabel: String(hour ?? '').padStart(2, '0'),
          steps,
          distance: d.distance !== undefined ? Number(d.distance) : Math.round(steps * 0.0007 * 100) / 100,
          activeTime: d.activeTime !== undefined ? Number(d.activeTime) : Math.round(steps * 0.012)
        }
      })
    },
    /* 趋势图用全部 24 小时，明细表可按「仅活跃时段」过滤 */
    hourlyRows() {
      const dir = this.sortDir === 'asc' ? 1 : -1
      const list = this.sortKey === 'hour'
        ? this.normalized.slice().sort((a, b) => cmpHour(a, b) * dir)
        : this.normalized.slice().sort((a, b) => (a.steps - b.steps) * dir)
      if (this.showAll) return list
      return list.filter(r => r.steps > 0)
    },
    /* 趋势图始终按时间升序展示 24 小时 */
    trendRows() {
      return this.normalized.slice().sort(cmpHour)
    },
    peakHour() {
      return this.trendRows.reduce((m, r) => (!m || r.steps > m.steps ? r : m), null)
    },
    totalSteps() {
      return this.trendRows.reduce((s, r) => s + r.steps, 0)
    },
    totalDistance() {
      return Math.round(this.trendRows.reduce((s, r) => s + r.distance, 0) * 100) / 100
    },
    totalActiveTime() {
      return this.trendRows.reduce((s, r) => s + r.activeTime, 0)
    },
    /* ---- 卡片展示值：优先用父级统计，缺失时回退到小时数据汇总 ---- */
    cardSteps() {
      const v = Number(this.stepStats.todaySteps) || 0
      return v || this.totalSteps
    },
    cardDistance() {
      const v = Number(this.stepStats.walkingDistance) || 0
      return v || this.totalDistance
    },
    cardActiveTime() {
      const v = Number(this.stepStats.activeTime) || 0
      return v || this.totalActiveTime
    },
    cardFrequency() {
      const v = Number(this.stepStats.stepFrequency) || 0
      if (v) return v
      // 回退：总步数 / 活跃分钟
      const mins = this.totalActiveTime
      return mins ? Math.round((this.totalSteps / mins) * 10) / 10 : 0
    },
    activeHours() {
      return this.trendRows.filter(r => r.steps > 0).length
    },
    avgStepsPerHour() {
      return this.activeHours ? Math.round(this.totalSteps / this.activeHours) : 0
    },
    /* 每活跃小时的步数，比「步均」更能反映活动强度 */
    densityPerHour() {
      return this.activeHours ? Math.round(this.cardSteps / this.activeHours) : 0
    },
    stepsGoal() {
      // 固定参考基准 8000 步，保证进度条在所有设备下都有一致含义
      return 8000
    },
    stepsProgress() {
      if (!this.stepsGoal) return 0
      return Math.min(100, Math.round((this.cardSteps / this.stepsGoal) * 100))
    },
    freqLevel() {
      const f = this.cardFrequency
      if (f >= 22) return { text: '偏快', cls: 'alert' }
      if (f >= 12) return { text: '正常', cls: 'ok' }
      if (f > 0) return { text: '偏慢', cls: 'watch' }
      return { text: '无数据', cls: 'neutral' }
    },
    /* 饼图分档（与原逻辑保持一致） */
    pieBands() {
      const bands = { '低 (<100)': 0, '中低 (100-300)': 0, '中高 (300-600)': 0, '高 (≥600)': 0 }
      for (const d of this.trendRows) {
        const s = d.steps
        if (s < 100) bands['低 (<100)']++
        else if (s < 300) bands['中低 (100-300)']++
        else if (s < 600) bands['中高 (300-600)']++
        else bands['高 (≥600)']++
      }
      return bands
    },
    hasPieData() {
      return Object.values(this.pieBands).some(v => v > 0)
    },
    maxActive() {
      return this.trendRows.reduce((m, r) => Math.max(m, r.activeTime), 0) || 1
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.hourlyRows.length / this.pageSize))
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize
      return this.hourlyRows.slice(start, start + this.pageSize)
    },
    pageList() {
      const pages = []
      const total = this.totalPages
      let start = Math.max(1, this.page - 2)
      let end = Math.min(total, start + 4)
      start = Math.max(1, end - 4)
      for (let i = start; i <= end; i++) pages.push(i)
      return pages
    }
  },
  watch: {
    stepHourlyData() {
      this.$nextTick(() => this.render())
    },
    hourlyRows() {
      // 过滤/排序变化后重置分页
      if (this.page > this.totalPages) this.page = 1
    },
    showAll() { this.page = 1 },
    devices(val) {
      if (val && val.length && !this.selectedDevice) {
        this.selectedDevice = val[0].deviceId || ''
        this.$emit('device-change', this.selectedDevice, this.selectedRange)
      }
    }
  },
  mounted() {
    this.$nextTick(() => this.render())
    window.addEventListener('resize', this.handleResize)
    if (this.devices.length && !this.selectedDevice) {
      this.selectedDevice = this.devices[0].deviceId || ''
      this.$emit('device-change', this.selectedDevice, this.selectedRange)
    }
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    this.trendChart?.dispose()
    this.pieChart?.dispose()
  },
  methods: {
    handleResize() {
      this.trendChart?.resize()
      this.pieChart?.resize()
    },
    fmtNum(v) {
      const n = Number(v) || 0
      return n.toLocaleString('zh-CN')
    },
    sortIcon(key) {
      if (this.sortKey !== key) return 'fas fa-sort'
      return this.sortDir === 'asc' ? 'fas fa-sort-up' : 'fas fa-sort-down'
    },
    toggleSort(key) {
      if (this.sortKey === key) {
        this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc'
      } else {
        this.sortKey = key
        this.sortDir = key === 'hour' ? 'desc' : 'desc'
      }
      this.page = 1
    },
    activePct(r) {
      return Math.max(4, Math.round((r.activeTime / this.maxActive) * 100))
    },
    levelStyle(r) {
      const s = r.steps
      if (s >= 600) return { text: '高度活跃', cls: 'alert', solid: 'linear-gradient(90deg,#E07070,#D05A5A)' }
      if (s >= 300) return { text: '中高活跃', cls: 'watch', solid: 'linear-gradient(90deg,#F4A261,#E58B4A)' }
      if (s >= 100) return { text: '中度活跃', cls: 'ok', solid: 'linear-gradient(90deg,#5BA8D8,#4FACFE)' }
      if (s > 0) return { text: '轻度活跃', cls: 'neutral', solid: 'linear-gradient(90deg,#9B8FD4,#8E7FC9)' }
      return { text: '静止', cls: 'neutral', solid: 'linear-gradient(90deg,#CBD5E1,#B8C4D4)' }
    },
    selectRange(val) {
      this.selectedRange = val
      this.page = 1
      this.$emit('device-change', this.selectedDevice, this.selectedRange)
    },
    onDeviceChange() {
      this.page = 1
      this.$emit('device-change', this.selectedDevice, this.selectedRange)
    },
    render() {
      this.renderTrend()
      this.renderPie()
    },
    renderTrend() {
      if (!this.$refs.trendRef || !this.trendRows.length) return
      if (this.trendChart) this.trendChart.dispose()
      this.trendChart = echarts.init(this.$refs.trendRef)
      this.trendChart.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: 'rgba(91,168,216,0.35)',
          borderWidth: 1,
          textStyle: { color: '#334155', fontSize: 12 },
          axisPointer: { type: 'line', lineStyle: { color: 'rgba(91,168,216,0.45)', type: 'dashed' } }
        },
        grid: { left: 46, right: 20, top: 28, bottom: 34 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: this.trendRows.map(d => `${d.hourLabel}:00`),
          axisLine: { lineStyle: { color: 'rgba(91,168,216,0.35)' } },
          axisTick: { show: false },
          axisLabel: { color: '#5A6B7D', fontSize: 11 }
        },
        yAxis: {
          type: 'value',
          name: '步数',
          nameTextStyle: { color: '#94A3B8', fontSize: 11 },
          axisLine: { show: false },
          axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(91,168,216,0.12)' } },
          axisLabel: { color: '#5A6B7D', fontSize: 11 }
        },
        series: [{
          name: '步数',
          data: this.trendRows.map(d => d.steps),
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          showSymbol: false,
          lineStyle: { width: 3, color: '#F093FB' },
          itemStyle: { color: '#F5576C', borderColor: '#fff', borderWidth: 2 },
          emphasis: { focus: 'series' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(240,147,251,0.42)' },
              { offset: 1, color: 'rgba(245,87,108,0.02)' }
            ])
          }
        }]
      })
    },
    renderPie() {
      if (!this.$refs.pieRef) return
      if (this.pieChart) this.pieChart.dispose()
      this.pieChart = echarts.init(this.$refs.pieRef)

      const palette = ['#9B8FD4', '#4FACFE', '#F4A261', '#E07070']
      const names = Object.keys(this.pieBands)
      const rows = names.map((name, i) => ({
        name,
        value: this.pieBands[name],
        itemStyle: { color: palette[i] }
      }))
      const real = rows.filter(r => r.value > 0)
      const data = real.length ? real : [{ name: '暂无数据', value: 1, itemStyle: { color: '#E0E6ED' } }]

      this.pieChart.setOption({
        tooltip: {
          trigger: 'item',
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: 'rgba(91,168,216,0.35)',
          borderWidth: 1,
          textStyle: { color: '#334155', fontSize: 12 },
          valueFormatter: v => `${v} 小时`
        },
        legend: {
          bottom: 4,
          left: 'center',
          icon: 'circle',
          itemWidth: 8,
          itemHeight: 8,
          itemGap: 12,
          textStyle: { color: '#5A6B7D', fontSize: 11 }
        },
        series: [{
          name: '活跃程度',
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: false,
          padAngle: 2,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: {
            show: true,
            position: 'center',
            formatter: () => `{v|${this.activeHours}}\n{t|活跃小时}`,
            rich: {
              v: { fontSize: 24, fontWeight: 700, color: '#334155', lineHeight: 30 },
              t: { fontSize: 11, color: '#94A3B8' }
            }
          },
          labelLine: { show: false },
          emphasis: {
            scale: true,
            scaleSize: 6,
            label: { show: true }
          },
          data
        }]
      })
    }
  }
}
</script>

<style scoped>
.content-header { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.content-header h2 { margin: 0; font-size: 18px; color: var(--primary-dark); white-space: nowrap; }
.header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-left: auto; }

/* 工具栏内的下拉框（.ym-field / .ym-range / .ym-actions 基础样式由 modules.css 提供） */
.ym-field select { min-width: 150px; cursor: pointer; }

.ym-search-btn {
  background: rgba(91,168,216,0.08);
  border: 1px solid var(--surface-line, rgba(91,168,216,0.22));
  color: var(--primary-dark);
  border-radius: var(--border-radius-sm);
  padding: 7px 14px;
  font-size: 12.5px;
  cursor: pointer;
  transition: all .2s;
  white-space: nowrap;
}
.ym-search-btn:hover { background: rgba(91,168,216,0.16); }

/* 卡片头右侧信息 */
.ycm-meta { display: flex; align-items: center; gap: 14px; }
.ycm-peak {
  font-size: 12px;
  color: #D05A5A;
  background: rgba(224,112,112,0.10);
  border-radius: var(--radius-pill, 999px);
  padding: 3px 10px;
  white-space: nowrap;
}

.chart-container { width: 100%; height: 300px; }
.pie-container { height: 300px; }
/* 图表区域需要定位上下文，空状态才能绝对覆盖 */
.ym-card-body { position: relative; }
.chart-empty-abs { position: absolute; inset: 0; background: rgba(255,255,255,0.92); backdrop-filter: blur(2px); }

/* 表格横向滚动容器 */
.ym-table-wrap { overflow-x: auto; }
.ym-table-wrap .ym-table { min-width: 620px; }

.ym-charts-row {
  display: grid;
  grid-template-columns: minmax(320px, 380px) 1fr;
  gap: 18px;
  align-items: start;
}

.table-skeleton { padding: 18px; display: flex; flex-direction: column; gap: 12px; }

.td-time { display: flex; flex-direction: column; line-height: 1.35; }
.t-strong { font-weight: 600; color: var(--text-primary); }
.t-sub { font-size: 11.5px; color: var(--text-muted); }

@media (max-width: 1080px) {
  .ym-charts-row { grid-template-columns: 1fr; }
  .chart-container, .pie-container { height: 260px; }
}
</style>
