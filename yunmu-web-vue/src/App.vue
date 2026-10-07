<template>
  <div class="app-wrapper">
    <header class="header">
      <div class="header-content">
        <div class="header-left">
          <h1><i class="fas fa-cow"></i> 云牧智感 - 高原牛羊行为AI监测系统</h1>
          <p>基于机器学习的牛羊行为智能识别与监测预警系统</p>
        </div>
        <div class="header-right">
          <div class="system-status" @click="checkBackendStatus" title="点击检查连接状态">
            <div class="status-indicator" :class="{connected: isConnected}"></div>
            <span>{{ connectionStatus }}</span>
            <i v-if="loading.overview" class="fas fa-sync-alt" style="font-size:12px;margin-left:5px"></i>
          </div>
          <div class="data-status"><i class="fas fa-database"></i><span>数据: {{ dataCount }}</span></div>
          <div class="last-update"><i class="fas fa-clock"></i><span>更新: {{ lastUpdate }}</span></div>
        </div>
      </div>
    </header>
    <div class="main-container">
      <nav class="sidebar" :class="{ collapsed: isSidebarCollapsed }">
        <div class="nav-header">
          <i class="fas fa-bars"></i>
          <span class="nav-text">功能菜单</span>
        </div>
        <ul class="nav-menu">
          <li v-for="item in navItems" :key="item.tab" class="nav-item" :class="{active: currentTab===item.tab}" @click="switchTab(item.tab)">
            <a href="javascript:void(0)"><i :class="item.icon"></i><span class="nav-text">{{ item.name }}</span></a>
          </li>
        </ul>
        <div class="sidebar-toggle" @click="toggleSidebar">
          <i class="fas" :class="isSidebarCollapsed ? 'fa-chevron-right' : ''"></i>
          <i class="fas fa-bars"></i>
          <i class="fas" :class="!isSidebarCollapsed ? 'fa-chevron-left' : ''"></i>
        </div>
      </nav>
      <main class="content">
        <OverviewTab v-if="currentTab==='overview'"
          :stats="stats" :animals="animals" :dataStreamLogs="dataStreamLogs" :loading="loading" :simulatorRunning="simulatorRunning" :simulatorInfo="simulatorInfo"
          @refresh="refreshOverview" @toggle-simulator="toggleSimulator" @refresh-all="refreshAllData"
          @notify="showNotification" @add-log="addLog" @update="updateLastUpdate" />
        <AnimalsTab v-if="currentTab==='animals'"
          :animals="animals" :animalFilter="animalFilter" :animalRecords="animalRecords" :loading="loading.animals"
          @load="loadAnimalData" @export="exportAnimalData" @notify="showNotification" @update="updateLastUpdate" />
        <LocationTab v-if="currentTab==='location'"
          :locationData="locationData" :locationUpdateInterval="locationUpdateInterval" :loading="loading.location"
          @load="loadLocationData" @clear="clearLocationTrace" @interval-change="onIntervalChange"
          @notify="showNotification" @update="updateLastUpdate" />
        <PostureTab v-if="currentTab==='posture'"
          :animals="animals" :postureFilter="postureFilter" :postureRecords="postureRecords" :loading="loading.posture"
          @load="loadPostureData" @analyze="analyzePosture" @notify="showNotification" @update="updateLastUpdate" />
        <StepsTab v-if="currentTab==='steps'"
          :stepStats="stepStats" :stepHourlyData="stepHourlyData" :loading="loading.steps"
          :devices="devicesForAnalysis" :simulatorRunning="simulatorRunning"
          @load="loadStepData" @refresh="refreshStepStats" @device-change="onStepsDeviceChange"
          @notify="showNotification" @update="updateLastUpdate" />
        <DataTab v-if="currentTab==='data'"
          :historyData="historyData" :dataFilter="dataFilter" :loading="loading.data"
          @filter="filterData" @export-all="exportAllData" @export-filtered="exportFilteredData"
          @notify="showNotification" @update="updateLastUpdate" />
        <AnalysisTab v-if="currentTab==='analysis'"
          :stats="stats" :analysis="analysis" :healthReport="healthReport" :loading="loading.analysis" :simulatorRunning="simulatorRunning"
          :devices="devicesForAnalysis"
          @load="loadBehaviorStatistics" @generate="generateHealthReport"
          @notify="showNotification" @update="updateLastUpdate" />
        <DeviceTab v-if="currentTab==='devices'" @notify="showNotification" />
        <AiAssistantTab v-if="currentTab==='ai'" />
      </main>
    </div>
    <div class="notification" :class="[notification.type, {show: notification.show}]">
      <i :class="notificationIcon"></i>
      <div class="notification-content">
        <div>{{ notification.title }}</div>
        <div>{{ notification.message }}</div>
      </div>
      <button class="notification-close" @click="closeNotification"><i class="fas fa-times"></i></button>
    </div>
  </div>
</template>

<script>
import OverviewTab from './components/tabs/OverviewTab.vue'
import AnimalsTab from './components/tabs/AnimalsTab.vue'
import LocationTab from './components/tabs/LocationTab.vue'
import PostureTab from './components/tabs/PostureTab.vue'
import StepsTab from './components/tabs/StepsTab.vue'
import DataTab from './components/tabs/DataTab.vue'
import AnalysisTab from './components/tabs/AnalysisTab.vue'
import DeviceTab from './components/tabs/DeviceTab.vue'
import AiAssistantTab from './components/tabs/AiAssistantTab.vue'
import { checkBackendStatus, loadOverviewFromBackend, loadAnimalData, loadBehaviorStatistics, loadStepDataFromBackend, loadHistoryDataFromBackend, fetchSimulatorStatus, startSimulator, stopSimulator } from './utils/api.js'
import { formatTime, convertToCSV, downloadFile, getTabName } from './utils/utils.js'
import { connectWebSocket, disconnectWebSocket } from './utils/websocket.js'

export default {
  name: 'App',
  components: { OverviewTab, AnimalsTab, LocationTab, PostureTab, StepsTab, DataTab, AnalysisTab, DeviceTab, AiAssistantTab },
  data() {
    return {
      currentTab: 'overview',
      isConnected: false,
      simulatorRunning: false,
      simulatorInfo: { intervalMs: 0, anomalyRate: 0, devices: [] },
      isSidebarCollapsed: false,
      connectionStatus: '\u672a\u8fde\u63a5',
      dataCount: 0,
      lastUpdate: '--:--:--',
      loading: { overview: false, animals: false, location: false, posture: false, steps: false, data: false, analysis: false },
      stats: { totalAnimals: 0, normal: 0, alert: 0, dataReceived: 0 },
      animals: [],
      dataStreamLogs: [],
      animalFilter: { animalId: '' },
      animalRecords: [],
      locationUpdateInterval: 5000,
      simulatorTimer: null,
      locationTimer: null,
      locationData: [],
      postureFilter: { animalId: '' },
      postureRecords: [],
      stepStats: { todaySteps: 0, walkingDistance: 0, activeTime: 0, stepFrequency: 0 },
      stepHourlyData: [],
      dataFilter: { startDate: '', endDate: '', dataType: '' },
      historyData: [],
      analysis: { healthAlerts: 0, postureAlerts: 0, stepAlerts: 0, locationAlerts: 0 },
      healthReport: { totalAnimals: 0, normalAnimals: 0, alertAnimals: 0 },
      notification: { show: false, type: 'info', title: '', message: '' },
      navItems: [
        { tab: 'overview', icon: 'fas fa-tachometer-alt', name: '\u7cfb\u7edf\u6982\u89c8' },
        { tab: 'animals', icon: 'fas fa-paw', name: '\u52a8\u7269\u76d1\u6d4b' },
        { tab: 'location', icon: 'fas fa-map-marker-alt', name: '\u5317\u6597\u5b9a\u4f4d' },
        { tab: 'posture', icon: 'fas fa-walking', name: '\u59ff\u6001\u8bc6\u522b' },
        { tab: 'steps', icon: 'fas fa-shoe-prints', name: '\u6b65\u6570\u7edf\u8ba1' },
        { tab: 'data', icon: 'fas fa-database', name: '\u6570\u636e\u7ba1\u7406' },
        { tab: 'analysis', icon: 'fas fa-chart-bar', name: '\u5206\u6790\u62a5\u544a' },
        { tab: 'devices', icon: 'fas fa-satellite-dish', name: '\u9879\u5708\u8bbe\u5907' },
        { tab: 'ai', icon: 'fas fa-robot', name: 'AI \u52a9\u624b' }
      ]
    }
  },
  computed: {
    notificationIcon() {
      const m = { success: 'fas fa-check-circle', error: 'fas fa-exclamation-circle', warning: 'fas fa-exclamation-triangle', info: 'fas fa-info-circle' }
      return m[this.notification.type] || m.info
    },
    devicesForAnalysis() {
      return this.animals.map(a => ({
        deviceId: a.id,
        animalType: a.type || a.animalType || ''
      }))
    }
  },
  methods: {
    switchTab(tab) { 
      this.currentTab = tab; 
      this.showNotification('info', '\u5207\u6362\u6210\u529f', `\u5df2\u5207\u6362\u5230${getTabName(tab)}\u6a21\u5757`)
      this.autoLoadTab(tab)
    },
    /**
     * 进入模块时自动拉取一次数据。
     *
     * 背景：「动物监测」「数据管理」原先必须在页内手动点「加载数据 / 筛选数据」
     * 才会看到内容，切进来只有空状态——这是漏做自动加载，并非有意设计。
     * 这里统一在切换页签时补一次**静默**加载（不弹"加载成功"提示，避免切页刷屏）；
     * 页内按钮保留，仍可用于手动刷新。
     */
    autoLoadTab(tab) {
      if (tab === 'animals') this.loadAnimalData({ silent: true })
      else if (tab === 'data') this.filterData({ silent: true })
      else if (tab === 'analysis') this.loadBehaviorStatistics()
    },
    async checkBackendStatus() { await checkBackendStatus(this) },
    async refreshOverview() {
      this.loading.overview = true
      try {
        if (this.isConnected) await loadOverviewFromBackend(this)
        else { this.showNotification('info', '\u7b49\u5f85\u6570\u636e', '\u540e\u7aef\u670d\u52a1\u672a\u8fde\u63a5\uff0c\u8bf7\u70b9\u51fb"\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668"\u6309\u94ae'); this.addLog('SYSTEM', '\u540e\u7aef\u672a\u8fde\u63a5\uff0c\u8bf7\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668') }
        this.updateLastUpdate()
      } catch(e) { this.showNotification('error', '\u5237\u65b0\u5931\u8d25', e.message) }
      finally { this.loading.overview = false }
    },
    async toggleSimulator() {
      if (this.simulatorRunning) await this.doStopSimulator()
      else await this.doStartSimulator()
    },
    async doStartSimulator() {
      this.loading.overview = true
      this.addLog('SYSTEM', '正在启动数据模拟器…')
      const res = await startSimulator(this)
      this.loading.overview = false
      const d = res && res.data
      if (!res || !res.ok || !d || !d.running) {
        const msg = (d && d.message) || '无法连接后端模拟器接口，请确认后端服务已启动'
        this.addLog('ERROR', '数据模拟器启动失败：' + msg)
        this.showNotification('error', '启动失败', msg)
        return
      }
      this.simulatorRunning = true
      this.simulatorInfo = d
      const devices = d.devices || []
      const unbound = devices.filter(x => !x.bound).map(x => String(x.code))
      this.addLog('SYSTEM', `数据模拟器已启动：${devices.length} 个项圈，每 ${Math.round((d.intervalMs || 5000) / 1000)} 秒上报一次`)
      if (unbound.length) {
        this.addLog('ERROR', `项圈 ${unbound.join('、')} 未绑定动物，其数据会被链路丢弃，请先在「项圈设备」页完成绑定`)
      }
      this.showNotification('success', '数据模拟器已启动',
        unbound.length ? `注意：项圈 ${unbound.join('、')} 未绑定动物` : '数据将走完整链路：Java → Python 推理 → 入库 → 实时推送')
      this.updateLastUpdate()
    },
    async doStopSimulator() {
      const res = await stopSimulator(this)
      this.simulatorRunning = false
      this.simulatorInfo = { intervalMs: 0, anomalyRate: 0, devices: [] }
      this.addLog('SIM', '数据模拟器已停止')
      this.showNotification('info', '模拟器已停止',
        (res && res.data && res.data.message) || '已入库的数据保留在数据库中')
      this.refreshOverview()
    },
    async refreshSimulatorStatus() {
      const d = await fetchSimulatorStatus(this)
      if (d) {
        this.simulatorRunning = !!d.running
        this.simulatorInfo = d
      }
    },
    async loadAnimalData(opts) { await loadAnimalData(this, opts) },
    exportAnimalData() {
      if (!this.animalRecords.length) { this.showNotification('warning', '\u65e0\u6570\u636e', '\u6ca1\u6709\u53ef\u5bfc\u51fa\u7684\u6570\u636e'); return }
      downloadFile(convertToCSV(this.animalRecords), 'animal_data.csv', 'text/csv')
      this.showNotification('success', '\u5bfc\u51fa\u6210\u529f', '\u52a8\u7269\u76d1\u6d4b\u6570\u636e\u5df2\u5bfc\u51fa')
    },
    async loadLocationData() {
      this.loading.location = true
      try {
        if (!this.locationData.length) this.showNotification('info', '\u65e0\u4f4d\u7f6e\u6570\u636e', '\u8bf7\u5148\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668')
        else this.showNotification('success', '\u52a0\u8f7d\u6210\u529f', `\u5df2\u52a0\u8f7d${this.locationData.length}\u6761\u4f4d\u7f6e\u6570\u636e`)
        this.updateLastUpdate()
      } finally { this.loading.location = false }
    },
    clearLocationTrace() { this.locationData = []; this.showNotification('info', '\u6e05\u9664\u6210\u529f', '\u4f4d\u7f6e\u8f68\u8ff9\u5df2\u6e05\u9664') },
    onIntervalChange(value) {
      if (typeof value === 'number' && value > 0) this.locationUpdateInterval = value
      if (this.locationTimer) clearInterval(this.locationTimer)
      this.locationTimer = setInterval(() => this.loadLocationData(), this.locationUpdateInterval)
      this.showNotification('info', '\u8bbe\u7f6e\u6210\u529f', `\u66f4\u65b0\u9891\u7387\u5df2\u8bbe\u7f6e\u4e3a ${this.locationUpdateInterval/1000} \u79d2`)
    },
    async loadPostureData() {
      this.loading.posture = true
      try {
        if (!this.postureRecords.length) this.showNotification('info', '\u65e0\u59ff\u6001\u6570\u636e', '\u8bf7\u5148\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668')
        else this.showNotification('success', '\u52a0\u8f7d\u6210\u529f', '\u59ff\u6001\u6570\u636e\u5df2\u52a0\u8f7d')
        this.updateLastUpdate()
      } finally { this.loading.posture = false }
    },
    analyzePosture() { this.showNotification('success', '\u5206\u6790\u5b8c\u6210', '\u59ff\u6001\u6570\u636e\u5206\u6790\u5b8c\u6210') },
    async loadStepData() {
      await loadStepDataFromBackend(this)
    },
    async onStepsDeviceChange(deviceId, range) {
      await loadStepDataFromBackend(this, deviceId, range)
    },
    refreshStepStats() {
      if (this.animals.length > 0) this.loadStepData()
      else this.showNotification('warning', '\u65e0\u52a8\u7269\u6570\u636e', '\u8bf7\u5148\u52a0\u8f7d\u6982\u89c8\u6570\u636e')
    },
    async filterData(opts) {
      await loadHistoryDataFromBackend(this, opts)
    },
    exportAllData() {
      if (!this.historyData.length) { this.showNotification('warning', '\u65e0\u6570\u636e', '\u6ca1\u6709\u53ef\u5bfc\u51fa\u7684\u6570\u636e'); return }
      downloadFile(convertToCSV(this.historyData), 'all_data.csv', 'text/csv')
      this.showNotification('success', '\u5bfc\u51fa\u6210\u529f', '\u5168\u90e8\u6570\u636e\u5df2\u5bfc\u51fa')
    },
    exportFilteredData() {
      if (!this.historyData.length) { this.showNotification('warning', '\u65e0\u6570\u636e', '\u6ca1\u6709\u53ef\u5bfc\u51fa\u7684\u6570\u636e'); return }
      downloadFile(convertToCSV(this.historyData), 'filtered_data.csv', 'text/csv')
      this.showNotification('success', '\u5bfc\u51fa\u6210\u529f', '\u7b5b\u9009\u7ed3\u679c\u5df2\u5bfc\u51fa')
    },
    async loadBehaviorStatistics() { await loadBehaviorStatistics(this) },
    generateHealthReport() {
      if (!this.healthReport.totalAnimals) { this.showNotification('warning', '\u65e0\u6570\u636e', '\u8bf7\u5148\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668'); return }
      this.showNotification('success', '\u751f\u6210\u6210\u529f', '\u5065\u5eb7\u62a5\u544a\u5df2\u751f\u6210')
      this.addLog('REPORT', '\u5065\u5eb7\u62a5\u544a\u751f\u6210\u5b8c\u6210')
    },
    addLog(source, message) { this.dataStreamLogs.unshift({ id: Date.now()+Math.random(), time: formatTime(new Date()), source, message }); if (this.dataStreamLogs.length > 100) this.dataStreamLogs = this.dataStreamLogs.slice(0, 100) },
    updateLastUpdate() { this.lastUpdate = formatTime(new Date()) },
    showNotification(type, title, message) { this.notification = { show: true, type, title, message }; setTimeout(() => { this.notification.show = false }, 3000) },
    closeNotification() { this.notification.show = false },
    toggleSidebar() { this.isSidebarCollapsed = !this.isSidebarCollapsed },
    refreshAllData() { if (!this.stats.totalAnimals) { this.showNotification('warning', '\u65e0\u6570\u636e', '\u8bf7\u5148\u542f\u52a8\u6570\u636e\u6a21\u62df\u5668'); return } this.refreshOverview(); this.showNotification('success', '\u5237\u65b0\u6210\u529f', '\u6240\u6709\u6570\u636e\u5df2\u5237\u65b0') }
  },
  mounted() {
    this.checkBackendStatus()
    connectWebSocket(this)
    this.refreshOverview()
    // 同步后端数据模拟器的运行状态（刷新页面后按钮状态不丢），并定期轮询保持同步
    this.refreshSimulatorStatus()
    this.simulatorTimer = setInterval(() => this.refreshSimulatorStatus(), 8000)
    this.addLog('SYSTEM', '\u7cfb\u7edf\u542f\u52a8 - Vue 2.7 + Vite')
    this.addLog('SYSTEM', 'Vue \u5e94\u7528\u521d\u59cb\u5316\u5b8c\u6210')
  },
  beforeDestroy() {
    if (this.locationTimer) clearInterval(this.locationTimer)
    if (this.simulatorTimer) clearInterval(this.simulatorTimer)
    disconnectWebSocket()
  }
}
</script>
