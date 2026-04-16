// ========================================
// 云牧智感 - 主应用入口
// 模块化版本 v4.0
//
// 加载顺序（必须按此顺序）:
// 1. config.js    - API 配置
// 2. utils.js     - 工具函数
// 3. api.js       - 后端 API 调用
// 4. mock.js      - 模拟数据生成
// 5. location.js  - 北斗定位与地图
// 6. posture.js   - 姿态识别图表
// 7. steps.js     - 步数统计图表
// 8. charts.js    - 分析报告图表
// ========================================

// ========================================
// Vue 应用根实例
// ========================================
new Vue({
  el: '#app',

  // ==================== 数据定义 ====================
  data: {
    // 当前标签页
    currentTab: 'overview',

    // 连接状态
    isConnected: false,
    connectionStatus: '未连接',
    dataCount: 0,
    lastUpdate: '--:--:--',

    // 加载状态
    loading: {
      overview: false,
      animals: false,
      location: false,
      posture: false,
      steps: false,
      data: false,
      analysis: false
    },

    // 统计数据
    stats: {
      totalAnimals: 0,
      normal: 0,
      alert: 0,
      dataReceived: 0
    },

    // 动物列表
    animals: [],

    // 数据流日志
    dataStreamLogs: [],

    // 动物监测数据
    animalFilter: { animalId: '' },
    animalRecords: [],

    // 北斗定位数据
    locationUpdateInterval: 5000,
    locationTimer: null,
    locationData: [],
    locationTrace: [],
    leafletMap: null,
    leafletMarkers: [],
    mapLoaded: false,
    polyline: null,

    // 姿态识别数据
    postureFilter: { animalId: '' },
    postureRecords: [],
    posturePieChart: null,
    postureBarChart: null,

    // 步数统计数据
    stepStats: {
      todaySteps: 0,
      walkingDistance: 0,
      activeTime: 0,
      stepFrequency: 0
    },
    stepTrendChart: null,
    activityPieChart: null,
    stepHourlyData: [],

    // 数据管理
    dataFilter: {
      startDate: '',
      endDate: '',
      dataType: ''
    },
    historyData: [],

    // 分析报告
    analysis: {
      healthAlerts: 0,
      postureAlerts: 0,
      stepAlerts: 0,
      locationAlerts: 0
    },
    healthReport: {
      totalAnimals: 0,
      normalAnimals: 0,
      alertAnimals: 0
    },
    trendChart: null,

    // 通知
    notification: {
      show: false,
      type: 'info',
      title: '',
      message: ''
    }
  },

  // ==================== 计算属性 ====================
  computed: {
    notificationIcon() {
      const icons = {
        success: 'fa-check-circle',
        error: 'fa-exclamation-circle',
        warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle'
      };
      return icons[this.notification.type] || icons.info;
    }
  },

  // ==================== 方法（代理到各模块） ====================
  methods: {

    // ==================== 标签切换 ====================
    switchTab(tab) {
      this.currentTab = tab;
      this.showNotification('info', '切换成功', `已切换到${getTabName(tab)}模块`);
      switch (tab) {
        case 'overview':   this.refreshOverview();       break;
        case 'animals':    this.loadAnimalData();        break;
        case 'location':
          this.$nextTick(() => {
            if (this.leafletMap) {
              this.leafletMap.invalidateSize();
            } else {
              initMap(this);
            }
            this.loadLocationData();
          });
          break;
        case 'posture':
          this.loadPostureData();
          this.$nextTick(() => { this.renderPostureCharts(); });
          break;
        case 'steps':
          this.loadStepData();
          this.$nextTick(() => { this.renderStepCharts(); });
          break;
        case 'data':        this.filterData();           break;
        case 'analysis':
          this.loadBehaviorStatistics();
          this.$nextTick(() => { this.renderTrendChart(); });
          break;
      }
    },

    // ==================== 后端连接 ====================
    async checkBackendStatus() {
      await checkBackendStatus(this);
    },

    // ==================== 系统概览 ====================
    async refreshOverview() {
      this.loading.overview = true;
      try {
        if (this.isConnected) {
          await loadOverviewFromBackend(this);
        } else {
          this.showNotification('info', '等待数据', '后端服务未连接，请点击"生成模拟数据"按钮');
          this.addLog('SYSTEM', '后端未连接，请生成模拟数据');
        }
        this.updateLastUpdate();
      } catch (error) {
        this.showNotification('error', '刷新失败', error.message);
      } finally {
        this.loading.overview = false;
      }
    },

    // ==================== 模拟数据 ====================
    generateMockData() {
      generateMockData(this);
    },

    // ==================== 动物监测 ====================
    async loadAnimalData() {
      await loadAnimalData(this);
    },

    exportAnimalData() {
      if (this.animalRecords.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      downloadFile(convertToCSV(this.animalRecords), 'animal_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '动物监测数据已导出');
    },

    // ==================== 北斗定位 ====================
    initMap() {
      initMap(this);
    },

    updateMapMarkers() {
      updateMapMarkers(this);
    },

    clearLocationTrace() {
      clearLocationTrace(this);
    },

    async loadLocationData() {
      await loadLocationData(this);
    },

    onIntervalChange() {
      if (this.locationTimer) clearInterval(this.locationTimer);
      this.locationTimer = setInterval(() => { this.loadLocationData(); }, this.locationUpdateInterval);
      this.showNotification('info', '设置成功', `更新频率已设置为 ${this.locationUpdateInterval / 1000} 秒`);
    },

    startLocationAutoRefresh() {
      if (this.locationTimer) clearInterval(this.locationTimer);
      this.locationTimer = setInterval(() => { this.loadLocationData(); }, this.locationUpdateInterval);
    },

    initMapWithRetry(retryCount = 0) {
      initMapWithRetry(this, retryCount);
    },

    handleResize() {
      handleMapResize(this);
      this.posturePieChart?.resize();
      this.postureBarChart?.resize();
      this.stepTrendChart?.resize();
      this.activityPieChart?.resize();
      this.trendChart?.resize();
    },

    // ==================== 姿态识别 ====================
    async loadPostureData() {
      await loadPostureData(this);
    },

    analyzePosture() {
      analyzePosture(this);
    },

    renderPostureCharts() {
      renderPostureCharts(this);
    },

    // ==================== 步数统计 ====================
    async loadStepData() {
      await loadStepData(this);
    },

    refreshStepStats() {
      refreshStepStats(this);
    },

    renderStepCharts() {
      renderStepCharts(this);
    },

    // ==================== 数据管理 ====================
    async filterData() {
      this.loading.data = true;
      try {
        if (this.historyData.length === 0) {
          this.showNotification('info', '无历史数据', '请先生成模拟数据');
        } else {
          this.showNotification('success', '筛选成功', `找到${this.historyData.length}条记录`);
        }
      } catch (error) {
        this.showNotification('error', '筛选失败', error.message);
      } finally {
        this.loading.data = false;
      }
    },

    exportAllData() {
      if (this.historyData.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      downloadFile(convertToCSV(this.historyData), 'all_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '全部数据已导出');
    },

    exportFilteredData() {
      if (this.historyData.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      downloadFile(convertToCSV(this.historyData), 'filtered_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '筛选结果已导出');
    },

    // ==================== 分析报告 ====================
    async loadBehaviorStatistics() {
      await loadBehaviorStatistics(this);
    },

    generateHealthReport() {
      generateHealthReport(this);
    },

    renderTrendChart() {
      renderTrendChart(this);
    },

    // ==================== 工具函数 ====================
    addLog(source, message) {
      this.dataStreamLogs.unshift({
        id: Date.now() + Math.random(),
        time: formatTime(new Date()),
        source,
        message
      });
      if (this.dataStreamLogs.length > 100) {
        this.dataStreamLogs = this.dataStreamLogs.slice(0, 100);
      }
    },

    refreshDataStream() {
      this.addLog('SYSTEM', '数据流刷新');
      this.showNotification('success', '刷新成功', '数据流已刷新');
    },

    clearDataStream() {
      this.dataStreamLogs = [];
      this.addLog('SYSTEM', '数据流已清空');
      this.showNotification('info', '清空成功', '数据流已清空');
    },

    updateLastUpdate() {
      this.lastUpdate = formatTime(new Date());
    },

    showNotification(type, title, message) {
      this.notification = { show: true, type, title, message };
      setTimeout(() => { this.notification.show = false; }, 3000);
    },

    closeNotification() {
      this.notification.show = false;
    },

    refreshAllData() {
      if (this.stats.totalAnimals === 0) {
        this.showNotification('warning', '无数据', '请先生成模拟数据');
        return;
      }
      this.refreshOverview();
      this.showNotification('success', '刷新成功', '所有数据已刷新');
    }
  },

  // ==================== 生命周期钩子 ====================
  mounted() {
    this.checkBackendStatus();
    this.refreshOverview();
    this.addLog('SYSTEM', '系统启动');
    this.addLog('SYSTEM', 'Vue 应用初始化完成');
    this.initMapWithRetry();
    this.startLocationAutoRefresh();
    window.addEventListener('resize', this.handleResize);
  },

  beforeDestroy() {
    if (this.locationTimer) clearInterval(this.locationTimer);
    if (this.leafletMap) {
      this.leafletMarkers.forEach(m => m.remove());
      this.leafletMarkers = [];
      this.leafletMap.remove();
    }
    if (this.posturePieChart) this.posturePieChart.dispose();
    if (this.postureBarChart) this.postureBarChart.dispose();
    if (this.stepTrendChart) this.stepTrendChart.dispose();
    if (this.activityPieChart) this.activityPieChart.dispose();
    if (this.trendChart) this.trendChart.dispose();
    window.removeEventListener('resize', this.handleResize);
  }
});

// ========================================
// 全局函数（供 HTML onclick 调用）
// ========================================
function checkBackendStatus() {
  const app = document.querySelector('#app').__vue__;
  if (app) app.checkBackendStatus();
}

function generateMockData() {
  const app = document.querySelector('#app').__vue__;
  if (app) app.generateMockData();
}

function refreshAllData() {
  const app = document.querySelector('#app').__vue__;
  if (app) app.refreshAllData();
}
