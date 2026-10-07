<template>
  <div class="tab-content location-page">
    <div class="content-header">
      <h2><i class="fas fa-map-location-dot"></i> 北斗定位</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{ loading: busy }" @click="$emit('load')">
          <i class="fas fa-location-crosshairs"></i> 加载位置
        </button>
        <button class="btn btn-info" @click="$emit('clear')"><i class="fas fa-eraser"></i> 清除轨迹</button>
        <div class="interval-group">
          <label><i class="fas fa-stopwatch"></i> 刷新频率</label>
          <div class="range-btns">
            <button v-for="o in intervalOptions" :key="o.value" :class="{ active: interval === o.value }" @click="setIntervalValue(o.value)">{{ o.label }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 概览指标 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:linear-gradient(135deg,#5BA8D8,#3498DB)"><i class="fas fa-location-dot"></i></div>
        <div class="stat-info"><div class="stat-value">{{ list.length }}</div><div class="stat-label">定位个体数</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:linear-gradient(135deg,#52B788,#40916C)"><i class="fas fa-circle-check"></i></div>
        <div class="stat-info"><div class="stat-value">{{ normalCount }}</div><div class="stat-label">定位正常</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:linear-gradient(135deg,#E07070,#C0392B)"><i class="fas fa-triangle-exclamation"></i></div>
        <div class="stat-info"><div class="stat-value">{{ alertCount }}</div><div class="stat-label">异常个体</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:linear-gradient(135deg,#7B9FE0,#5A7EC0)"><i class="fas fa-gauge-high"></i></div>
        <div class="stat-info"><div class="stat-value">{{ avgSpeed }}<em>m/s</em></div><div class="stat-label">平均移动速度</div></div>
      </div>
    </div>

    <div class="loc-layout">
      <!-- 地图主区 -->
      <div class="dashboard-card map-card">
        <div class="card-header">
          <h3><i class="fas fa-map"></i> 实时位置分布</h3>
          <div class="map-tools">
            <div class="range-btns">
              <button v-for="s in mapStyles" :key="s.value" :class="{ active: mapStyle === s.value }" @click="changeStyle(s.value)">{{ s.label }}</button>
            </div>
            <div class="zoom-tools">
              <button title="放大" @click="zoom(1)"><i class="fas fa-plus"></i></button>
              <button title="缩小" @click="zoom(-1)"><i class="fas fa-minus"></i></button>
              <button title="适应全部点位" @click="fit"><i class="fas fa-expand"></i></button>
            </div>
          </div>
        </div>
        <div class="map-wrap">
          <div id="locationMap"></div>

          <!-- 图例 -->
          <div class="map-legend">
            <span><i class="lg-dot normal"></i> 正常</span>
            <span><i class="lg-dot alert"></i> 异常</span>
          </div>

          <!-- 点位计数 -->
          <div class="map-badge">
            <i class="fas fa-satellite"></i> 已渲染 {{ list.length }} 个定位点
            <em v-if="renderedAt">· {{ renderedAt }}</em>
          </div>

          <!-- 空状态 -->
          <div v-if="!list.length" class="map-empty">
            <div class="me-card">
              <div class="me-icon"><i class="fas fa-satellite-dish"></i></div>
              <h4>暂无定位数据</h4>
              <p>请先在「系统概览」启动数据模拟器，或接入真实北斗上报服务后点击加载。</p>
              <button class="btn btn-primary" @click="$emit('load')"><i class="fas fa-location-crosshairs"></i> 加载位置</button>
            </div>
          </div>

          <!-- 地图未就绪 -->
          <div v-if="!amapReady" class="map-empty">
            <div class="me-card">
              <div class="me-icon warn"><i class="fas fa-map"></i></div>
              <h4>地图组件未就绪</h4>
              <p>未能加载高德地图 JS API，请检查网络连接或 index.html 中的地图 Key 配置。</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 侧边栏 -->
      <aside class="loc-side">
        <div class="dashboard-card side-card">
          <div class="card-header">
            <h3><i class="fas fa-list-ul"></i> 定位列表</h3>
            <span class="count-pill">{{ filtered.length }}</span>
          </div>
          <div class="side-search">
            <i class="fas fa-search"></i>
            <input v-model.trim="keyword" type="text" placeholder="搜索个体 ID / 行为" />
            <button v-if="keyword" @click="keyword = ''"><i class="fas fa-times-circle"></i></button>
          </div>
          <div class="loc-list">
            <button
              v-for="a in filtered"
              :key="a.id"
              class="loc-item"
              :class="{ active: activeId === a.id, alert: isAlert(a) }"
              @click="focus(a)">
              <span class="li-dot"></span>
              <span class="li-main">
                <span class="li-title">
                  {{ a.id }}
                </span>
                <span class="li-sub">
                  <i class="fas fa-person-walking"></i> {{ a.behavior || '未知' }}
                  <b>{{ a.speed !== undefined ? a.speed : '--' }} m/s</b>
                </span>
              </span>
              <span class="li-badge">{{ isAlert(a) ? '异常' : '正常' }}</span>
            </button>
            <div v-if="!filtered.length" class="loc-list-empty">
              <i class="fas fa-inbox"></i>
              <span>{{ list.length ? '没有匹配的个体' : '暂无定位数据' }}</span>
            </div>
          </div>
          <div class="side-foot">
            <span><i class="fas fa-sync-alt"></i> 每 {{ interval / 1000 }} 秒自动刷新</span>
          </div>
        </div>

        <div class="dashboard-card side-card">
          <div class="card-header"><h3><i class="fas fa-chart-pie"></i> 行为分布</h3></div>
          <div class="chart-container mini" ref="donutRef"></div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { initMap, updateMarkers, destroyMap, focusMarker, fitMarkers, setMapStyle, zoomBy, resizeMap } from '../../utils/amap.js'

export default {
  name: 'LocationTab',
  props: {
    locationData: { type: Array, default: () => [] },
    locationUpdateInterval: { type: Number, default: 5000 },
    /* App 传入的是 loading.location（Boolean） */
    loading: { type: [Object, Boolean], default: false }
  },
  data() {
    return {
      keyword: '',
      activeId: '',
      interval: this.locationUpdateInterval,
      renderedAt: '',
      amapReady: true,
      mapStyle: 'light',
      intervalOptions: [
        { label: '5秒', value: 5000 },
        { label: '10秒', value: 10000 },
        { label: '30秒', value: 30000 }
      ],
      mapStyles: [
        { label: '浅色', value: 'light' },
        { label: '标准', value: 'standard' },
        { label: '深色', value: 'dark' }
      ]
    }
  },
  computed: {
    busy() {
      if (typeof this.loading === 'boolean') return this.loading
      return !!(this.loading && this.loading.location)
    },
    list() { return Array.isArray(this.locationData) ? this.locationData : [] },
    filtered() {
      const kw = this.keyword.toLowerCase()
      if (!kw) return this.list
      return this.list.filter(a =>
        String(a.id || '').toLowerCase().includes(kw) ||
        String(a.behavior || a.posture || '').toLowerCase().includes(kw)
      )
    },
    alertCount() { return this.list.filter(a => this.isAlert(a)).length },
    normalCount() { return this.list.length - this.alertCount },
    avgSpeed() {
      const valid = this.list.map(a => Number(a.speed)).filter(n => isFinite(n))
      if (!valid.length) return '0.0'
      return (valid.reduce((s, n) => s + n, 0) / valid.length).toFixed(1)
    }
  },
  watch: {
    locationData: {
      handler(val) {
        this.$nextTick(() => {
          if (!this._map) return
          updateMarkers(val || [], this._map)
          this.renderedAt = this.nowText()
          this.renderDonut()
        })
      },
      deep: true
    },
    locationUpdateInterval(v) { this.interval = v }
  },
  mounted() {
    this.$nextTick(() => this.setupMap())
    window.addEventListener('resize', this.onResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.onResize)
    if (this.donutChart) this.donutChart.dispose()
    destroyMap()
  },
  methods: {
    setupMap() {
      this._map = initMap('locationMap', [90.0, 30.5])
      this.amapReady = !!this._map
      if (!this._map) return
      if (this.list.length) {
        updateMarkers(this.list, this._map)
        this.renderedAt = this.nowText()
        setTimeout(() => fitMarkers(), 300)
      }
      this.renderDonut()
    },
    onResize() { resizeMap() },
    isAlert(a) { return a.status === 'alert' || a.status === '异常' },
    nowText() {
      const d = new Date()
      const p = n => String(n).padStart(2, '0')
      return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    },
    setIntervalValue(v) {
      this.interval = v
      this.$emit('interval-change', v)
    },
    changeStyle(v) {
      this.mapStyle = v
      setMapStyle(v === 'standard' ? 'normal' : v)
    },
    zoom(delta) { zoomBy(delta) },
    fit() { fitMarkers() },
    focus(a) {
      this.activeId = a.id
      focusMarker(a.id)
    },
    renderDonut() {
      if (!this.$refs.donutRef) return
      if (!this.donutChart) this.donutChart = echarts.init(this.$refs.donutRef)
      const bands = {}
      this.list.forEach(a => {
        const k = a.behavior || a.posture || '未知'
        bands[k] = (bands[k] || 0) + 1
      })
      const palette = ['#5BA8D8', '#52B788', '#F4A261', '#7B9FE0', '#E07070', '#8FBF9F', '#B69BE0']
      const data = Object.keys(bands).map((name, i) => ({ name, value: bands[name], itemStyle: { color: palette[i % palette.length] } }))
      this.donutChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 只 ({d}%)' },
        legend: {
          bottom: 0, left: 'center',
          icon: 'circle', itemWidth: 8, itemHeight: 8,
          textStyle: { color: '#5A6B7D', fontSize: 11 }
        },
        series: [{
          name: '行为分布',
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold', formatter: '{b}\n{c} 只' }, scaleSize: 6 },
          data: data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#E0E6ED' } }]
        }]
      })
    }
  }
}
</script>

<style scoped>
.location-page { display: flex; flex-direction: column; }

/* 刷新频率 */
.interval-group { display: flex; align-items: center; gap: 8px; }
.interval-group label { font-size: 12px; color: var(--text-secondary); white-space: nowrap; }
.range-btns { display: flex; gap: 4px; }
.range-btns button {
  background: rgba(91,168,216,0.06);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: var(--radius-pill);
  padding: 6px 14px;
  font-size: 12px; font-family: inherit;
  cursor: pointer;
  transition: var(--transition);
}
.range-btns button.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
  border-color: transparent;
  color: #fff; font-weight: 600;
  box-shadow: 0 3px 10px rgba(91,168,216,0.3);
}
.range-btns button:hover:not(.active) { background: rgba(91,168,216,0.14); color: var(--primary-dark); }

.stat-value em { font-style: normal; font-size: 13px; color: var(--text-muted); margin-left: 3px; }

/* 布局 */
.loc-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}
.map-card { margin-bottom: 0; display: flex; flex-direction: column; }
.map-card .card-header { flex-wrap: wrap; gap: 12px; }
.map-tools { display: flex; align-items: center; gap: 10px; }
.zoom-tools { display: flex; gap: 4px; }
.zoom-tools button {
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-color);
  background: var(--surface-soft);
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  font-size: 12px; cursor: pointer;
  transition: var(--transition);
}
.zoom-tools button:hover { background: rgba(91,168,216,0.14); color: var(--primary-dark); border-color: var(--surface-line-strong); }

/* 地图 */
.map-wrap {
  position: relative;
  height: 560px;
  border-radius: var(--border-radius);
  overflow: hidden;
  border: 1px solid var(--border-color);
  background: #EEF4FA;
}
.map-legend {
  position: absolute; left: 14px; bottom: 14px; z-index: 5;
  display: flex; gap: 14px;
  padding: 7px 14px;
  background: rgba(255,255,255,0.94);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-sm);
  font-size: 12px; color: var(--text-secondary);
  backdrop-filter: blur(6px);
}
.map-legend span { display: inline-flex; align-items: center; gap: 6px; }
.lg-dot { width: 9px; height: 9px; border-radius: 50%; }
.lg-dot.normal { background: var(--state-online); box-shadow: 0 0 0 3px var(--state-online-soft); }
.lg-dot.alert { background: var(--state-danger); box-shadow: 0 0 0 3px var(--state-danger-soft); }

.map-badge {
  position: absolute; top: 14px; left: 14px; z-index: 5;
  display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 14px;
  background: rgba(255,255,255,0.94);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-sm);
  font-size: 12px; color: var(--text-secondary);
  backdrop-filter: blur(6px);
}
.map-badge i { color: var(--primary-color); }
.map-badge em { font-style: normal; color: var(--text-muted); }

.map-empty {
  position: absolute; inset: 0; z-index: 6;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(232,244,253,0.94), rgba(194,224,240,0.9));
  backdrop-filter: blur(2px);
}
.me-card {
  text-align: center;
  padding: 32px 40px;
  background: rgba(255,255,255,0.96);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  box-shadow: var(--shadow-md);
  max-width: 400px;
}
.me-icon {
  width: 60px; height: 60px; margin: 0 auto 14px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; color: #fff;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
  box-shadow: 0 10px 24px rgba(91,168,216,0.3);
}
.me-icon.warn { background: linear-gradient(135deg, #F4A261, #E76F51); box-shadow: 0 10px 24px rgba(244,162,97,0.3); }
.me-card h4 { font-size: 16px; color: var(--text-primary); margin-bottom: 7px; }
.me-card p { font-size: 13px; color: var(--text-muted); line-height: 1.7; margin-bottom: 18px; }

/* 侧栏 */
.loc-side { display: flex; flex-direction: column; gap: 20px; }
.side-card { margin-bottom: 0; padding: 18px; }
.side-card .card-header { margin-bottom: 14px; padding-bottom: 12px; }
.count-pill {
  font-size: 11px; font-weight: 700;
  padding: 2px 10px; border-radius: var(--radius-pill);
  background: rgba(91,168,216,0.14); color: var(--primary-dark);
}
.side-search {
  display: flex; align-items: center; gap: 9px;
  padding: 0 12px;
  background: var(--surface-soft);
  border: 1.5px solid var(--surface-line);
  border-radius: var(--radius-pill);
  margin-bottom: 12px;
  transition: var(--transition);
}
.side-search:focus-within { border-color: var(--primary-color); background: #fff; box-shadow: var(--ring-primary); }
.side-search i { font-size: 12px; color: var(--text-muted); }
.side-search input {
  flex: 1; min-width: 0;
  border: none; outline: none; background: transparent;
  padding: 8px 0; font-size: 13px; font-family: inherit; color: var(--text-primary);
}
.side-search input::placeholder { color: var(--text-muted); }
.side-search button { border: none; background: none; cursor: pointer; color: var(--text-muted); font-size: 13px; }
.side-search button:hover { color: var(--danger-color); }

.loc-list {
  height: 336px;
  overflow-y: auto;
  margin: 0 -4px;
  padding: 0 4px;
  /* 底部渐隐，提示列表可继续滚动 */
  -webkit-mask-image: linear-gradient(to bottom, #000 calc(100% - 26px), transparent 100%);
  mask-image: linear-gradient(to bottom, #000 calc(100% - 26px), transparent 100%);
}
.loc-list::-webkit-scrollbar { width: 6px; }
.loc-list::-webkit-scrollbar-thumb { background: rgba(91,168,216,0.3); border-radius: 6px; }
.loc-list::-webkit-scrollbar-track { background: transparent; }

.loc-item {
  width: 100%;
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  margin-bottom: 6px;
  background: #fff;
  border: 1px solid var(--border-color);
  border-left: 3px solid var(--state-online);
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: var(--transition);
}
.loc-item:hover { background: var(--surface-soft); transform: translateX(2px); box-shadow: var(--shadow-xs); }
.loc-item.alert { border-left-color: var(--state-danger); }
.loc-item.active {
  background: rgba(91,168,216,0.1);
  border-color: var(--primary-color);
  box-shadow: var(--ring-primary);
}
.li-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--state-online); flex-shrink: 0; }
.loc-item.alert .li-dot { background: var(--state-danger); animation: pulse 1.4s infinite; }
.li-main { flex: 1; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.li-title { font-size: 13px; font-weight: 700; color: var(--text-primary); font-family: var(--font-mono); display: flex; align-items: center; gap: 6px; }
.li-title em { font-style: normal; font-size: 11px; font-family: inherit; font-weight: 500; color: var(--text-muted); }
.li-sub { font-size: 11px; color: var(--text-muted); display: flex; align-items: center; gap: 7px; }
.li-sub i { color: var(--primary-color); }
.li-sub b { color: var(--text-secondary); font-weight: 600; }
.li-badge {
  font-size: 10px; font-weight: 700; flex-shrink: 0;
  padding: 2px 8px; border-radius: var(--radius-pill);
  background: var(--state-online-soft); color: #2F7A57;
}
.loc-item.alert .li-badge { background: var(--state-danger-soft); color: #C0392B; }

.loc-list-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 26px 0; color: var(--text-muted); font-size: 12px;
}
.loc-list-empty i { font-size: 22px; color: #C6D2DD; }

.side-foot {
  margin-top: 12px; padding-top: 10px;
  border-top: 1px dashed var(--surface-line);
  font-size: 11px; color: var(--text-muted);
}
.side-foot i { margin-right: 5px; color: var(--primary-color); }

.chart-container.mini { height: 210px; }

@media (max-width: 1400px) {
  .loc-layout { grid-template-columns: minmax(0, 1fr) 300px; }
}
@media (max-width: 1200px) {
  .loc-layout { grid-template-columns: 1fr; }
  .loc-list { height: 300px; }
}
@media (max-width: 768px) {
  .map-wrap { height: 380px; }
  .map-tools { width: 100%; justify-content: space-between; }
  .interval-group { width: 100%; }
}
</style>
