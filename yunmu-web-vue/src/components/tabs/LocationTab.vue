<template>
  <div class="tab-content">
    <div class="content-header"><h2><i class="fas fa-map-marker-alt"></i> 北斗定位</h2></div>
    <div class="filter-panel">
      <div class="filter-row">
        <button class="btn btn-primary" :class="{loading: loading.location}" @click="$emit('load')"><i class="fas fa-map-marker-alt"></i> 加载位置</button>
        <button class="btn btn-info" @click="$emit('clear')"><i class="fas fa-eraser"></i> 清除轨迹</button>
        <div class="filter-item" style="margin-left:auto">
          <label>更新频率:</label>
          <select v-model="locationUpdateInterval" @change="$emit('interval-change')">
            <option value="5000">5秒</option><option value="10000">10秒</option><option value="30000">30秒</option>
          </select>
        </div>
      </div>
    </div>
    <div class="dashboard-card">
      <div class="card-header"><h3><i class="fas fa-map"></i> 实时位置</h3></div>
      <div class="map-container"><div id="locationMap"></div></div>
    </div>
  </div>
</template>
<script>
import { initMap, updateMarkers, destroyMap } from '../../utils/amap.js'
export default {
  name: 'LocationTab',
  props: { locationData: Array, locationUpdateInterval: Number, loading: Object },
  watch: { locationData: { handler(val) { this.$nextTick(() => { if (val && val.length) updateMarkers(val, window._yunmuMap) }) }, deep: true } },
  mounted() { this.$nextTick(() => { this._map = initMap('locationMap', [90.0, 30.5]); if (this.locationData && this.locationData.length) updateMarkers(this.locationData, this._map) }) },
  beforeDestroy() { destroyMap() }
}
</script>