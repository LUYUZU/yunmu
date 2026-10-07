<template>
  <div class="tab-content animals-tab">
    <div class="content-header">
      <h2><i class="fas fa-paw"></i> 动物监测</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: busy }" @click="$emit('load')">
          <i class="fas fa-download"></i> 加载数据
        </button>
        <button class="btn btn-success" :disabled="!filteredRecords.length" @click="$emit('export')">
          <i class="fas fa-file-export"></i> 导出{{ statusFilter !== 'all' ? '筛选' : '' }}
        </button>
      </div>
    </div>

    <!-- ===== 概览指标 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#5BA8D8,#7EC8E3);--accent-solid:linear-gradient(135deg,#5BA8D8,#3498DB);--accent-shadow:rgba(91,168,216,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-clipboard-list"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ records.length }}<em>条</em></div>
          <div class="ym-stat-label">监测记录总数</div>
          <div class="ym-stat-foot"><span>{{ scopeLabel }}</span></div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#52B788,#38EF7D);--accent-solid:linear-gradient(135deg,#11998E,#38EF7D);--accent-shadow:rgba(56,239,125,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-circle-check"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ normalCount }}<em>条</em></div>
          <div class="ym-stat-label">正常记录</div>
          <div class="ym-stat-progress"><span :style="{ width: normalPct + '%', background: 'linear-gradient(90deg,#52B788,#40916C)' }"></span></div>
          <div class="ym-stat-foot"><span>占比 <b class="pct">{{ normalPct }}%</b></span></div>
        </div>
      </div>
      <div class="ym-stat" :class="{ alert: alertCount > 0 }" style="--accent:linear-gradient(90deg,#EB3349,#F45C43);--accent-solid:linear-gradient(135deg,#EB3349,#F45C43);--accent-shadow:rgba(235,51,73,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-triangle-exclamation"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ alertCount }}<em>条</em></div>
          <div class="ym-stat-label">异常记录</div>
          <div class="ym-stat-progress"><span :style="{ width: alertPct + '%', background: 'linear-gradient(90deg,#E07070,#C0392B)' }"></span></div>
          <div class="ym-stat-foot">
            <span v-if="alertCount" class="ym-trend up"><i class="fas fa-circle-exclamation"></i> 建议核查</span>
            <span v-else class="ym-trend down"><i class="fas fa-circle-check"></i> 全部正常</span>
          </div>
        </div>
      </div>
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#4FACFE,#00F2FE);--accent-solid:linear-gradient(135deg,#4FACFE,#00F2FE);--accent-shadow:rgba(79,172,254,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-paw"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ involvedAnimals }}<em>只</em></div>
          <div class="ym-stat-label">涉及动物</div>
          <div class="ym-stat-foot"><span>{{ behaviorKinds }} 种行为</span></div>
        </div>
      </div>
    </div>

    <!-- ===== 筛选工具栏 ===== -->
    <div class="ym-toolbar">
      <div class="ym-field">
        <label><i class="fas fa-microchip"></i> 动物 ID</label>
        <select v-model="animalFilter.animalId" @change="$emit('load')">
          <option value="">全部动物</option>
          <option v-for="a in animals" :key="a.id" :value="a.id">{{ a.id }}</option>
        </select>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-filter"></i> 状态</label>
        <div class="ym-range">
          <button v-for="f in statusFilters" :key="f.value" :class="{ active: statusFilter === f.value }" @click="statusFilter = f.value">
            {{ f.label }}<em>{{ countStatus(f.value) }}</em>
          </button>
        </div>
      </div>
      <div class="ym-field">
        <label><i class="fas fa-magnifying-glass"></i> 搜索</label>
        <div class="ym-search">
          <i class="fas fa-search"></i>
          <input v-model.trim="keyword" type="text" placeholder="动物 ID / 行为" />
          <button v-if="keyword" @click="keyword = ''"><i class="fas fa-times-circle"></i></button>
        </div>
      </div>
      <div class="ym-actions">
        <button class="btn btn-sm" @click="resetFilters"><i class="fas fa-rotate-left"></i> 重置</button>
      </div>
    </div>

    <!-- ===== 记录表 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-table-list"></i> 监测记录</h3>
        <span class="ym-card-sub">
          共 {{ filteredRecords.length }} 条{{ scopeLabel ? ' · ' + scopeLabel : '' }}
        </span>
      </div>

      <div v-if="busy && !records.length" class="table-skeleton">
        <div v-for="n in 6" :key="n" class="ym-skeleton sk-row"></div>
      </div>

      <div v-else-if="!records.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-clipboard-list"></i></div>
        <h5>暂无监测记录</h5>
        <p>请先在「系统概览」启动数据模拟器，或等待真实项圈上报后点击「加载数据」。</p>
        <button class="btn btn-primary" @click="$emit('load')"><i class="fas fa-download"></i> 加载数据</button>
      </div>

      <div v-else-if="!filteredRecords.length" class="ym-empty">
        <div class="ym-empty-icon"><i class="fas fa-filter-circle-xmark"></i></div>
        <h5>没有符合条件的记录</h5>
        <p>尝试调整筛选条件或清空搜索关键词。</p>
        <button class="btn btn-sm" @click="resetFilters"><i class="fas fa-rotate-left"></i> 重置筛选</button>
      </div>

      <template v-else>
        <div class="table-scroll">
          <table class="ym-table">
            <thead>
              <tr>
                <th v-for="col in columns" :key="col.key"
                    :class="['sortable', { sorted: sortKey === col.key }]"
                    @click="toggleSort(col.key)">
                  {{ col.label }}
                  <i class="fas sort-ind"
                     :class="sortKey === col.key ? (sortDir === 'asc' ? 'fa-arrow-up-short-wide' : 'fa-arrow-down-wide-short') : 'fa-sort'"></i>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in pagedRecords" :key="i" :class="{ 'is-alert': r.status === '异常' }">
                <td class="cell-time">
                  <div class="time-main">{{ r.time }}</div>
                  <div class="time-rel">{{ relative(r.time) }}</div>
                </td>
                <td>
                  <span class="animal-chip"><i class="fas fa-paw"></i>{{ r.animalId }}</span>
                </td>
                <td>
                  <span class="ym-behavior" :style="behaviorStyle(r.behavior)">{{ r.behavior || '未知' }}</span>
                </td>
                <td>
                  <div class="ym-meter-cell">
                    <span class="val" :style="{ color: tempColor(r.temperature) }">{{ fmtTemp(r.temperature) }}</span>
                    <div class="bar"><span :style="{ width: tempPct(r.temperature) + '%', background: tempColor(r.temperature) }"></span></div>
                  </div>
                </td>
                <td>
                  <div class="ym-meter-cell">
                    <span class="val" :style="{ color: hrColor(r.heartRate) }">{{ r.heartRate || '--' }}</span>
                    <div class="bar"><span :style="{ width: hrPct(r.heartRate) + '%', background: hrColor(r.heartRate) }"></span></div>
                  </div>
                </td>
                <td v-if="hasConfidence">
                  <div class="ym-conf">
                    <span class="ym-conf-ring" :style="confStyle(r.confidence)"></span>
                    <span class="ym-conf-num">{{ r.confidence || '--' }}%</span>
                  </div>
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
          <div class="ym-pager-info">
            第 <b>{{ page }}</b> / {{ totalPages }} 页 · 共 <b>{{ filteredRecords.length }}</b> 条
          </div>
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
  name: 'AnimalsTab',
  props: {
    animals: Array,
    animalFilter: Object,
    animalRecords: Array,
    /* 父组件传入的是 loading.animals（Boolean） */
    loading: { type: [Object, Boolean], default: () => ({}) }
  },
  data() {
    return {
      keyword: '',
      statusFilter: 'all',
      sortKey: 'time',
      sortDir: 'desc',
      page: 1,
      pageSize: 10,
      statusFilters: [
        { label: '全部', value: 'all' },
        { label: '正常', value: 'normal' },
        { label: '异常', value: 'alert' }
      ],
      columns: [
        { key: 'time', label: '时间' },
        { key: 'animalId', label: '动物 ID' },
        { key: 'behavior', label: '行为' },
        { key: 'temperature', label: '体温' },
        { key: 'heartRate', label: '心率' },
        { key: 'confidence', label: '置信度' },
        { key: 'status', label: '状态' }
      ]
    }
  },
  computed: {
    /* 父组件传入的是 loading.animals（Boolean），兼容对象写法 */
    busy() {
      if (typeof this.loading === 'boolean') return this.loading
      return !!(this.loading && this.loading.animals)
    },
    records() { return this.animalRecords || [] },
    hasConfidence() { return this.records.some(r => r.confidence !== undefined && r.confidence !== null) },
    scopeLabel() {
      const ids = new Set(this.records.map(r => r.animalId).filter(Boolean))
      return ids.size ? `涉及 ${ids.size} 只动物` : ''
    },
    involvedAnimals() { return new Set(this.records.map(r => r.animalId).filter(Boolean)).size },
    behaviorKinds() { return new Set(this.records.map(r => r.behavior).filter(Boolean)).size },
    normalCount() { return this.records.filter(r => r.status === '正常').length },
    alertCount() { return this.records.filter(r => r.status === '异常').length },
    normalPct() { return this.records.length ? Math.round(this.normalCount / this.records.length * 100) : 0 },
    alertPct() { return this.records.length ? Math.round(this.alertCount / this.records.length * 100) : 0 },
    filteredRecords() {
      let list = this.records.slice()
      const kw = this.keyword.toLowerCase()
      if (kw) {
        list = list.filter(r =>
          String(r.animalId || '').toLowerCase().includes(kw) ||
          String(r.behavior || '').toLowerCase().includes(kw)
        )
      }
      if (this.statusFilter === 'normal') list = list.filter(r => r.status === '正常')
      if (this.statusFilter === 'alert') list = list.filter(r => r.status === '异常')
      const k = this.sortKey
      const dir = this.sortDir === 'asc' ? 1 : -1
      list.sort((a, b) => {
        if (k === 'temperature' || k === 'heartRate' || k === 'confidence') {
          return ((parseFloat(a[k]) || 0) - (parseFloat(b[k]) || 0)) * dir
        }
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
      const cur = this.page
      let start = Math.max(1, cur - 2)
      const end = Math.min(total, start + 4)
      start = Math.max(1, end - 4)
      const out = []
      for (let i = start; i <= end; i++) out.push(i)
      return out
    }
  },
  watch: {
    keyword() { this.page = 1 },
    statusFilter() { this.page = 1 },
    filteredRecords() { if (this.page > this.totalPages) this.page = this.totalPages }
  },
  methods: {
    resetFilters() {
      this.keyword = ''
      this.statusFilter = 'all'
      this.sortKey = 'time'
      this.sortDir = 'desc'
      this.page = 1
      if (this.animalFilter) this.animalFilter.animalId = ''
    },
    countStatus(v) {
      if (v === 'all') return this.records.length
      return this.records.filter(r => (v === 'normal' ? r.status === '正常' : r.status === '异常')).length
    },
    toggleSort(key) {
      if (this.sortKey === key) {
        this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc'
      } else {
        this.sortKey = key
        this.sortDir = key === 'time' ? 'desc' : 'asc'
      }
    },
    behaviorStyle(name) {
      const c = BEHAVIOR_COLORS[name] || '#5BA8D8'
      return { '--bh': c, '--bh-soft': this.hexAlpha(c, 0.14) }
    },
    hexAlpha(hex, a) {
      const h = hex.replace('#', '')
      const r = parseInt(h.slice(0, 2), 16)
      const g = parseInt(h.slice(2, 4), 16)
      const b = parseInt(h.slice(4, 6), 16)
      return `rgba(${r},${g},${b},${a})`
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
    fmtTemp(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return '暂无'
      return n.toFixed(1) + '°'
    },
    tempColor(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return 'var(--text-muted)'
      if (n > 39.5 || n < 37.5) return 'var(--scale-alert)'
      if (n > 39) return 'var(--scale-watch)'
      return 'var(--success-dark)'
    },
    tempPct(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return 0
      return Math.max(6, Math.min(100, (n / 42) * 100))
    },
    hrColor(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return 'var(--text-muted)'
      if (n > 100 || n < 45) return 'var(--scale-alert)'
      if (n > 90 || n < 55) return 'var(--scale-watch)'
      return 'var(--success-dark)'
    },
    hrPct(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return 0
      return Math.max(6, Math.min(100, (n / 130) * 100))
    },
    confStyle(v) {
      const n = Number(v) || 0
      const ring = n >= 90 ? 'var(--viz-green)' : n >= 75 ? 'var(--viz-blue)' : 'var(--viz-amber)'
      return { '--pct': Math.max(0, Math.min(100, n)), '--ring': ring }
    }
  }
}
</script>

<style scoped>
.table-scroll { overflow-x: auto; }
.table-scroll .ym-table { min-width: 860px; }

.cell-time .time-main { font-size: 13px; color: var(--text-secondary); font-variant-numeric: tabular-nums; white-space: nowrap; }
.cell-time .time-rel { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.animal-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: var(--font-mono);
  font-size: 12.5px; font-weight: 700;
  color: var(--primary-dark);
}
.animal-chip i { font-size: 11px; color: var(--primary-color); }

.table-skeleton { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.sk-row { height: 20px; }
.sk-row:nth-child(odd) { width: 92%; }

.pct { color: var(--text-primary); font-weight: 700; }

@media (max-width: 768px) {
  .ym-toolbar .ym-actions { margin-left: 0; width: 100%; }
  .ym-toolbar .ym-field { flex: 1 1 100%; }
}
</style>
