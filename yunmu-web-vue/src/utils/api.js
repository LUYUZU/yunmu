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
    // 1) 动物档案：只含基础信息（id/type/breed/weight/deviceId 等），不含实时指标，也不含动物名字
    let archive = []
    const r = await fetch(`${API_CONFIG.BASE_URL}/animals`).catch(() => null)
    if (r && r.ok) {
      const d = await r.json()
      if (Array.isArray(d)) archive = d
    }

    // 2) 实时指标：来自真实链路（后端为每只动物取最新 sensor_data）
    const realtime = {}
    const r2 = await fetch(`${API_CONFIG.BASE_URL}/realtime/summary`).catch(() => null)
    if (r2 && r2.ok) {
      const d2 = await r2.json()
      if (d2 && d2.success && Array.isArray(d2.animals)) {
        d2.animals.forEach(a => { if (a && a.id) realtime[a.id] = a })
      }
    }

    // 3) 合并：档案为基础，实时字段以 /realtime/summary 为准
    const idSet = new Set([...archive.map(a => a && a.id).filter(Boolean), ...Object.keys(realtime)])
    if (!idSet.size) {
      vm.addLog('SYSTEM', '后端无动物数据，请先启动数据模拟器，或等待真实项圈上报')
      return []
    }
    return Array.from(idSet).map(id => {
      const base = archive.find(a => a && a.id === id) || {}
      const rt = realtime[id] || {}
      const pickRt = (k) => (rt[k] !== undefined && rt[k] !== null ? rt[k] : base[k])
      return {
        ...base,
        id,
        type: base.type || rt.type || '',
        status: rt.status || 'normal',
        temperature: pickRt('temperature'),
        heartRate: pickRt('heartRate'),
        steps: pickRt('steps'),
        latitude: rt.latitude,
        longitude: rt.longitude,
        // 以下字段只能来自真实链路：后端 /realtime/summary 汇总自 behavior_results / posture_results，
        // 前端不生成、不兜底，查不到就是空（显示为"未知"）
        behavior: rt.behavior,
        behaviorType: rt.behaviorType,
        posture: rt.posture,
        postureType: rt.postureType,
        healthScore: rt.healthScore
      }
    })
  } catch (e) {
    vm.addLog('ERROR', '加载动物列表失败: ' + e.message)
  }
  return []
}

// ========== 动物监测 - 从后端加载真实数据 ==========

// opts.silent = true 时用于「切页自动加载」：静默完成，不弹成功/无数据提示（错误仍会提示）
export async function loadAnimalData(vm, opts = {}) {
  vm.loading.animals = true
  try {
    // 如果没有指定 animalId，加载所有动物的最近行为记录
    if (!vm.animalFilter.animalId) {
      // 从 behavior_results 表获取所有动物的最近记录
      const r = await fetch(`${API_CONFIG.BASE_URL}/realtime/summary`).catch(()=>null)
      if (r && r.ok) {
        const d = await r.json()
        if (d && d.success && d.animals) {
          // 转换为 animalRecords 格式
          vm.animalRecords = d.animals.map(a => ({
            time: new Date().toLocaleString('zh-CN'),
            animalId: a.id,
            behavior: a.behavior || '未知',
            temperature: a.temperature ? String(a.temperature) : '--',
            heartRate: a.heartRate || '--',
            status: a.status
          }))
        }
      }
    } else {
      // 有指定 animalId，调用 behavior/statistics
      const now = new Date(), start = new Date(now - 24*60*60*1000)
      const url = `${API_CONFIG.BASE_URL}/behavior/statistics/${encodeURIComponent(vm.animalFilter.animalId)}?startTime=${formatISO(start)}&endTime=${formatISO(now)}`
      const r = await fetch(url).catch(()=>null)
      if (r && r.ok) {
        const d = await r.json()
        vm.animalRecords = convertBehaviorStats(d, vm.animalFilter.animalId)
      } else {
        // 兜底：从 realtime/latest 获取单个动物数据
        const r2 = await fetch(`${API_CONFIG.BASE_URL}/realtime/latest/${encodeURIComponent(vm.animalFilter.animalId)}`).catch(()=>null)
        if (r2 && r2.ok) {
          const d2 = await r2.json()
          if (d2 && d2.success && d2.data) {
            vm.animalRecords = [{
              time: new Date().toLocaleString('zh-CN'),
              animalId: d2.data.animalId,
              behavior: '实时',
              temperature: d2.data.temperature ? String(d2.data.temperature) : '--',
              heartRate: d2.data.heartRate || '--',
              status: '正常'
            }]
          }
        }
      }
    }
    
    vm.updateLastUpdate()
    if (opts.silent) return
    if (vm.animalRecords.length > 0) {
      vm.showNotification('success', '加载成功', `已加载 ${vm.animalRecords.length} 条记录`)
    } else {
      vm.showNotification('info', '无数据', '数据库暂无记录，请发送 MQTT 数据')
    }
  } catch(e) { 
    vm.showNotification('error', '加载失败', e.message)
    vm.addLog('ERROR', '加载动物数据失败: '+e.message)
  }
  finally { vm.loading.animals = false }
}

function convertBehaviorStats(stats, animalId) {
  const records = []
  if (stats && stats.behaviors) {
    Object.entries(stats.behaviors).forEach(([behavior, data]) => {
      records.push({
        time: new Date().toLocaleString('zh-CN'),
        animalId: stats.animalId || animalId,
        behavior: translateBehavior(behavior),
        temperature: '--',
        heartRate: '--',
        status: (data.confidence || 0.9) > 0.8 ? '正常' : '异常'
      })
    })
  } else if (stats && stats.animalId) {
    // 单条记录格式
    records.push({
      time: new Date().toLocaleString('zh-CN'),
      animalId: stats.animalId,
      behavior: stats.behaviorType || '未知',
      temperature: '--',
      heartRate: '--',
      status: '正常'
    })
  }
  return records
}

function translateBehavior(b) {
  const map = { resting: '休息', standing: '站立', walking: '行走', feeding: '采食', running: '奔跑', unknown: '未知' }
  return map[b] || b
}

/**
 * 解析一个真实存在的个体 ID。
 *
 * 兜底顺序：显式传入 → 已加载的动物列表 → 后端 /animals 首个个体。
 *
 * 注意：这里**不能**写死 `'cow_7249'` 这类常量——它是早期 mock 数据的遗留 ID，
 * 库里并不存在（真实 ID 为 cow_001 / cow_002 / sheep_001），
 * 一旦 vm.animals 尚未加载完成，就会命中一个不存在的个体而永远查不到数据。
 */
async function resolveAnimalId(vm, explicit) {
  if (explicit) return explicit
  const cached = vm.animals && vm.animals[0] && vm.animals[0].id
  if (cached) return cached
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/animals`).catch(() => null)
    if (r && r.ok) {
      const list = await r.json()
      if (Array.isArray(list) && list.length && list[0] && list[0].id) return list[0].id
    }
  } catch (e) { /* 忽略：由调用方按"无数据"处理 */ }
  return null
}

// ========== 步数统计 - 从后端加载真实数据 ==========

export async function loadStepDataFromBackend(vm, animalId = null, range = '7d') {
  vm.loading.steps = true
  try {
    const targetId = await resolveAnimalId(vm, animalId)
    if (!targetId) {
      vm.stepHourlyData = []
      vm.showNotification('info', '无数据', '尚未获取到个体档案，请先在「系统概览」启动数据模拟器')
      return
    }
    const days = parseInt(range) || 7
    
    // 获取今日步数
    const todayRes = await fetch(`${API_CONFIG.BASE_URL}/steps/today/${encodeURIComponent(targetId)}`).catch(()=>null)
    let todaySteps = 0
    if (todayRes && todayRes.ok) {
      const d = await todayRes.json()
      if (d && d.success) todaySteps = d.todaySteps || 0
    }
    
    // 获取步数统计（距离、时长、频率）
    const now = new Date()
    const start = new Date(now - days * 86400000)
    const statsRes = await fetch(`${API_CONFIG.BASE_URL}/steps/statistics/${encodeURIComponent(targetId)}?startTime=${formatISO(start)}&endTime=${formatISO(now)}`).catch(()=>null)
    
    if (statsRes && statsRes.ok) {
      const d = await statsRes.json()
      vm.stepStats = {
        todaySteps: d.totalSteps || todaySteps,
        walkingDistance: Math.round((d.totalDistance || 0) * 100) / 100,
        activeTime: d.totalActiveDuration || 0,
        stepFrequency: Math.round((d.avgStepsPerHour || 0) * 10) / 10
      }
    } else {
      vm.stepStats.todaySteps = todaySteps
    }
    
    // 2. 加载每小时步数
    const hourlyRes = await fetch(`${API_CONFIG.BASE_URL}/steps/hourly/${encodeURIComponent(targetId)}?hours=${days * 24}`).catch(()=>null)
    if (hourlyRes && hourlyRes.ok) {
      const d = await hourlyRes.json()
      if (d && d.success && d.hourlyData) {
        vm.stepHourlyData = d.hourlyData.map(h => ({
          hour: h.hour,
          steps: h.steps
        }))
      }
    }
    
    vm.updateLastUpdate()
    if (vm.stepStats.todaySteps > 0) {
      vm.showNotification('success', '加载成功', `今日步数: ${vm.stepStats.todaySteps}`)
    } else {
      vm.showNotification('info', '无步数数据', '数据库暂无步数记录')
    }
  } catch(e) {
    vm.showNotification('error', '加载失败', e.message)
    vm.addLog('ERROR', '加载步数数据失败: '+e.message)
  }
  finally { vm.loading.steps = false }
}

// ========== 分析报告 - 从后端加载真实数据 ==========

export async function loadBehaviorStatistics(vm) {
  vm.loading.analysis = true
  try {
    // 1. 从 realtime/summary 获取动物总数和状态统计
    const summaryRes = await fetch(`${API_CONFIG.BASE_URL}/realtime/summary`).catch(()=>null)
    if (summaryRes && summaryRes.ok) {
      const d = await summaryRes.json()
      if (d && d.success) {
        vm.healthReport.totalAnimals = d.total || 0
        vm.healthReport.normalAnimals = d.normal || 0
        vm.healthReport.alertAnimals = d.alert || 0
      }
    }
    
    // 2. 获取步数统计用于报告
    const stepsRes = await fetch(`${API_CONFIG.BASE_URL}/steps/today/all`).catch(()=>null)
    if (stepsRes && stepsRes.ok) {
      const d = await stepsRes.json()
      if (d && d.success && d.data) {
        const totalSteps = Object.values(d.data).reduce((a, b) => a + b, 0)
        vm.analysis.stepAlerts = totalSteps > 10000 ? 1 : 0
      }
    }
    
    vm.updateLastUpdate()
    if (vm.healthReport.totalAnimals > 0) {
      vm.showNotification('success', '加载成功', `统计了 ${vm.healthReport.totalAnimals} 只动物`)
    } else {
      vm.showNotification('info', '无统计数据', '数据库暂无记录')
    }
  } catch(e) {
    vm.showNotification('error', '加载失败', e.message)
    vm.addLog('ERROR', '加载分析报告失败: '+e.message)
  }
  finally { vm.loading.analysis = false }
}

// ========== 历史数据 - 从后端加载 ==========

// opts.silent = true 时用于「切页自动加载」：静默完成，不弹成功/无数据提示（错误仍会提示）
export async function loadHistoryDataFromBackend(vm, opts = {}) {
  vm.loading.data = true
  try {
    const animalId = await resolveAnimalId(vm, vm.dataFilter.animalId)
    if (!animalId) {
      vm.historyData = []
      if (!opts.silent) vm.showNotification('info', '无数据', '尚未获取到个体档案，请先在「系统概览」启动数据模拟器')
      return
    }
    const start = vm.dataFilter.startDate || new Date(Date.now() - 24*60*60*1000).toISOString().slice(0, 19)
    const end = vm.dataFilter.endDate || new Date().toISOString().slice(0, 19)
    
    const url = `${API_CONFIG.BASE_URL}/realtime/history/${encodeURIComponent(animalId)}?startDate=${start}&endDate=${end}&limit=100`
    const r = await fetch(url).catch(()=>null)
    
    if (r && r.ok) {
      const d = await r.json()
      if (d && d.success && d.records) {
        vm.historyData = d.records.map(rec => ({
          time: rec.time,
          animalId: rec.animalId,
          temperature: rec.temperature || '--',
          heartRate: rec.heartRate || '--',
          steps: rec.stepCount || 0,
          latitude: rec.latitude || '--',
          longitude: rec.longitude || '--'
        }))
      }
    }
    
    vm.updateLastUpdate()
    if (opts.silent) return
    if (vm.historyData.length > 0) {
      vm.showNotification('success', '加载成功', `找到 ${vm.historyData.length} 条记录`)
    } else {
      vm.showNotification('info', '无历史数据', '数据库暂无记录')
    }
  } catch(e) {
    vm.showNotification('error', '加载失败', e.message)
    vm.addLog('ERROR', '加载历史数据失败: '+e.message)
  }
  finally { vm.loading.data = false }
}
// ========== 设备数据模拟器（后端） ==========
// 说明：模拟数据由后端生成并注入 MQTT 消息处理入口，之后走完整真实链路
// （Java 解析 → Python 推理 → 回调入库 → WebSocket 推送），前端只负责启停与状态同步。

export async function fetchSimulatorStatus(vm) {
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/simulator/status`, { method: 'GET' }).catch(() => null)
    if (r && r.ok) return await r.json()
  } catch (e) { /* 静默：后端未启动时不打扰用户 */ }
  return null
}

export async function startSimulator(vm, options) {
  const body = Object.assign({
    intervalMs: 5000,
    durationSeconds: 0,
    anomalyRate: 0.06
  }, options || {})
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/simulator/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    const data = await r.json().catch(() => null)
    return { ok: r.ok, data }
  } catch (e) {
    return { ok: false, data: { success: false, message: '无法连接后端模拟器接口：' + e.message } }
  }
}

export async function stopSimulator(vm) {
  try {
    const r = await fetch(`${API_CONFIG.BASE_URL}/simulator/stop`, { method: 'POST' })
    const data = await r.json().catch(() => null)
    return { ok: r.ok, data }
  } catch (e) {
    return { ok: false, data: { success: false, message: '无法连接后端模拟器接口：' + e.message } }
  }
}
