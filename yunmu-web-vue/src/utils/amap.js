let _map = null
const _markers = {}
export function initMap(containerId, center) {
  if (_map) { _map.destroy(); _map = null }
  _map = new AMap.Map(containerId, { zoom:8, center:center||[90.0,30.5], mapStyle:'amap://styles/light', resizeEnable:true, viewMode:'2D' })
  window._yunmuMap = _map
  return _map
}
export function updateMarkers(animals, map) {
  Object.values(_markers).forEach(m=>m.setMap(null))
  for (const k in _markers) delete _markers[k]
  if (!map) return
  animals.forEach(animal => {
    const lat = parseFloat(animal.latitude||animal.lat||0)
    const lng = parseFloat(animal.longitude||animal.lng||0)
    if (isNaN(lat)||isNaN(lng)) return
    const isAlert = animal.status==='alert'||animal.status==='异常'
    const m = new AMap.Marker({ position:[lng,lat], title:animal.id||animal.deviceId,
      content:`<div style="background:#5BA8D8;color:#fff;padding:5px 10px;border-radius:20px;font-size:12px;font-weight:bold;box-shadow:0 2px 8px rgba(91,168,216,0.4);white-space:nowrap"><i class="fa fa-paw"></i> ${animal.id||animal.deviceId}</div>`,
      offset: new AMap.Pixel(-30,-15) })
    const info = new AMap.InfoWindow({ content:`<div style="min-width:200px;font-family:'Microsoft YaHei',sans-serif;padding:8px">
      <h4 style="margin:0 0 8px;color:#5BA8D8"><i class="fa fa-paw"></i> ${animal.id||animal.deviceId}</h4>
      <p style="margin:3px 0;color:#555"><strong>状态:</strong> <span style="color:${isAlert?'#E07070':'#52B788'};font-weight:bold">${isAlert?'异常':'正常'}</span></p>
      <p style="margin:3px 0;color:#555"><strong>行为:</strong> ${animal.behavior||animal.posture||'未知'}</p>
      <p style="margin:3px 0;color:#555"><strong>心率:</strong> ${animal.heartRate||'暂无'} bpm</p>
      <p style="margin:3px 0;color:#555"><strong>体温:</strong> ${animal.temperature||'暂无'} C</p>
      <p style="margin:3px 0;color:#888;font-size:12px"><strong>更新:</strong> ${animal.timestamp||'未知'}</p></div>`,
      offset: new AMap.Pixel(0,-30) })
    m.on('click',()=>info.open(map,m.getPosition()))
    m.setMap(map)
    _markers[animal.id||animal.deviceId] = m
  })
}
export function destroyMap() { if(_map){_map.destroy();_map=null} for(const k in _markers)delete _markers[k]; window._yunmuMap=null }
export function getMap() { return _map }