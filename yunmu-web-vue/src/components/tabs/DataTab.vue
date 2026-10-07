<template>
  <div class="tab-content data-tab">
    <div class="content-header">
      <h2><i class="fas fa-database"></i> 数据管理</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: busy }" @click="$emit('filter')">
          <i class="fas fa-filter"></i> 筛选
        </button>
        <button class="btn btn-success" :disabled="!historyData.length" @click="$emit('export-all')">
          <i class="fas fa-file-export"></i> 全部导出
        </button>
        <button class="btn btn-info" :disabled="!filteredData.length" @click="$emit('export-filtered')">
          <i class="fas fa-file-export"></i> 导出筛选
        </button>
      </div>
    </div>

    <!-- ===== 概览指标 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#5BA8D8,#7EC8E3);--accent-solid:linear-gradient(135deg,#5BA8D8,#3498DB);--accent-shadow:rgba(91,168,216,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-layer-group"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ historyData.length }}<em>条</em></div>
          <div class="ym-stat-label">历史数据总量</div>
          <div class="ym-stat-foot"><span>已筛选 <b class="pct">{{ filteredData.length }}</b> 条</span></div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#52B788,#38EF7D);--accent-solid:linear-gradient(135deg,#11998E,#38EF7D);--accent-shadow:rgba(56,239,125,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-paw"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ animalCount }}<em>只</em></div>
          <div class="ym-stat-label">涉及动物</div>
          <div class="ym-stat-foot"><span>{{ behaviorCount }} 种行为</span></div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#4FC3C0,#2C8C89);--accent-solid:linear-gradient(135deg,#4FC3C0,#2C8C89);--accent-shadow:rgba(79,195,192,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-shoe-prints"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ totalSteps.toLocaleString('zh-CN') }}<em>步</em></div>
          <div class="ym-stat-label">累计步数</div>
          <div class="ym-stat-foot"><span>均值 <b class="pct">{{ avgSteps }}</b> 步/条</span></div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: alertCount > 0 }" style="--accent:linear-gradient(90deg,#EB3349,#F45C43);--accent-solid:linear-gradient(135deg,#EB3349,#F45C43);--accent-shadow:rgba(235,51,73,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-triangle-exclamation"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ alertCount }}<em>条</em></div>
          <div class="ym-stat-label">异常记录</div>
          <div class="ym-stat-progress"><span :style="{ width: alertPct + '%', background: 'linear-gradient(90deg,#E07070,#C0392B)' }"></span></div>
          <div class="ym-stat-foot"><span>占比 <b class="pct">{{ alertPct }}%</b></span></div>
        </div>
      </div>
    </div>

    <!-- ===== 筛选工具栏 ===== -->
    <div class="ym-toolbar">
      <div class="ym-field">
        <label><i class="fas fa-calendar-day"></i> 起始日期</label>
        <div class="ym-date">
          <i class="fas fa-calendar-days"></i>
          <span class="yd-text" :class="{ empty: !dataFilter.startDate }">{{ dateText(dataFilter.startDate) }}</span>
          <input type="date" :value="dataFilter.startDate" @input="onDateInput('startDate', $event)" />
        </div>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-calendar-check"></i> 结束日期</label>
        <div class="ym-date">
          <i class="fas fa-calendar-days"></i>
          <span class="yd-text" :class="{ empty: !dataFilter.endDate }">{{ dateText(dataFilter.endDate) }}</span>
          <input type="date" :value="dataFilter.endDate" @input="onDateInput('endDate', $event)" />
        </div>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-tags"></i> 数据类型</label>
        <select v-model="dataFilter.dataType">
          <option value="">全部类型</option>
          <option v-for="t in dataTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-clock-rotate-left"></i> 快捷范围</label>
        <div class="ym-range">
          <button v-for="q in quickRanges" :key="q.days" :class="{ active: activeQuick === q.days }" @click="applyQuick(q.days)">{{ q.label }}</button>
        </div>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-magnifying-glass"></i> 搜索</label>
        <div class="ym-search">
          <i class="fas fa-search"></i>
          <input v-model.trim="keyword" type="text" placeholder="动物 ID / 行为 / 位置" />
          <button v-if="keyword" @click="keyword = ''"><i class="fas fa-times-circle"></i></button>
        </div>
      </div>
      <div class="ym-actions">
        <button class="btn btn-sm" @click="resetFilters"><i class="fas fa-rotate-left"></i> 重置</button>
      </div>
    </div>

    <!-- ===== 数据表 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-table-list"></i> 历史数据</h3>
        <span class="ym-card-sub">共 {{ filteredData.length }} 条 · 显示第 {{ page }} 页</span>
      </div>

      <div v-if="busy && !historyData.length" class="table-skeleton">
        <div v-for="n in 6" :key="n" class="ym-skeleton sk-row"></div>
      </div>

      <div v-else-if="!historyData.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-database"></i></div>
        <h5>暂无历史数据</h5>
        <p>请先在「系统概览」启动数据模拟器，或等待后端写入历史数据后点击「筛选」拉取。</p>
        <button class="btn btn-primary" @click="$emit('filter')"><i class="fas fa-filter"></i> 筛选数据</button>
      </div>

      <div v-else-if="!filteredData.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-filter-circle-xmark"></i></div>
        <h5>没有符合条件的数据</h5>
        <p>尝试放宽时间范围、切换数据类型，或清空搜索关键词。</p>
        <button class="btn btn-sm" @click="resetFilters"><i class="fas fa-rotate-left"></i> 重置筛选</button>
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
                <th>行为</th>
                <th class="sortable" :class="{ sorted: sortKey === 'steps' }" @click="toggleSort('steps')">
                  步数<i class="fas sort-ind" :class="sortInd('steps')"></i>
                </th>
                <th>步数强度</th>
                <th>位置</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in pagedData" :key="i" :class="{ 'is-alert': r.status === '异常' }">
                <td class="cell-time">
                  <div class="time-main">{{ r.time }}</div>
                  <div class="time-rel">{{ relative(r.time) }}</div>
                </td>
                <td><span class="animal-chip"><i class="fas fa-paw"></i>{{ r.animalId }}</span></td>
                <td><span class="ym-behavior" :style="behaviorStyle(r.behavior)">{{ r.behavior || '未知' }}</span></td>
                <td class="num">{{ Number(r.steps || 0).toLocaleString('zh-CN') }}</td>
                <td>
                  <div class="ym-meter-cell">
                    <div class="bar wide"><span :style="{ width: stepPct(r.steps) + '%', background: stepGradient }"></span></div>
                    <span class="lv" :class="stepLevel(r.steps).cls">{{ stepLevel(r.steps).text }}</span>
                  </div>
                </td>
                <td>
                  <span v-if="r.location" class="loc-cell" :title="r.location">
                    <i class="fas fa-location-dot"></i>
                    <span class="loc-text">{{ shortLoc(r.location) }}</span>
                  </span>
                  <span v-else class="text-muted">未上报</span>
                </td>
                <td>
                  <span class="ym-badge" :class="r.status === '正常' ? 'ok' : 'alert'">
                    <i class="fas fa-circle"></i>{{ r.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="ym-pager">
          <div class="ym-pager-info">第 <b>{{ page }}</b> / {{ totalPages }} 页 · 共 <b>{{ filteredData.length }}</b> 条</div>
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
const BEHAVIOR_COLORS = {
  采食: '#F093FB', 站立: '#4FACFE', 行走: '#43E97B', 躺卧: '#A18CD1',
  休息: '#38F9D7', 奔跑: '#F5576C', 饮水: '#4FC3C0'
}

export default {
  name: 'DataTab',
  props: {
    historyData: Array,
    dataFilter: Object,
    /* 父组件传入的是 loading.data（Boolean） */
    loading: { type: [Object, Boolean], default: () => ({}) }
  },
  data() {
    return {
      keyword: '',
      sortKey: 'time',
      sortDir: 'desc',
      page: 1,
      pageSize: 10,
      activeQuick: null,
      quickRanges: [
        { label: '今天', days: 1 },
        { label: '近7天', days: 7 },
        { label: '近30天', days: 30 }
      ],
      dataTypes: [
        { label: '行为数据', value: 'behavior' },
        { label: '定位数据', value: 'location' },
        { label: '姿态数据', value: 'posture' },
        { label: '步数数据', value: 'step' }
      ]
    }
  },
  computed: {
    /* 父组件传入的是 loading.data（Boolean），兼容对象写法 */
    busy() {
      if (typeof this.loading === 'boolean') return this.loading
      return !!(this.loading && this.loading.data)
    },
    records() { return this.historyData || [] },
    /* 前端筛选：日期 + 关键词；数据类型由后端筛选按钮处理，这里同时兼容本地过滤 */
    filteredData() {
      let list = this.records.slice()
      const { startDate, endDate } = this.dataFilter || {}
      if (startDate) {
        const s = new Date(startDate.replace(/-/g, '/')).getTime()
        list = list.filter(r => {
          const t = new Date(String(r.time).replace(/-/g, '/')).getTime()
          return !isNaN(t) && t >= s
        })
      }
      if (endDate) {
        const e = new Date(endDate.replace(/-/g, '/')).getTime() + 86400000 - 1
        list = list.filter(r => {
          const t = new Date(String(r.time).replace(/-/g, '/')).getTime()
          return !isNaN(t) && t <= e
        })
      }
      const kw = this.keyword.toLowerCase()
      if (kw) {
        list = list.filter(r =>
          String(r.animalId || '').toLowerCase().includes(kw) ||
          String(r.behavior || '').toLowerCase().includes(kw) ||
          String(r.location || '').toLowerCase().includes(kw)
        )
      }
      const k = this.sortKey
      const dir = this.sortDir === 'asc' ? 1 : -1
      list.sort((a, b) => {
        if (k === 'steps') return ((Number(a.steps) || 0) - (Number(b.steps) || 0)) * dir
        return String(a[k] ?? '').localeCompare(String(b[k] ?? '')) * dir
      })
      return list
    },
    animalCount() { return new Set(this.records.map(r => r.animalId).filter(Boolean)).size },
    behaviorCount() { return new Set(this.records.map(r => r.behavior).filter(Boolean)).size },
    totalSteps() { return this.records.reduce((s, r) => s + (Number(r.steps) || 0), 0) },
    avgSteps() {
      if (!this.records.length) return 0
      return Math.round(this.totalSteps / this.records.length)
    },
    alertCount() { return this.records.filter(r => r.status === '异常').length },
    alertPct() { return this.records.length ? Math.round(this.alertCount / this.records.length * 100) : 0 },
    maxSteps() {
      const v = this.records.map(r => Number(r.steps) || 0)
      return v.length ? Math.max(...v) : 0
    },
    stepGradient() { return 'linear-gradient(90deg,#4FC3C0,#2C8C89)' },
    totalPages() { return Math.max(1, Math.ceil(this.filteredData.length / this.pageSize)) },
    pagedData() {
      const start = (this.page - 1) * this.pageSize
      return this.filteredData.slice(start, start + this.pageSize)
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
    keyword() { this.page = 1 },
    filteredData() { if (this.page > this.totalPages) this.page = this.totalPages }
  },
  methods: {
    /* 把 YYYY-MM-DD 显示为「年/月/日」中文占位，替代原生控件的 yyyy/mm/日 */
    dateText(v) {
      if (!v) return '年/月/日'
      const [y, m, d] = String(v).split('-')
      if (!y || !m || !d) return '年/月/日'
      return `${y}年${Number(m)}月${Number(d)}日`
    },
    onDateInput(key, e) {
      const val = e.target.value || ''
      if (this.dataFilter) this.dataFilter[key] = val
      this.activeQuick = null
      this.page = 1
    },
    applyQuick(days) {
      this.activeQuick = days
      const end = new Date()
      const start = new Date(end.getTime() - (days - 1) * 86400000)
      const p = n => String(n).padStart(2, '0')
      const fmt = d => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
      this.dataFilter.startDate = fmt(start)
      this.dataFilter.endDate = fmt(end)
      this.page = 1
    },
    resetFilters() {
      this.keyword = ''
      this.activeQuick = null
      this.sortKey = 'time'
      this.sortDir = 'desc'
      this.page = 1
      if (this.dataFilter) {
        this.dataFilter.startDate = ''
        this.dataFilter.endDate = ''
        this.dataFilter.dataType = ''
      }
    },
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
      if (diff < 0) return '刚刚'
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
      return Math.floor(diff / 86400000) + ' 天前'
    },
    behaviorStyle(name) {
      const c = BEHAVIOR_COLORS[name] || '#5BA8D8'
      return { '--bh': c, '--bh-soft': this.hexAlpha(c, 0.14) }
    },
    hexAlpha(hex, a) {
      const h = hex.replace('#', '')
      return `rgba(${parseInt(h.slice(0, 2), 16)},${parseInt(h.slice(2, 4), 16)},${parseInt(h.slice(4, 6), 16)},${a})`
    },
    stepPct(v) {
      if (!this.maxSteps) return 0
      return Math.max(4, Math.min(100, ((Number(v) || 0) / this.maxSteps) * 100))
    },
    stepLevel(v) {
      const n = Number(v) || 0
      if (n >= 250) return { text: '高', cls: 'alert' }
      if (n >= 80) return { text: '中', cls: 'watch' }
      return { text: '低', cls: 'ok' }
    },
    /* 位置列：保留到分，完整值放 title 悬浮查看 */
    shortLoc(loc) {
      const s = String(loc)
      if (s.length <= 22) return s
      return s.slice(0, 21) + '…'
    }
  }
}
</script>

<style scoped>
/* ---------- 自定义日期框：显示中文占位，点击唤起原生选择器 ---------- */
.ym-date {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  min-width: 148px;
  background: var(--surface-soft);
  border: 1.5px solid var(--surface-line);
  border-radius: var(--border-radius-sm);
  transition: var(--transition);
  cursor: pointer;
}
.ym-date:hover { border-color: var(--primary-color); }
.ym-date:focus-within {
  border-color: var(--primary-color);
  background: #fff;
  box-shadow: var(--ring-primary);
}
.ym-date > i { color: var(--primary-color); font-size: 12.5px; flex-shrink: 0; }
.yd-text {
  font-size: 13px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.yd-text.empty { color: var(--text-muted); }
/* 原生控件透明覆盖整块区域，仅用于唤起日期选择面板 */
.ym-date input[type="date"] {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  border: none;
  padding: 0;
  margin: 0;
  cursor: pointer;
  background: transparent;
  -webkit-appearance: none;
  appearance: none;
}
/* 让原生日历指示器也铺满，保证任意位置可点 */
.ym-date input[type="date"]::-webkit-calendar-picker-indicator {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
  opacity: 0;
}

.table-scroll { overflow-x: auto; }
.table-scroll .ym-table { min-width: 980px; }

.cell-time .time-main { font-size: 13px; color: var(--text-secondary); font-variant-numeric: tabular-nums; white-space: nowrap; }
.cell-time .time-rel { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.animal-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: var(--font-mono);
  font-size: 12.5px; font-weight: 700;
  color: var(--primary-dark);
}
.animal-chip i { font-size: 11px; color: var(--primary-color); }

.ym-meter-cell .bar.wide { max-width: 96px; }
.ym-meter-cell .lv {
  font-size: 11px; font-weight: 700; white-space: nowrap;
  padding: 1px 8px; border-radius: var(--radius-pill);
}
.ym-meter-cell .lv.ok { background: var(--scale-ok-soft); color: var(--success-dark); }
.ym-meter-cell .lv.watch { background: var(--scale-watch-soft); color: #A9622E; }
.ym-meter-cell .lv.alert { background: var(--scale-alert-soft); color: var(--danger-dark); }

.loc-cell {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--text-secondary);
  max-width: 190px;
}
.loc-cell i { color: var(--primary-color); flex-shrink: 0; }
.loc-text {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.text-muted { color: var(--text-muted); }
.table-skeleton { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.sk-row { height: 20px; }
.sk-row:nth-child(odd) { width: 92%; }

.pct { color: var(--text-primary); font-weight: 700; }

@media (max-width: 768px) {
  .ym-toolbar .ym-field { flex: 1 1 100%; }
  .ym-toolbar .ym-actions { margin-left: 0; width: 100%; }
}
</style>
