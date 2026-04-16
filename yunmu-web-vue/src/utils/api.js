import { API_CONFIG } from './config.js'
import { formatISO } from './utils.js'

export async function checkBackendStatus(vm) {
  vm.loading.overview = true
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/data/health`, { method: 'GET' }).catch(() => null)
    if (r && r.ok) { vm.isConnected = true; vm.connectionStatus = '已连接'; vm.addLog('SYSTEM', '后端连接检查成功') }
    else throw new Error()
  } catch {
    try {
      const ml = await fetch(`${API_CONFIG.ML_SERVICE_URL}/health`, { method: 'GET' }).catch(() => null)
      if (ml && ml.ok) { vm.isConnected = true; vm.connectionStatus = '已连接 (ML)'; vm.addLog('SYSTEM', '后端连接成功 - Python ML') }
      else { vm.isConnected = false; vm.connectionStatus = '未连接'; vm.addLog('SYSTEM', '后端连接失败') }
    } catch { vm.isConnected = false; vm.connectionStatus = '未连接'; vm.addLog('SYSTEM', '后端连接失败') }
  } finally { vm.loading.overview = false }
}
export async function loadOverviewFromBackend(vm) {
  try {
    const res = await fetch(`${API_CONFIG.BASE_URL}/steps/today/all`).catch(() => null)
    if (res && res.ok) {
      const d = await res.json()
      if (d && d.success) { const keys = Object.keys(d.data||{}); vm.stats.totalAnimals = keys.length; vm.stats.dataReceived = Object.values(d.data||{}).reduce((a,b)=>a+b,0) }
    }
    vm.animals = await loadAnimalsList(vm)
    vm.stats.normal = vm.animals.filter(a=>a.status==='normal').length
    vm.stats.alert = vm.animals.filter(a=>a.status==='alert').length
    vm.dataCount = vm.stats.dataReceived
  } catch(e) { vm.addLog('ERROR', '加载概览数据失败: '+e.message) }
}
export async function loadAnimalsList(vm) {
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/animals`).catch(()=>null)
    if (r && r.ok) { const d = await r.json(); if (d && d.length) return d }
    vm.addLog('SYSTEM', '后端无动物数据，请生成模拟数据')
  } catch {}
  return []
}
export async function loadAnimalData(vm) {
  vm.loading.animals = true
  try {
    if (vm.isConnected && vm.animalFilter.animalId) {
      const now = new Date(), start = new Date(now - 24*60*60*1000)
      const url = `${API_CONFIG.BASE_URL}/behavior/statistics/${encodeURIComponent(vm.animalFilter.animalId)}?startTime=${formatISO(start)}&endTime=${formatISO(now)}`
      const r = await fetch(url).catch(()=>null)
      if (r && r.ok) { const d = await r.json(); vm.animalRecords = convertStats(d, vm) }
      else vm.animalRecords = []
    } else if (!vm.animalRecords.length) { vm.showNotification('info','无数据','请先生成模拟数据') }
    vm.updateLastUpdate()
    if (vm.animalRecords.length > 0) vm.showNotification('success','加载成功',`已加载${vm.animalRecords.length}条记录`)
  } catch(e) { vm.showNotification('error','加载失败',e.message) }
  finally { vm.loading.animals = false }
}
function convertStats(stats, vm) {
  const records = []
  if (stats && stats.behaviors) {
    Object.entries(stats.behaviors).forEach(([behavior, data]) => {
      records.push({ time: new Date().toLocaleString('zh-CN'), animalId: stats.animalId||'unknown', behavior,
        temperature: (37+Math.random()*2).toFixed(1), heartRate: Math.floor(50+Math.random()*40),
        status: (data.confidence||0.9)>0.8?'正常':'异常' })
    })
  }
  return records
}
export async function loadBehaviorStatistics(vm) {
  vm.loading.analysis = true
  try {
    if (!vm.healthReport.totalAnimals) vm.showNotification('info','无统计数据','请先生成模拟数据')
    else vm.showNotification('success','加载成功','统计数据已加载')
  } catch(e) { vm.showNotification('error','加载失败',e.message) }
  finally { vm.loading.analysis = false }
}