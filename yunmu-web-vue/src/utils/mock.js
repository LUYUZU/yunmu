import { formatTime } from './utils.js'
export function generateMockData(vm) {
  const total = Math.floor(Math.random()*20)+10
  const alertCount = Math.floor(Math.random()*3)
  const behaviors = ['采食','站立','行走','躺卧']
  const types = ['cow','sheep']
  vm.stats = { totalAnimals:total, normal:total-alertCount, alert:alertCount, dataReceived:Math.floor(Math.random()*1000)+500 }
  vm.animals = []
  for (let i=1; i<=Math.min(total,12); i++) {
    vm.animals.push({ id:`NO.${String(i).padStart(3,'0')}`, type:types[Math.floor(Math.random()*types.length)],
      status:i<=alertCount?'alert':'normal', temperature:(37+Math.random()*3).toFixed(1),
      heartRate:Math.floor(50+Math.random()*50), steps:Math.floor(Math.random()*5000),
      behavior:behaviors[Math.floor(Math.random()*behaviors.length)] })
  }
  vm.dataCount = vm.stats.dataReceived
  vm.animalRecords = genAnimalRecords(vm)
  vm.postureRecords = genPostureRecords(vm)
  vm.stepStats = genStepStats()
  vm.stepHourlyData = genHourlyData()
  vm.historyData = genHistoryData(vm)
  vm.locationData = genLocationData(vm)
  vm.analysis = { healthAlerts:Math.floor(Math.random()*5), postureAlerts:Math.floor(Math.random()*8), stepAlerts:Math.floor(Math.random()*6), locationAlerts:Math.floor(Math.random()*3) }
  vm.healthReport = { totalAnimals:vm.stats.totalAnimals, normalAnimals:vm.stats.normal, alertAnimals:vm.stats.alert }
  vm.updateLastUpdate()
  vm.addLog('MOCK', `生成${total}只动物的模拟监测数据`)
  vm.showNotification('success','生成成功',`已生成${total}只动物的模拟监测数据`)
}
function pickAnimal(vm) { return vm.animals[Math.floor(Math.random()*vm.animals.length)]||{id:'NO.001'} }
function genAnimalRecords(vm) {
  const behaviors=['采食','站立','行走','躺卧']
  return Array.from({length:20},(_,i)=>({ time:formatTime(new Date(Date.now()-i*30*60*1000)), animalId:pickAnimal(vm).id,
    behavior:behaviors[Math.floor(Math.random()*behaviors.length)], temperature:(37+Math.random()*3).toFixed(1),
    heartRate:Math.floor(50+Math.random()*50), status:Math.random()<0.1?'异常':'正常' }))
}
function genPostureRecords(vm) {
  const postures=['采食','站立','行走','躺卧']
  return Array.from({length:15},(_,i)=>({ time:formatTime(new Date(Date.now()-i*20*60*1000)), animalId:pickAnimal(vm).id,
    posture:postures[Math.floor(Math.random()*postures.length)], confidence:Math.floor(75+Math.random()*25), duration:Math.floor(5+Math.random()*55) }))
}
function genStepStats() { return { todaySteps:Math.floor(Math.random()*10000)+3000, walkingDistance:Math.floor(Math.random()*5000)+2000, activeTime:Math.floor(Math.random()*300)+60, stepFrequency:Math.floor(Math.random()*30)+10 } }
function genHourlyData() { return Array.from({length:24},(_,h)=>({ hour:String(h).padStart(2,'0'), steps:Math.floor(Math.random()*500)+50, distance:Math.floor(Math.random()*200)+20, activeTime:Math.floor(Math.random()*40)+5 })) }
function genHistoryData(vm) {
  const behaviors=['采食','站立','行走','躺卧']
  return Array.from({length:30},(_,i)=>({ time:formatTime(new Date(Date.now()-i*60*60*1000)), animalId:pickAnimal(vm).id,
    behavior:behaviors[Math.floor(Math.random()*behaviors.length)], steps:Math.floor(Math.random()*1000),
    location:`${(30+Math.random()*2).toFixed(4)}N, ${(90+Math.random()*2).toFixed(4)}E`, status:Math.random()<0.1?'异常':'正常' }))
}
function genLocationData(vm) { return vm.animals.map(a=>({ id:a.id, latitude:29.0+Math.random()*2.5, longitude:88.0+Math.random()*4.0, timestamp:formatTime(new Date()) })) }