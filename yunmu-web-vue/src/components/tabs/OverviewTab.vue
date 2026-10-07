<template>
  <div class="tab-content overview-tab">
    <div class="content-header">
      <h2><i class="fas fa-tachometer-alt"></i> 系统概览</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: loading.overview }" @click="$emit('refresh')">
          <i class="fas fa-plug"></i> 检查后端连接
        </button>
        <button class="btn" :class="[simulatorRunning ? 'btn-danger' : 'btn-success', { loading: loading.overview }]" @click="$emit('toggle-simulator')" :title="simulatorRunning ? '停止后端设备数据模拟器' : '由后端按设备协议生成数据，注入真实链路'">
          <i :class="simulatorRunning ? 'fas fa-ban' : 'fas fa-bolt'"></i> {{ simulatorRunning ? '停止数据模拟器' : '启动数据模拟器' }}
        </button>
        <button class="btn btn-info" :class="{ loading: loading.overview }" @click="$emit('refresh-all')">
          <i class="fas fa-sync-alt"></i> 刷新
        </button>
      </div>
    </div>

    <!-- ===== 核心指标 ===== -->
    <div class="stats-cards">
      <div class="ym-stat" style="--accent:linear-gradient(90deg,#667eea,#764ba2);--accent-solid:linear-gradient(135deg,#667eea,#764ba2);--accent-shadow:rgba(118,75,162,0.28)">
        <div class="ym-stat-icon"><i class="fas fa-paw"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ stats.totalAnimals }}<em>只</em></div>
          <div class="ym-stat-label">监测动物总数</div>
          <div class="ym-stat-foot">
            <span v-if="cowCount || sheepCount">
              <i class="fas fa-cow"></i> 牛 {{ cowCount }} · <i class="fas fa-paw"></i> 羊 {{ sheepCount }}
            </span>
            <span v-else>等待数据接入</span>
          </div>
        </div>
      </div>

      <div class="ym-stat" style="--accent:linear-gradient(90deg,#52B788,#38EF7D);--accent-solid:linear-gradient(135deg,#11998E,#38EF7D);--accent-shadow:rgba(56,239,125,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-shield-heart"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ stats.normal }}<em>只</em></div>
          <div class="ym-stat-label">正常状态</div>
          <div class="ym-stat-progress"><span :style="{ width: normalPct + '%', background: 'linear-gradient(90deg,#52B788,#40916C)' }"></span></div>
          <div class="ym-stat-foot"><span>占总体 <b class="pct">{{ normalPct }}%</b></span></div>
        </div>
      </div>

      <div class="ym-stat" :class="{ alert: stats.alert > 0 }" style="--accent:linear-gradient(90deg,#EB3349,#F45C43);--accent-solid:linear-gradient(135deg,#EB3349,#F45C43);--accent-shadow:rgba(235,51,73,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-triangle-exclamation"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ stats.alert }}<em>只</em></div>
          <div class="ym-stat-label">异常预警</div>
          <div class="ym-stat-progress"><span :style="{ width: alertPct + '%', background: 'linear-gradient(90deg,#E07070,#C0392B)' }"></span></div>
          <div class="ym-stat-foot">
            <span v-if="stats.alert > 0" class="ym-trend up"><i class="fas fa-circle-exclamation"></i> 需及时处理</span>
            <span v-else class="ym-trend down"><i class="fas fa-circle-check"></i> 暂无异常</span>
          </div>
        </div>
      </div>

      <div class="ym-stat" style="--accent:linear-gradient(90deg,#4FACFE,#00F2FE);--accent-solid:linear-gradient(135deg,#4FACFE,#00F2FE);--accent-shadow:rgba(79,172,254,0.26)">
        <div class="ym-stat-icon"><i class="fas fa-database"></i></div>
        <div class="ym-stat-body">
          <div class="ym-stat-value">{{ stats.dataReceived }}</div>
          <div class="ym-stat-label">数据接收数</div>
          <div class="ym-stat-foot">
            <span><i class="fas fa-stream"></i> 日志 {{ dataStreamLogs.length }} 条</span>
            <span v-if="simulatorRunning" class="live-dot">模拟器运行中 · {{ simulatorDeviceCount }} 个项圈 · 每 {{ simulatorIntervalSec }}s</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 实时数据流 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-terminal"></i> 实时数据流</h3>
        <div class="stream-tools">
          <label class="stream-toggle">
            <input type="checkbox" v-model="autoScroll" />
            <span>自动滚动</span>
          </label>
          <span class="stream-count">{{ filteredLogs.length }} 条</span>
          <button v-if="cleared" class="mini-icon-btn" title="恢复显示" @click="cleared = false"><i class="fas fa-rotate-left"></i></button>
          <button v-else class="mini-icon-btn" title="清空视图" @click="cleared = true"><i class="fas fa-broom"></i></button>
        </div>
      </div>
      <div class="stream-filters">
        <button v-for="s in sourceFilters" :key="s.value" :class="{ active: logFilter === s.value }" @click="logFilter = s.value">
          {{ s.label }}<em>{{ countSource(s.value) }}</em>
        </button>
      </div>
      <div class="stream-console" ref="consoleRef">
        <div v-if="!filteredLogs.length" class="ym-empty ym-empty-inline">
          <div class="ym-empty-icon"><i class="fas fa-stream"></i></div>
          <h5>暂无数据流</h5>
          <p>点击右上角「启动数据模拟器」，或等待真实项圈设备上报后刷新。</p>
        </div>
        <div v-for="log in filteredLogs" :key="log.id" class="console-line">
          <span class="cl-time">{{ log.time }}</span>
          <span class="cl-badge" :class="sourceClass(log.source)">{{ log.source }}</span>
          <span class="cl-msg">{{ log.message }}</span>
        </div>
      </div>
    </div>

    <!-- ===== 实时监测 ===== -->
    <div class="ym-card">
      <div class="ym-card-head">
        <h3><i class="fas fa-heart-pulse"></i> 实时监测</h3>
        <div class="monitor-tools">
          <div class="ym-search">
            <i class="fas fa-search"></i>
            <input v-model.trim="animalKeyword" type="text" placeholder="搜索动物 ID / 行为" />
            <button v-if="animalKeyword" @click="animalKeyword = ''"><i class="fas fa-times-circle"></i></button>
          </div>
          <div class="ym-range">
            <button v-for="f in monitorFilters" :key="f.value" :class="{ active: monitorFilter === f.value }" @click="monitorFilter = f.value">
              {{ f.label }}<em>{{ countMonitor(f.value) }}</em>
            </button>
          </div>
          <div class="sort-select">
            <i class="fas fa-arrow-down-wide-short"></i>
            <select v-model="sortBy">
              <option value="id">按编号</option>
              <option value="health">按健康分</option>
              <option value="temp">按体温</option>
              <option value="status">异常优先</option>
            </select>
          </div>
        </div>
      </div>

      <div class="ym-card-body">
        <div v-if="!animals.length" class="ym-empty">
          <div class="ym-empty-icon"><i class="fas fa-heart-pulse"></i></div>
          <h5>暂无监测数据</h5>
          <p>请先启动数据模拟器，或确认真实项圈设备已在后端接入数据流后刷新本页。</p>
        </div>

        <div v-else-if="!visibleAnimals.length" class="ym-empty">
          <div class="ym-empty-icon"><i class="fas fa-filter-circle-xmark"></i></div>
          <h5>没有匹配的动物</h5>
          <p>尝试调整搜索关键词或状态筛选条件。</p>
        </div>

        <div v-else class="animal-cards-grid">
          <article
            v-for="animal in visibleAnimals"
            :key="animal.id"
            class="animal-card-v2"
            :class="{ alert: animal.status === 'alert' }">
            <header class="ac-head">
              <div class="ac-ident">
                <span class="ac-avatar" :class="speciesKey(animal.type)">
                  <i :class="speciesKey(animal.type) === 'cow' ? 'fas fa-cow' : 'fas fa-paw'"></i>
                </span>
                <div class="ac-ident-text">
                  <div class="ac-id">{{ animal.id }}</div>
                  <div class="ac-name">
                    <span class="ac-species">{{ speciesText(animal.type) }}</span>
                  </div>
                </div>
              </div>
              <span class="ym-badge" :class="animal.status === 'normal' ? 'ok' : 'alert'">
                <i class="fas fa-circle"></i>{{ animal.status === 'normal' ? '正常' : '异常' }}
              </span>
            </header>

            <div class="ac-vitals">
              <div class="ac-vital">
                <div class="ac-vital-head">
                  <span><i class="fas fa-temperature-half"></i> 体温</span>
                  <b :style="{ color: tempColor(animal.temperature) }">{{ fmtTemp(animal.temperature) }}</b>
                </div>
                <div class="ac-vital-bar"><span :style="{ width: tempPct(animal.temperature) + '%', background: tempColor(animal.temperature) }"></span></div>
              </div>
              <div class="ac-vital">
                <div class="ac-vital-head">
                  <span><i class="fas fa-heart"></i> 心率</span>
                  <b :style="{ color: hrColor(animal.heartRate) }">{{ animal.heartRate || '--' }}</b>
                </div>
                <div class="ac-vital-bar"><span :style="{ width: hrPct(animal.heartRate) + '%', background: hrColor(animal.heartRate) }"></span></div>
              </div>
            </div>

            <div class="ac-stats">
              <div class="ac-stat">
                <span class="ac-stat-label"><i class="fas fa-shoe-prints"></i> 步数</span>
                <span class="ac-stat-value">{{ fmtNum(animal.steps) }}</span>
              </div>
              <div class="ac-stat">
                <span class="ac-stat-label"><i class="fas fa-person-walking"></i> 行为</span>
                <span class="ac-stat-value">{{ animal.behavior || '未知' }}</span>
              </div>
            </div>

            <footer class="ac-foot">
              <div class="ac-health">
                <span class="ac-health-label">健康分</span>
                <div class="ac-health-bar"><span :style="{ width: healthPct(animal.healthScore) + '%', background: healthColor(animal.healthScore) }"></span></div>
                <b :style="{ color: healthColor(animal.healthScore) }">{{ healthText(animal.healthScore) }}</b>
              </div>
            </footer>
          </article>
        </div>

        <div v-if="visibleAnimals.length < animals.length && animals.length" class="list-foot">
          <span>已展示 <b>{{ visibleAnimals.length }}</b> / {{ animals.length }} 只</span>
          <button v-if="!showAll" class="link-btn" @click="showAll = true">展开全部 <i class="fas fa-chevron-down"></i></button>
          <button v-else class="link-btn" @click="showAll = false">收起 <i class="fas fa-chevron-up"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'OverviewTab',
  props: {
    stats: Object,
    animals: Array,
    dataStreamLogs: Array,
    /* 父组件可能传入 loading 对象或 loading.overview（Boolean） */
    loading: { type: [Object, Boolean], default: () => ({}) },
    simulatorRunning: { type: Boolean, default: false },
    simulatorInfo: { type: Object, default: () => ({ intervalMs: 0, anomalyRate: 0, devices: [] }) }
  },
  data() {
    return {
      logFilter: 'ALL',
      cleared: false,      // 仅隐藏当前视图内的日志，不修改父组件数据
      autoScroll: true,
      animalKeyword: '',
      monitorFilter: 'all',
      sortBy: 'id',
      showAll: false,
      monitorFilters: [
        { label: '全部', value: 'all' },
        { label: '正常', value: 'normal' },
        { label: '异常', value: 'alert' }
      ]
    }
  },
  computed: {
    sourceFilters() {
      const set = new Set(['ALL'])
      ;(this.dataStreamLogs || []).forEach(l => set.add(l.source || 'OTHER'))
      return Array.from(set).map(v => ({ value: v, label: v === 'ALL' ? '全部' : v }))
    },
    /* ---- 数据模拟器状态（来自后端 /api/simulator/status） ---- */
    simulatorDeviceCount() {
      const d = this.simulatorInfo && this.simulatorInfo.devices
      return Array.isArray(d) ? d.length : 0
    },
    simulatorIntervalSec() {
      const ms = this.simulatorInfo && this.simulatorInfo.intervalMs
      return ms ? Math.round(ms / 1000) : 0
    },
    filteredLogs() {
      const base = this.cleared ? [] : (this.dataStreamLogs || [])
      if (this.logFilter === 'ALL') return base
      return base.filter(l => (l.source || 'OTHER') === this.logFilter)
    },
    cowCount() { return (this.animals || []).filter(a => a.type === 'cow').length },
    sheepCount() { return (this.animals || []).filter(a => a.type === 'sheep').length },
    normalPct() {
      if (!this.stats.totalAnimals) return 0
      return Math.round(this.stats.normal / this.stats.totalAnimals * 100)
    },
    alertPct() {
      if (!this.stats.totalAnimals) return 0
      return Math.round(this.stats.alert / this.stats.totalAnimals * 100)
    },
    visibleAnimals() {
      let list = (this.animals || []).slice()
      const kw = this.animalKeyword.toLowerCase()
      if (kw) {
        list = list.filter(a =>
          String(a.id || '').toLowerCase().includes(kw) ||
          String(a.behavior || '').toLowerCase().includes(kw) ||
          String(a.posture || '').toLowerCase().includes(kw)
        )
      }
      if (this.monitorFilter !== 'all') list = list.filter(a => a.status === this.monitorFilter)
      const s = this.sortBy
      list.sort((a, b) => {
        if (s === 'health') return (b.healthScore || 0) - (a.healthScore || 0)
        if (s === 'temp') return (parseFloat(b.temperature) || 0) - (parseFloat(a.temperature) || 0)
        if (s === 'status') {
          if (a.status === b.status) return 0
          return a.status === 'alert' ? -1 : 1
        }
        return String(a.id).localeCompare(String(b.id))
      })
      return this.showAll ? list : list.slice(0, 12)
    }
  },
  watch: {
    dataStreamLogs: {
      handler() {
        if (!this.autoScroll) return
        this.$nextTick(() => {
          const box = this.$refs.consoleRef
          if (box) box.scrollTop = 0
        })
      },
      deep: true
    }
  },
  methods: {
    countSource(v) {
      const base = this.cleared ? [] : (this.dataStreamLogs || [])
      if (v === 'ALL') return base.length
      return base.filter(l => (l.source || 'OTHER') === v).length
    },
    countMonitor(v) {
      const base = this.animals || []
      if (v === 'all') return base.length
      return base.filter(a => a.status === v).length
    },
    sourceClass(source) {
      const s = String(source || '').toUpperCase()
      if (s.includes('SYSTEM')) return 'sys'
      if (s.includes('SIM')) return 'sim'
      if (s.includes('ALERT') || s.includes('ERROR')) return 'alert'
      if (s.includes('REPORT')) return 'report'
      if (s.includes('MQTT') || s.includes('WS')) return 'net'
      return 'data'
    },
    /* ---- 数值格式化 ---- */
    fmtNum(v) {
      const n = Number(v)
      if (!isFinite(n)) return v || '--'
      return n.toLocaleString('zh-CN')
    },
    fmtTemp(v) {
      const n = parseFloat(v)
      if (!isFinite(n)) return '暂无'
      return n.toFixed(1) + '°C'
    },
    /* ---- 体温：正常 38~39.5，越低越安全 ---- */
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
    /* ---- 心率：牛 55~120，羊 65~100 ---- */
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
    /* 档案中的 animal_type 可能是 cow / cattle / sheep，统一归一化后再决定图标与文案 */
    speciesKey(t) {
      return (t === 'cow' || t === 'cattle') ? 'cow' : 'sheep'
    },
    speciesText(t) {
      return (t === 'cow' || t === 'cattle') ? '牛' : '羊'
    },
    healthPct(v) {
      const n = Number(v)
      if (!isFinite(n)) return 0
      return Math.max(0, Math.min(100, n))
    },
    healthText(v) {
      const n = Number(v)
      if (!isFinite(n)) return '--'
      return n
    },
    healthColor(v) {
      const n = Number(v)
      if (!isFinite(n)) return 'var(--text-muted)'
      if (n >= 85) return 'var(--viz-green)'
      if (n >= 70) return 'var(--viz-amber)'
      return 'var(--viz-red)'
    }
  }
}
</script>

<style scoped>
/* ============ 数据流 ============ */
.stream-tools { display: flex; align-items: center; gap: 14px; }
.stream-toggle { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); cursor: pointer; user-select: none; }
.stream-toggle input { accent-color: var(--primary-color); cursor: pointer; }
.stream-count { font-size: 12px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
.mini-icon-btn {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid var(--surface-line);
  background: var(--surface-soft);
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  font-size: 12px; cursor: pointer;
  transition: var(--transition);
}
.mini-icon-btn:hover { background: rgba(224,112,112,0.1); color: var(--danger-dark); border-color: rgba(224,112,112,0.3); }

.stream-filters {
  display: flex; gap: 4px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--surface-line);
  background: rgba(91,168,216,0.03);
  overflow-x: auto;
}
.stream-filters button {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 12px;
  font-size: 11.5px; font-family: inherit; font-weight: 600;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  cursor: pointer;
  white-space: nowrap;
  transition: var(--transition);
}
.stream-filters button em {
  font-style: normal; font-size: 10px; font-weight: 700;
  padding: 0 5px; border-radius: var(--radius-pill);
  background: rgba(91,168,216,0.14); color: var(--primary-dark);
}
.stream-filters button:hover { background: rgba(91,168,216,0.09); }
.stream-filters button.active {
  background: #fff; border-color: var(--surface-line-strong);
  color: var(--primary-dark);
  box-shadow: var(--shadow-xs);
}

.stream-console {
  height: 264px;
  overflow-y: auto;
  padding: 12px 16px;
  background: #F7FBFE;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.7;
}
.stream-console::-webkit-scrollbar { width: 8px; }
.stream-console::-webkit-scrollbar-thumb { background: rgba(91,168,216,0.28); border-radius: 8px; }
.stream-console::-webkit-scrollbar-track { background: transparent; }

.console-line {
  display: flex; align-items: baseline; gap: 10px;
  padding: 4px 8px;
  border-radius: 6px;
  border-left: 2px solid transparent;
  animation: lineIn 0.32s var(--ease-out);
}
.console-line:hover { background: rgba(91,168,216,0.06); }
@keyframes lineIn { from { opacity: 0; transform: translateX(-10px); } to { opacity: 1; transform: translateX(0); } }
.cl-time { color: var(--text-muted); flex-shrink: 0; font-size: 12px; }
.cl-badge {
  flex-shrink: 0;
  font-size: 10.5px; font-weight: 700;
  padding: 1px 8px;
  border-radius: 4px;
  letter-spacing: 0.3px;
}
.cl-badge.sys { background: rgba(91,168,216,0.16); color: var(--primary-dark); }
.cl-badge.sim { background: rgba(155,143,212,0.18); color: #6B5EB8; }
.cl-badge.alert { background: var(--state-danger-soft); color: var(--danger-dark); }
.cl-badge.report { background: var(--state-online-soft); color: var(--success-dark); }
.cl-badge.net { background: rgba(79,195,192,0.18); color: #2C8C89; }
.cl-badge.data { background: var(--state-offline-soft); color: #63798C; }
.cl-msg { flex: 1; color: var(--text-secondary); word-break: break-word; }

/* ============ 监测工具栏 ============ */
.monitor-tools { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.monitor-tools .ym-search { min-width: 200px; }
.monitor-tools .ym-range button {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 13px; font-size: 12px; font-family: inherit;
  color: var(--text-secondary);
  background: var(--surface-soft);
  border: 1px solid var(--surface-line);
  border-radius: var(--radius-pill);
  cursor: pointer; transition: var(--transition);
}
.monitor-tools .ym-range button em {
  font-style: normal; font-size: 10px; font-weight: 700;
  padding: 0 5px; border-radius: var(--radius-pill);
  background: rgba(91,168,216,0.14); color: var(--primary-dark);
}
.monitor-tools .ym-range button.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
  border-color: transparent; color: #fff; font-weight: 600;
  box-shadow: 0 3px 10px rgba(91,168,216,0.3);
}
.monitor-tools .ym-range button.active em { background: rgba(255,255,255,0.26); color: #fff; }
.monitor-tools .ym-range button:hover:not(.active) { background: rgba(91,168,216,0.12); color: var(--primary-dark); }

.sort-select {
  display: flex; align-items: center; gap: 7px;
  padding: 0 12px;
  background: var(--surface-soft);
  border: 1.5px solid var(--surface-line);
  border-radius: var(--radius-pill);
}
.sort-select i { font-size: 11px; color: var(--text-muted); }
.sort-select select {
  border: none; outline: none; background: transparent;
  padding: 8px 0; font-size: 12.5px; font-family: inherit;
  color: var(--text-primary); cursor: pointer;
}

/* ============ 动物卡片 ============ */
.animal-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.animal-card-v2 {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  padding: 16px;
  transition: var(--transition);
  overflow: hidden;
}
.animal-card-v2::before {
  content: '';
  position: absolute; left: 0; top: 0; bottom: 0; width: 3px;
  background: linear-gradient(180deg, var(--viz-green), var(--success-dark));
  transition: var(--transition);
}
.animal-card-v2.alert::before { background: linear-gradient(180deg, var(--viz-red), #C0392B); }
.animal-card-v2:hover { transform: translateY(-4px); box-shadow: var(--card-shadow-hover); border-color: var(--surface-line-strong); }

.ac-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 14px; }
.ac-ident { display: flex; align-items: center; gap: 11px; min-width: 0; }
.ac-avatar {
  width: 40px; height: 40px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 12px;
  font-size: 16px; color: #fff;
}
.ac-avatar.cow { background: linear-gradient(135deg, #7B9FE0, #5A7EC0); box-shadow: 0 4px 12px rgba(90,126,192,0.3); }
.ac-avatar.sheep { background: linear-gradient(135deg, #52B788, #40916C); box-shadow: 0 4px 12px rgba(64,145,108,0.28); }
.ac-ident-text { min-width: 0; }
.ac-id { font-size: 14.5px; font-weight: 700; color: var(--text-primary); font-family: var(--font-mono); }
.ac-name { font-size: 12px; color: var(--text-muted); margin-top: 2px; display: flex; align-items: center; gap: 6px; }
.ac-species {
  font-size: 10.5px; font-weight: 700;
  padding: 1px 7px; border-radius: var(--radius-pill);
  background: rgba(91,168,216,0.12); color: var(--primary-dark);
}

.ac-vitals { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; margin-bottom: 13px; }
.ac-vital-head {
  display: flex; align-items: baseline; justify-content: space-between; gap: 6px;
  font-size: 11.5px; color: var(--text-muted); margin-bottom: 6px;
}
.ac-vital-head span { display: inline-flex; align-items: center; gap: 5px; }
.ac-vital-head span i { color: var(--primary-color); }
.ac-vital-head b { font-size: 13px; font-variant-numeric: tabular-nums; }
.ac-vital-bar { height: 6px; border-radius: var(--radius-pill); background: rgba(91,168,216,0.12); overflow: hidden; }
.ac-vital-bar span { display: block; height: 100%; border-radius: var(--radius-pill); transition: width 0.55s var(--ease-out); }

.ac-stats {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
  padding: 11px 0;
  border-top: 1px dashed var(--surface-line);
  border-bottom: 1px dashed var(--surface-line);
  margin-bottom: 12px;
}
.ac-stat { display: flex; flex-direction: column; gap: 3px; }
.ac-stat-label { font-size: 11px; color: var(--text-muted); display: inline-flex; align-items: center; gap: 5px; }
.ac-stat-label i { color: var(--primary-color); }
.ac-stat-value { font-size: 13.5px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }

.ac-foot { display: flex; align-items: center; }
.ac-health { flex: 1; display: flex; align-items: center; gap: 9px; }
.ac-health-label { font-size: 11px; color: var(--text-muted); white-space: nowrap; }
.ac-health-bar { flex: 1; height: 6px; border-radius: var(--radius-pill); background: rgba(91,168,216,0.12); overflow: hidden; }
.ac-health-bar span { display: block; height: 100%; border-radius: var(--radius-pill); transition: width 0.55s var(--ease-out); }
.ac-health b { font-size: 12.5px; font-variant-numeric: tabular-nums; }

/* ============ 列表尾部 ============ */
.list-foot {
  display: flex; align-items: center; justify-content: center; gap: 14px;
  margin-top: 16px; padding-top: 14px;
  border-top: 1px dashed var(--surface-line);
  font-size: 12.5px; color: var(--text-muted);
}
.list-foot b { color: var(--text-primary); font-variant-numeric: tabular-nums; }
.link-btn {
  border: none; background: none;
  color: var(--primary-dark);
  font-size: 12.5px; font-family: inherit; font-weight: 600;
  cursor: pointer; display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 10px; border-radius: var(--radius-pill);
  transition: var(--transition);
}
.link-btn:hover { background: rgba(91,168,216,0.12); }

.pct { color: var(--text-primary); font-weight: 700; }
.live-dot { display: inline-flex; align-items: center; gap: 5px; color: var(--warning-dark); font-weight: 600; }
.live-dot::before {
  content: '';
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--warning-color);
  animation: pulse 1.6s infinite;
}

@media (max-width: 768px) {
  .animal-cards-grid { grid-template-columns: 1fr; }
  .monitor-tools { width: 100%; }
  .monitor-tools .ym-search { flex: 1 1 100%; }
}
</style>
