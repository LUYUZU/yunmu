/* ========================================
   云牧智感 - 高德地图封装
   支持：初始化 / 渲染自定义标记 / 聚焦某个体 / 视野自适应 /
        底图风格切换 / 缩放 / 尺寸重算 / 销毁
   ======================================== */

let _map = null
let _markers = {}        // id -> { marker, info, animal }
let _infoWin = null
let _activeId = null

const COLOR_NORMAL = '#52B788'
const COLOR_ALERT = '#E07070'

function isAlertOf(a) { return a.status === 'alert' || a.status === '异常' }

function markerHtml(animal) {
  const alert = isAlertOf(animal)
  const c = alert ? COLOR_ALERT : COLOR_NORMAL
  const soft = alert ? 'rgba(224,112,112,0.18)' : 'rgba(82,183,136,0.18)'
  const label = animal.id || animal.deviceId || '未知'
  const species = animal.type === 'sheep' ? '羊' : (animal.type === 'cow' ? '牛' : '')
  return `<div class="ym-marker ${alert ? 'is-alert' : ''} ${_activeId === label ? 'is-active' : ''}"
      style="--c:${c};--c-soft:${soft}" title="${label}">
      <span class="ym-marker-dot"></span>
      <span class="ym-marker-label">${label}</span>
      ${species ? `<span class="ym-marker-species">${species}</span>` : ''}
    </div>`
}

function infoHtml(animal) {
  const alert = isAlertOf(animal)
  const c = alert ? COLOR_ALERT : COLOR_NORMAL
  const lat = Number(animal.latitude || animal.lat)
  const lng = Number(animal.longitude || animal.lng)
  const rows = [
    ['行为', animal.behavior || animal.posture || '未知'],
    ['速度', animal.speed !== undefined ? `${animal.speed} m/s` : '--'],
    ['心率', animal.heartRate !== undefined ? `${animal.heartRate} bpm` : '--'],
    ['体温', animal.temperature !== undefined ? `${animal.temperature} ℃` : '--'],
    ['电量', animal.battery !== undefined ? `${animal.battery}%` : '--']
  ].map(([k, v]) => `<div class="ym-info-row"><span>${k}</span><b>${v}</b></div>`).join('')
  return `<div class="ym-info">
      <div class="ym-info-head">
        <span class="ym-info-title"><i class="fa fa-paw"></i>${animal.id || animal.deviceId || '未知'}</span>
        <span class="ym-info-tag ${alert ? 'alert' : ''}">${alert ? '异常' : '正常'}</span>
      </div>
      <div class="ym-info-grid">
        ${rows}
        <div class="ym-info-row wide"><span>经纬度</span><b>${isFinite(lat) ? lat.toFixed(4) : '--'}, ${isFinite(lng) ? lng.toFixed(4) : '--'}</b></div>
      </div>
      <div class="ym-info-foot"><i class="fa fa-clock"></i>${animal.timestamp || '更新时间未知'}</div>
    </div>`
}

export function initMap(containerId, center) {
  if (typeof AMap === 'undefined') return null
  if (_map) { try { _map.destroy() } catch (e) { /* ignore */ } _map = null }
  _markers = {}
  _infoWin = null
  _activeId = null
  _map = new AMap.Map(containerId, {
    zoom: 9,
    center: center || [90.0, 30.5],
    mapStyle: 'amap://styles/light',
    resizeEnable: true,
    viewMode: '2D'
  })
  window._yunmuMap = _map
  return _map
}

export function updateMarkers(animals, map) {
  const m = map || _map
  if (!m) return
  if (!_infoWin) {
    _infoWin = new AMap.InfoWindow({ isCustom: false, offset: new AMap.Pixel(0, -56) })
  }
  const list = animals || []
  const seen = {}
  list.forEach(animal => {
    const lat = parseFloat(animal.latitude || animal.lat)
    const lng = parseFloat(animal.longitude || animal.lng)
    if (isNaN(lat) || isNaN(lng)) return
    const id = animal.id || animal.deviceId
    if (!id) return
    seen[id] = true
    const pos = [lng, lat]
    const exist = _markers[id]
    // 复用已有标记：避免周期性刷新时整屏闪烁、丢失选中态
    if (exist) {
      exist.animal = animal
      exist.marker.setPosition(pos)
      exist.marker.setTitle(String(id))
      exist.marker.setContent(markerHtml(animal))
    } else {
      const marker = new AMap.Marker({
        position: pos,
        title: String(id),
        content: markerHtml(animal),
        offset: new AMap.Pixel(0, 0),
        zIndex: 110
      })
      marker.on('click', () => openInfo(id))
      marker.setMap(m)
      _markers[id] = { marker, animal }
    }
  })
  // 清理已消失的个体
  Object.keys(_markers).forEach(id => {
    if (!seen[id]) {
      try { _markers[id].marker.setMap(null) } catch (e) { /* ignore */ }
      delete _markers[id]
    }
  })
  // 选中的信息窗跟随最新位置；个体消失时自动关闭
  if (_activeId) {
    if (_markers[_activeId]) openInfo(_activeId)
    else { _activeId = null; _infoWin.close() }
  }
}

/** 打开某个体的信息窗（并高亮标记） */
export function openInfo(id) {
  const item = _markers[id]
  if (!item || !_map || !_infoWin) return
  _activeId = id
  Object.keys(_markers).forEach(k => {
    _markers[k].marker.setContent(markerHtml(_markers[k].animal))
  })
  _infoWin.setContent(infoHtml(item.animal))
  _infoWin.open(_map, item.marker.getPosition())
}

/** 聚焦到某个体：平移 + 适度放大 + 打开信息窗 */
export function focusMarker(id, zoom) {
  const item = _markers[id]
  if (!item || !_map) return false
  _map.setZoomAndCenter(zoom || 13, item.marker.getPosition())
  openInfo(id)
  return true
}

/** 视野自适应到全部标记 */
export function fitMarkers() {
  if (!_map || !Object.keys(_markers).length) return
  _infoWin && _infoWin.close()
  _map.setFitView(Object.values(_markers).map(o => o.marker), false, [70, 70, 70, 70], 14)
}

/** 切换底图风格：light / normal / dark */
export function setMapStyle(style) {
  if (!_map) return
  const preset = { light: 'amap://styles/light', normal: 'amap://styles/normal', dark: 'amap://styles/dark' }
  _map.setMapStyle(preset[style] || preset.light)
}

export function zoomBy(delta) {
  if (!_map) return
  const z = Math.min(18, Math.max(3, _map.getZoom() + delta))
  _map.setZoom(z)
}

export function resizeMap() {
  if (_map && typeof _map.resize === 'function') _map.resize()
}

/** 返回当前已有的定位点 id 列表（用于联动列表高亮） */
export function getMarkerIds() { return Object.keys(_markers) }

export function destroyMap() {
  if (_map) { try { _map.destroy() } catch (e) { /* ignore */ } _map = null }
  _markers = {}
  _infoWin = null
  _activeId = null
  window._yunmuMap = null
}

export function getMap() { return _map }
