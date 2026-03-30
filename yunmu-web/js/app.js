// ========================================
// 云牧智感 - 高原牛羊行为监测系统
// 前端应用 - app.js
// 版本：v3.0 - 使用 Leaflet 开源地图
// ========================================

// ==================== 配置常量 ====================
const API_CONFIG = {
  // Java Spring Boot 后端 - 主 API 服务
  BASE_URL: 'http://localhost:8080/api',
  // Python Flask 后端 - 机器学习服务
  ML_SERVICE_URL: 'http://localhost:5000/api'
};

// ==================== Vue 实例 ====================
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
    animalFilter: {
      animalId: ''
    },
    animalRecords: [],

    // 北斗定位数据
    locationUpdateInterval: 5000,
    locationTimer: null,
    locationData: [],
    locationTrace: [],

    // Leaflet 地图
    leafletMap: null,
    leafletMarkers: [],
    mapLoaded: false,
    polyline: null,

    // 姿态识别数据
    postureFilter: {
      animalId: ''
    },
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

  // ==================== 方法定义 ====================
  methods: {
    // ==================== 标签切换 ====================
    resizeCurrentCharts() {
      // 根据当前标签页重新渲染对应的图表
      switch (this.currentTab) {
        case 'posture':
          this.$nextTick(() => {
            if (this.posturePieChart) {
              this.posturePieChart.resize();
              // 强制重新设置数据，确保显示
              this.renderPostureCharts();
            }
            if (this.postureBarChart) {
              this.postureBarChart.resize();
            }
          });
          break;
        case 'steps':
          this.$nextTick(() => {
            if (this.stepTrendChart) {
              this.stepTrendChart.resize();
              this.renderStepCharts();
            }
            if (this.activityPieChart) {
              this.activityPieChart.resize();
            }
          });
          break;
        case 'analysis':
          this.$nextTick(() => {
            if (this.trendChart) {
              this.trendChart.resize();
              this.renderTrendChart();
            }
          });
          break;
      }
    },
    switchTab(tab) {
      this.currentTab = tab;
      this.showNotification('info', '切换成功', `已切换到${this.getTabName(tab)}模块`);

      // 切换到对应标签时加载数据
      switch (tab) {
        case 'overview':
          this.refreshOverview();
          break;
        case 'animals':
          this.loadAnimalData();
          break;
        case 'location':
          this.$nextTick(() => {
            if (this.leafletMap) {
              this.leafletMap.invalidateSize();
            } else {
              this.initMap();
            }
            this.loadLocationData();
          });
          break;
        case 'posture':
          this.loadPostureData();
          this.$nextTick(() => {
            this.renderPostureCharts();
          });
          break;
        case 'steps':
          this.loadStepData();
          this.$nextTick(() => {
            this.renderStepCharts();
          });
          break;
        case 'data':
          this.filterData();
          break;
        case 'analysis':
          this.loadBehaviorStatistics();
          this.$nextTick(() => {
            this.renderTrendChart();
          });
          break;
      }
    },


    getTabName(tab) {
      const names = {
        overview: '系统概览',
        animals: '动物监测',
        location: '北斗定位',
        posture: '姿态识别',
        steps: '步数统计',
        data: '数据管理',
        analysis: '分析报告'
      };
      return names[tab] || tab;
    },

    // ==================== 后端连接检查 ====================
    async checkBackendStatus() {
      this.loading.overview = true;
      try {
        // 检查 Java 后端连接
        const response = await fetch(`${API_CONFIG.BASE_URL}/data/health`, {
          method: 'GET',
          headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
          this.isConnected = true;
          this.connectionStatus = '已连接';
          this.showNotification('success', '连接成功', '后端服务连接正常');
          this.addLog('SYSTEM', '后端连接检查成功 - Java 服务');
        } else {
          throw new Error('连接失败');
        }
      } catch (error) {
        // Java 后端不可用，尝试 Python 后端
        try {
          const mlResponse = await fetch(`${API_CONFIG.ML_SERVICE_URL}/health`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
          });

          if (mlResponse.ok) {
            this.isConnected = true;
            this.connectionStatus = '已连接 (ML 服务)';
            this.showNotification('success', '连接成功', 'Python ML 服务连接正常');
            this.addLog('SYSTEM', '后端连接检查成功 - Python ML 服务');
          } else {
            throw new Error('连接失败');
          }
        } catch (mlError) {
          this.isConnected = false;
          this.connectionStatus = '未连接';
          this.showNotification('warning', '连接失败', '后端服务未启动或不可用');
          this.addLog('SYSTEM', '后端连接失败');
          // 注意：不再自动生成模拟数据
        }
      } finally {
        this.loading.overview = false;
      }
    },

    // ==================== 数据流日志 ====================
    addLog(source, message) {
      const log = {
        id: Date.now() + Math.random(),
        time: this.formatTime(new Date()),
        source: source,
        message: message
      };
      this.dataStreamLogs.unshift(log);

      // 限制日志数量
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

    // ==================== 系统概览 ====================
    async refreshOverview() {
      this.loading.overview = true;
      try {
        if (this.isConnected) {
          await this.loadOverviewFromBackend();
        } else {
          // 后端未连接时，不清空数据，只显示提示
          this.showNotification('info', '等待数据', '后端服务未连接，请点击"生成模拟数据"按钮');
          this.addLog('SYSTEM', '后端未连接，请生成模拟数据');
        }
        this.updateLastUpdate();
      } catch (error) {
        console.error('刷新概览失败:', error);
        this.showNotification('error', '刷新失败', error.message);
      } finally {
        this.loading.overview = false;
      }
    },

    async loadOverviewFromBackend() {
      try {
        // 从 Java 后端加载统计数据
        const [healthRes, stepsRes] = await Promise.all([
          fetch(`${API_CONFIG.BASE_URL}/health/assessment`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
          }).catch(() => null),
          fetch(`${API_CONFIG.BASE_URL}/steps/today/all`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
          }).catch(() => null)
        ]);

        // 处理统计数据
        if (stepsRes && stepsRes.ok) {
          const stepsData = await stepsRes.json();
          if (stepsData.success) {
            const animalIds = Object.keys(stepsData.data || {});
            this.stats.totalAnimals = animalIds.length;
            this.stats.dataReceived = Object.values(stepsData.data || {}).reduce((a, b) => a + b, 0);
          }
        }

        // 生成动物列表（从后端或模拟）
        this.animals = await this.loadAnimalsList();

        // 统计正常和异常数量
        this.stats.normal = this.animals.filter(a => a.status === 'normal').length;
        this.stats.alert = this.animals.filter(a => a.status === 'alert').length;

        this.dataCount = this.stats.dataReceived;
      } catch (error) {
        console.error('加载概览数据失败:', error);
        // 不自动生成模拟数据，只记录错误
        this.addLog('ERROR', `加载概览数据失败: ${error.message}`);
      }
    },

    async loadAnimalsList() {
      // 从后端获取动物列表
      try {
        // 尝试从后端获取动物列表
        const response = await fetch(`${API_CONFIG.BASE_URL}/animals`, {
          method: 'GET',
          headers: { 'Content-Type': 'application/json' }
        }).catch(() => null);

        if (response && response.ok) {
          const data = await response.json();
          if (data && data.length) {
            return data;
          }
        }

        // 后端无数据时返回空数组，不再自动生成模拟数据
        this.addLog('SYSTEM', '后端无动物数据，请生成模拟数据');
        return [];
      } catch (error) {
        console.error('加载动物列表失败:', error);
        return [];
      }
    },

    // ==================== 生成模拟数据 ====================
    generateMockData() {
      // 生成模拟统计数据
      const totalAnimals = Math.floor(Math.random() * 20) + 10;
      const alertCount = Math.floor(Math.random() * 3);
      this.stats = {
        totalAnimals: totalAnimals,
        normal: totalAnimals - alertCount,
        alert: alertCount,
        dataReceived: Math.floor(Math.random() * 1000) + 500
      };

      const behaviors = ['采食', '反刍', '站立', '行走', '躺卧'];
      const animalTypes = ['cow', 'sheep'];
      this.animals = [];

      for (let i = 1; i <= Math.min(totalAnimals, 12); i++) {
        const id = `NO.${String(i).padStart(3, '0')}`;
        const isAlert = i <= alertCount;
        this.animals.push({
          id: id,
          type: animalTypes[Math.floor(Math.random() * animalTypes.length)],
          status: isAlert ? 'alert' : 'normal',
          temperature: (37 + Math.random() * 3).toFixed(1),
          heartRate: Math.floor(50 + Math.random() * 50),
          steps: Math.floor(Math.random() * 5000),
          behavior: behaviors[Math.floor(Math.random() * behaviors.length)]
        });
      }

      this.dataCount = this.stats.dataReceived;

      // 同时生成其他模块的模拟数据，确保各模块都有数据显示
      this.animalRecords = this.generateMockAnimalRecords();
      this.postureRecords = this.generateMockPostureRecords();
      this.stepStats = this.generateMockStepStats();
      this.stepHourlyData = this.generateMockHourlyData();
      this.historyData = this.generateMockHistoryData();
      this.locationData = this.generateMockLocationData();

      // 更新分析报告数据
      this.analysis = {
        healthAlerts: Math.floor(Math.random() * 5),
        postureAlerts: Math.floor(Math.random() * 8),
        stepAlerts: Math.floor(Math.random() * 6),
        locationAlerts: Math.floor(Math.random() * 3)
      };
      this.healthReport = {
        totalAnimals: this.stats.totalAnimals,
        normalAnimals: this.stats.normal,
        alertAnimals: this.stats.alert
      };

      // 重新渲染图表
      this.renderPostureCharts();
      this.renderStepCharts();
      this.renderTrendChart();

      // 更新地图标记
      if (this.mapLoaded && this.leafletMap) {
        this.updateMapMarkers();
      }

      this.updateLastUpdate();
      this.addLog('MOCK', `生成${totalAnimals}只动物的模拟监测数据`);
      this.showNotification('success', '生成成功', `已生成${totalAnimals}只动物的模拟监测数据`);
    },

    // ==================== 动物监测 ====================
    async loadAnimalData() {
      this.loading.animals = true;
      try {
        if (this.isConnected && this.animalFilter.animalId) {
          // 从 Java 后端加载行为数据
          const now = new Date();
          const startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000);
          const url = `${API_CONFIG.BASE_URL}/behavior/statistics/${encodeURIComponent(this.animalFilter.animalId)}?startTime=${this.formatISO(startTime)}&endTime=${this.formatISO(now)}`;
          const response = await fetch(url);
          if (response.ok) {
            const data = await response.json();
            this.animalRecords = this.convertBehaviorStatsToRecords(data);
          } else {
            this.animalRecords = [];
          }
        } else {
          // 如果已有模拟数据则保留，否则显示提示
          if (this.animalRecords.length === 0) {
            this.showNotification('info', '无数据', '请先点击"生成模拟数据"按钮');
          }
        }
        this.updateLastUpdate();
        if (this.animalRecords.length > 0) {
          this.showNotification('success', '加载成功', `已加载${this.animalRecords.length}条记录`);
        }
      } catch (error) {
        console.error('加载动物数据失败:', error);
        this.showNotification('error', '加载失败', error.message);
      } finally {
        this.loading.animals = false;
      }
    },

    generateMockAnimalRecords() {
      const behaviors = ['采食', '反刍', '站立', '行走', '躺卧'];
      const records = [];
      const now = new Date();

      for (let i = 0; i < 20; i++) {
        const time = new Date(now.getTime() - i * 30 * 60 * 1000);
        const isAlert = Math.random() < 0.1;
        records.push({
          time: this.formatTime(time),
          animalId: this.animalFilter.animalId || this.animals[Math.floor(Math.random() * this.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
          behavior: behaviors[Math.floor(Math.random() * behaviors.length)],
          temperature: (37 + Math.random() * 3).toFixed(1),
          heartRate: Math.floor(50 + Math.random() * 50),
          status: isAlert ? '异常' : '正常'
        });
      }

      return records;
    },

    convertBehaviorStatsToRecords(stats) {
      const records = [];
      if (stats && stats.behaviors) {
        Object.entries(stats.behaviors).forEach(([behavior, data]) => {
          records.push({
            time: this.formatTime(new Date()),
            animalId: stats.animalId || 'unknown',
            behavior: behavior,
            temperature: (37 + Math.random() * 2).toFixed(1),
            heartRate: Math.floor(50 + Math.random() * 40),
            status: data.confidence > 0.8 ? '正常' : '异常'
          });
        });
      }
      return records;
    },

    exportAnimalData() {
      if (this.animalRecords.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      const csv = this.convertToCSV(this.animalRecords);
      this.downloadFile(csv, 'animal_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '动物监测数据已导出');
    },

    // ==================== 北斗定位 - Leaflet 地图 ====================

    initMap() {
      try {
        const mapContainer = document.getElementById('locationMap');
        if (!mapContainer) {
          console.error('地图容器不存在');
          setTimeout(() => this.initMap(), 500);
          return;
        }

        // 确保容器有正确尺寸
        const parent = mapContainer.parentElement;
        if (parent && parent.clientHeight > 0) {
          mapContainer.style.height = parent.clientHeight + 'px';
        }

        if (mapContainer.clientHeight === 0) {
          mapContainer.style.height = '500px';
        }

        console.log('地图容器尺寸:', mapContainer.clientWidth, 'x', mapContainer.clientHeight);

        // 创建地图实例
        this.leafletMap = L.map('locationMap').setView([30.0, 90.0], 6);

        // 强制刷新地图容器尺寸
        setTimeout(() => {
          if (this.leafletMap) {
            this.leafletMap.invalidateSize();
          }
        }, 100);

        // 使用稳定的瓦片源 - 改用更可靠的源
        const tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
          maxZoom: 19,
          minZoom: 3
        });

        tileLayer.addTo(this.leafletMap);

        // 添加比例尺
        L.control.scale({ metric: true, imperial: false }).addTo(this.leafletMap);

        this.mapLoaded = true;

        this.addLog('MAP', 'Leaflet 地图初始化成功');

        // 加载位置数据
        this.loadLocationData();

      } catch (error) {
        console.error('Leaflet 地图初始化失败:', error);
        this.mapLoaded = false;
        this.addLog('MAP', `地图初始化失败: ${error.message}`);
      }
    },

    updateMapMarkers() {
      if (!this.leafletMap || !this.mapLoaded) return;

      // 清除旧标记
      this.leafletMarkers.forEach(marker => marker.remove());
      this.leafletMarkers = [];

      if (!this.locationData || this.locationData.length === 0) return;

      console.log('更新地图标记，数量:', this.locationData.length);

      // 创建新标记
      this.locationData.forEach(loc => {
        const animal = this.animals.find(a => a.id === loc.id);
        const isAlert = animal?.status === 'alert';
        const markerColor = isAlert ? '#e74c3c' : '#3498db';

        // 创建自定义 HTML 图标
        const iconHtml = `
          <div style="
            background: ${markerColor};
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: 3px solid white;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
            cursor: pointer;
            transition: transform 0.2s;
          ">
            ${animal?.type === 'cow' ? '🐄' : '🐑'}
          </div>
        `;

        const customIcon = L.divIcon({
          html: iconHtml,
          iconSize: [46, 46],
          className: 'custom-marker',
          popupAnchor: [0, -23]
        });

        // 创建标记
        const marker = L.marker([loc.latitude, loc.longitude], {
          icon: customIcon
        });

        // 创建弹窗内容
        const popupContent = `
          <div style="padding: 8px; min-width: 200px;">
            <h4 style="margin: 0 0 10px 0; color: ${markerColor};">
              ${animal?.type === 'cow' ? '🐄' : '🐑'} ${loc.id}
            </h4>
            <p style="margin: 5px 0;">
              <strong>📍 位置:</strong><br>
              ${loc.latitude.toFixed(6)}°N, ${loc.longitude.toFixed(6)}°E
            </p>
            <p style="margin: 5px 0;">
              <strong>💚 状态:</strong> 
              <span style="color: ${markerColor}; font-weight: bold;">
                ${isAlert ? '⚠️ 异常' : '✅ 正常'}
              </span>
            </p>
            <p style="margin: 5px 0;">
              <strong>🌡️ 体温:</strong> ${animal?.temperature || '38.5'}°C
            </p>
            <p style="margin: 5px 0;">
              <strong>💓 心率:</strong> ${animal?.heartRate || '65'} bpm
            </p>
            <p style="margin: 5px 0;">
              <strong>👣 步数:</strong> ${animal?.steps || '0'}
            </p>
            <p style="margin: 5px 0;">
              <strong>🕐 更新时间:</strong><br>
              ${loc.timestamp || this.lastUpdate}
            </p>
          </div>
        `;

        marker.bindPopup(popupContent);

        // 添加悬停效果
        marker.on('mouseover', function () {
          this.openPopup();
        });

        marker.addTo(this.leafletMap);
        this.leafletMarkers.push(marker);
      });

      // 如果有多个标记，自动调整地图视野
      if (this.leafletMarkers.length > 0) {
        const group = L.featureGroup(this.leafletMarkers);
        this.leafletMap.fitBounds(group.getBounds().pad(0.2));
      }

      this.addLog('MAP', `已更新 ${this.leafletMarkers.length} 个动物标记`);
    },

    clearLocationTrace() {
      this.locationTrace = [];
      if (this.polyline && this.leafletMap) {
        this.polyline.remove();
        this.polyline = null;
      }
      this.showNotification('success', '清除成功', '轨迹已清除');
      this.addLog('MAP', '轨迹已清除');
    },

    async loadLocationData() {
      this.loading.location = true;
      try {
        // 如果已有位置数据则保留，否则显示提示
        if (this.locationData.length === 0) {
          this.showNotification('info', '无位置数据', '请先生成模拟数据');
        }
        this.updateLastUpdate();

        // 更新地图标记
        if (this.mapLoaded && this.leafletMap && this.locationData.length > 0) {
          this.updateMapMarkers();
        }

        if (this.locationData.length > 0) {
          this.showNotification('success', '加载成功', `已加载 ${this.locationData.length} 个位置数据`);
          this.addLog('LOCATION', `位置数据已更新，共 ${this.locationData.length} 个点位`);
        }
      } catch (error) {
        console.error('加载位置数据失败:', error);
        this.showNotification('error', '加载失败', error.message);
      } finally {
        this.loading.location = false;
      }
    },

    generateMockLocationData() {
      const data = [];
      // 模拟西藏高原地区的坐标范围（真实的高原牧场区域）
      const latRange = [29.0, 31.5];  // 西藏地区纬度范围
      const lngRange = [88.0, 92.0];  // 西藏地区经度范围

      for (let i = 0; i < this.animals.length; i++) {
        data.push({
          id: this.animals[i].id,
          latitude: latRange[0] + Math.random() * (latRange[1] - latRange[0]),
          longitude: lngRange[0] + Math.random() * (lngRange[1] - lngRange[0]),
          timestamp: this.formatTime(new Date())
        });
      }
      return data;
    },

    onIntervalChange() {
      if (this.locationTimer) {
        clearInterval(this.locationTimer);
      }
      this.locationTimer = setInterval(() => {
        this.loadLocationData();
      }, this.locationUpdateInterval);
      this.showNotification('info', '设置成功', `更新频率已设置为 ${this.locationUpdateInterval / 1000} 秒`);
    },

    startLocationAutoRefresh() {
      if (this.locationTimer) {
        clearInterval(this.locationTimer);
      }
      this.locationTimer = setInterval(() => {
        this.loadLocationData();
      }, this.locationUpdateInterval);
    },

    initMapWithRetry(retryCount = 0) {
      const maxRetries = 20;

      this.$nextTick(() => {
        const mapContainer = document.getElementById('locationMap');
        const mapWrapper = document.querySelector('.map-container');

        if (!mapContainer || !mapWrapper) {
          console.warn('地图容器不存在，等待渲染...', retryCount);
          if (retryCount < maxRetries) {
            setTimeout(() => this.initMapWithRetry(retryCount + 1), 300);
          }
          return;
        }

        // 确保容器可见且有高度
        const ensureHeight = () => {
          if (mapWrapper.clientHeight === 0) {
            mapWrapper.style.height = '500px';
          }
          if (mapContainer.clientHeight === 0) {
            mapContainer.style.height = '100%';
            mapContainer.style.minHeight = '500px';
          }
          // 确保容器是可见的
          if (mapWrapper.offsetParent !== null) {
            this.initMap();
          } else {
            console.warn('地图容器不可见，等待...');
            setTimeout(ensureHeight, 200);
          }
        };

        ensureHeight();
      });
    },

    handleResize() {
      if (this.leafletMap) {
        setTimeout(() => {
          this.leafletMap.invalidateSize();
        }, 200);
      }

      // 重新渲染图表
      this.posturePieChart?.resize();
      this.postureBarChart?.resize();
      this.stepTrendChart?.resize();
      this.activityPieChart?.resize();
      this.trendChart?.resize();
    },



    // ==================== 姿态识别 ====================
    async loadPostureData() {
      this.loading.posture = true;
      try {
        if (this.postureRecords.length === 0) {
          this.showNotification('info', '无姿态数据', '请先生成模拟数据');
        }
        this.renderPostureCharts();
        this.updateLastUpdate();
        if (this.postureRecords.length > 0) {
          this.showNotification('success', '加载成功', '姿态数据已加载');
        }
      } catch (error) {
        console.error('加载姿态数据失败:', error);
        this.showNotification('error', '加载失败', error.message);
      } finally {
        this.loading.posture = false;
      }
    },

    generateMockPostureRecords() {
      const postures = ['反刍', '采食', '站立', '行走', '躺卧'];
      const records = [];
      const now = new Date();

      for (let i = 0; i < 15; i++) {
        const time = new Date(now.getTime() - i * 20 * 60 * 1000);
        records.push({
          time: this.formatTime(time),
          animalId: this.postureFilter.animalId || this.animals[Math.floor(Math.random() * this.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
          posture: postures[Math.floor(Math.random() * postures.length)],
          confidence: Math.floor(75 + Math.random() * 25),
          duration: Math.floor(5 + Math.random() * 55)
        });
      }

      return records;
    },

    analyzePosture() {
      this.renderPostureCharts();
      this.showNotification('success', '分析完成', '姿态数据分析完成');
    },

    renderPostureCharts() {
      // 姿态分布饼图
      const postureCounts = {};
      this.postureRecords.forEach(record => {
        postureCounts[record.posture] = (postureCounts[record.posture] || 0) + 1;
      });

      const pieData = Object.entries(postureCounts).map(([name, value]) => ({ name, value }));

      // 先销毁旧实例
      if (this.posturePieChart) {
        this.posturePieChart.dispose();
        this.posturePieChart = null;
      }

      const pieContainer = document.getElementById('posturePieChart');
      if (pieContainer && pieContainer.clientWidth > 0 && pieContainer.clientHeight > 0) {
        this.posturePieChart = echarts.init(pieContainer);
        this.posturePieChart.setOption({
          tooltip: { trigger: 'item' },
          legend: {
            top: '5%',
            left: 'center',
            textStyle: { color: '#ecf0f1' }
          },
          series: [{
            name: '姿态分布',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: {
              borderRadius: 10,
              borderColor: '#1a252f',
              borderWidth: 2
            },
            label: {
              show: true,
              position: 'inside',
              formatter: '{b}: {d}%',
              color: '#fff',
              fontWeight: 'bold',
              fontSize: 12
            },
            emphasis: {
              label: {
                show: true,
                fontSize: 16,
                fontWeight: 'bold',
                color: '#ecf0f1'
              }
            },
            data: pieData
          }]
        });
      }

      // 姿态时长柱状图
      const postureDuration = {};
      this.postureRecords.forEach(record => {
        postureDuration[record.posture] = (postureDuration[record.posture] || 0) + record.duration;
      });

      const barData = Object.entries(postureDuration);

      if (this.postureBarChart) {
        this.postureBarChart.dispose();
      }

      const barContainer = document.getElementById('postureBarChart');
      if (barContainer) {
        this.postureBarChart = echarts.init(barContainer);
        this.postureBarChart.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: {
            type: 'category',
            data: barData.map(([name]) => name),
            axisLabel: { color: '#ecf0f1' }
          },
          yAxis: {
            type: 'value',
            name: '分钟',
            axisLabel: { color: '#ecf0f1' },
            nameTextStyle: { color: '#ecf0f1' }
          },
          series: [{
            data: barData.map(([, value]) => value),
            type: 'bar',
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#667eea' },
                { offset: 1, color: '#764ba2' }
              ])
            }
          }]
        });
      }
    },

    // ==================== 步数统计 ====================
    async loadStepData() {
      this.loading.steps = true;
      try {
        if (this.stepStats.todaySteps === 0) {
          this.showNotification('info', '无步数数据', '请先生成模拟数据');
        }
        this.renderStepCharts();
        this.updateLastUpdate();
        if (this.stepStats.todaySteps > 0) {
          this.showNotification('success', '加载成功', '步数统计数据已加载');
        }
      } catch (error) {
        console.error('加载步数数据失败:', error);
        this.showNotification('error', '加载失败', error.message);
      } finally {
        this.loading.steps = false;
      }
    },

    generateMockStepStats() {
      return {
        todaySteps: Math.floor(Math.random() * 10000) + 3000,
        walkingDistance: Math.floor(Math.random() * 5000) + 2000,
        activeTime: Math.floor(Math.random() * 300) + 60,
        stepFrequency: Math.floor(Math.random() * 30) + 10
      };
    },

    generateMockHourlyData() {
      const data = [];
      for (let hour = 0; hour < 24; hour++) {
        data.push({
          hour: String(hour).padStart(2, '0'),
          steps: Math.floor(Math.random() * 500) + 50,
          distance: Math.floor(Math.random() * 200) + 20,
          activeTime: Math.floor(Math.random() * 40) + 5
        });
      }
      return data;
    },

    refreshStepStats() {
      if (this.stepStats.todaySteps === 0) {
        this.showNotification('warning', '无数据', '请先生成模拟数据');
        return;
      }
      this.loadStepData();
    },

    renderStepCharts() {
      // 24 小时步数趋势图
      if (this.stepTrendChart) {
        this.stepTrendChart.dispose();
      }

      const trendContainer = document.getElementById('stepTrendChart');
      if (trendContainer && this.stepHourlyData.length > 0) {
        this.stepTrendChart = echarts.init(trendContainer);
        this.stepTrendChart.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: {
            type: 'category',
            data: this.stepHourlyData.map(d => `${d.hour}:00`),
            axisLabel: { color: '#ecf0f1' }
          },
          yAxis: {
            type: 'value',
            name: '步数',
            axisLabel: { color: '#ecf0f1' },
            nameTextStyle: { color: '#ecf0f1' }
          },
          series: [{
            data: this.stepHourlyData.map(d => d.steps),
            type: 'line',
            smooth: true,
            itemStyle: { color: '#f093fb' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(240, 147, 251, 0.5)' },
                { offset: 1, color: 'rgba(240, 147, 251, 0.1)' }
              ])
            }
          }]
        });
      }

      // 活跃程度饼图
      if (this.activityPieChart) {
        this.activityPieChart.dispose();
      }

      const activityContainer = document.getElementById('activityPieChart');
      if (activityContainer) {
        this.activityPieChart = echarts.init(activityContainer);
        this.activityPieChart.setOption({
          tooltip: { trigger: 'item' },
          legend: { top: '5%', left: 'center', textStyle: { color: '#ecf0f1' } },
          series: [{
            name: '活跃程度',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: {
              borderRadius: 10,
              borderColor: '#1a252f',
              borderWidth: 2
            },
            label: { show: false, position: 'center' },
            emphasis: {
              label: { show: true, fontSize: 16, fontWeight: 'bold', color: '#ecf0f1' }
            },
            data: [
              { value: Math.floor(Math.random() * 30) + 10, name: '低' },
              { value: Math.floor(Math.random() * 40) + 20, name: '中低' },
              { value: Math.floor(Math.random() * 40) + 30, name: '中高' },
              { value: Math.floor(Math.random() * 30) + 10, name: '高' }
            ]
          }]
        });
      }
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
        console.error('筛选数据失败:', error);
        this.showNotification('error', '筛选失败', error.message);
      } finally {
        this.loading.data = false;
      }
    },

    generateMockHistoryData() {
      const behaviors = ['采食', '反刍', '站立', '行走', '躺卧'];
      const data = [];
      const now = new Date();

      for (let i = 0; i < 30; i++) {
        const time = new Date(now.getTime() - i * 60 * 60 * 1000);
        data.push({
          time: this.formatTime(time),
          animalId: this.animals[Math.floor(Math.random() * this.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
          behavior: behaviors[Math.floor(Math.random() * behaviors.length)],
          steps: Math.floor(Math.random() * 1000),
          location: `${(30 + Math.random() * 2).toFixed(4)}°N, ${(90 + Math.random() * 2).toFixed(4)}°E`,
          status: Math.random() < 0.1 ? '异常' : '正常'
        });
      }

      return data;
    },

    exportAllData() {
      if (this.historyData.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      const csv = this.convertToCSV(this.historyData);
      this.downloadFile(csv, 'all_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '全部数据已导出');
    },

    exportFilteredData() {
      if (this.historyData.length === 0) {
        this.showNotification('warning', '无数据', '没有可导出的数据，请先生成模拟数据');
        return;
      }
      const csv = this.convertToCSV(this.historyData);
      this.downloadFile(csv, 'filtered_data.csv', 'text/csv');
      this.showNotification('success', '导出成功', '筛选结果已导出');
    },

    // ==================== 分析报告 ====================
    async loadBehaviorStatistics() {
      this.loading.analysis = true;
      try {
        if (this.analysis.healthAlerts === 0 && this.healthReport.totalAnimals === 0) {
          this.showNotification('info', '无统计数据', '请先生成模拟数据');
        } else {
          this.showNotification('success', '加载成功', '统计数据已加载');
        }
        this.renderTrendChart();
      } catch (error) {
        console.error('加载统计失败:', error);
        this.showNotification('error', '加载失败', error.message);
      } finally {
        this.loading.analysis = false;
      }
    },

    generateHealthReport() {
      if (this.healthReport.totalAnimals === 0) {
        this.showNotification('warning', '无数据', '请先生成模拟数据');
        return;
      }
      this.showNotification('success', '生成成功', '健康报告已生成');
      this.addLog('REPORT', '健康报告生成完成');
    },

    renderTrendChart() {
      if (this.trendChart) {
        this.trendChart.dispose();
      }

      const container = document.getElementById('trendChart');
      if (container && this.healthReport.totalAnimals > 0) {
        this.trendChart = echarts.init(container);

        const days = [];
        const behaviorData = [];
        for (let i = 6; i >= 0; i--) {
          const date = new Date();
          date.setDate(date.getDate() - i);
          days.push(`${date.getMonth() + 1}/${date.getDate()}`);
          behaviorData.push(Math.floor(Math.random() * 100) + 50);
        }

        this.trendChart.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: {
            type: 'category',
            data: days,
            axisLabel: { color: '#ecf0f1' }
          },
          yAxis: {
            type: 'value',
            axisLabel: { color: '#ecf0f1' }
          },
          series: [{
            data: behaviorData,
            type: 'line',
            smooth: true,
            itemStyle: { color: '#4facfe' }
          }]
        });
      }
    },

    // ==================== 工具函数 ====================
    updateLastUpdate() {
      this.lastUpdate = this.formatTime(new Date());
    },

    formatTime(date) {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`;
    },

    formatISO(date) {
      return date.toISOString().slice(0, 19);
    },

    convertToCSV(data) {
      if (!data || data.length === 0) return '';

      const headers = Object.keys(data[0]);
      const csvRows = [];

      csvRows.push(headers.join(','));

      data.forEach(row => {
        const values = headers.map(header => {
          const value = row[header];
          if (typeof value === 'string' && (value.includes(',') || value.includes('"'))) {
            return `"${value.replace(/"/g, '""')}"`;
          }
          return value;
        });
        csvRows.push(values.join(','));
      });

      return csvRows.join('\n');
    },

    downloadFile(content, filename, mimeType) {
      const blob = new Blob([content], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    },

    showNotification(type, title, message) {
      this.notification = {
        show: true,
        type,
        title,
        message
      };

      setTimeout(() => {
        this.notification.show = false;
      }, 3000);
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
    // 1. 初始化系统状态
    this.checkBackendStatus();
    this.refreshOverview();
    this.addLog('SYSTEM', '系统启动');
    this.addLog('SYSTEM', 'Vue 应用初始化完成');

    // 2. 初始化地图
    this.initMapWithRetry();

    // 3. 启动位置自动刷新
    this.startLocationAutoRefresh();

    // 4. 窗口尺寸自适应
    window.addEventListener('resize', this.handleResize);
  },

  beforeDestroy() {
    if (this.locationTimer) {
      clearInterval(this.locationTimer);
    }
    // 清除地图标记
    if (this.leafletMap) {
      this.leafletMarkers.forEach(marker => marker.remove());
      this.leafletMarkers = [];
      this.leafletMap.remove();
    }
    // 销毁图表实例
    if (this.posturePieChart) this.posturePieChart.dispose();
    if (this.postureBarChart) this.postureBarChart.dispose();
    if (this.stepTrendChart) this.stepTrendChart.dispose();
    if (this.activityPieChart) this.activityPieChart.dispose();
    if (this.trendChart) this.trendChart.dispose();

    window.removeEventListener('resize', this.handleResize);
  }
});

// ========================================
// 全局函数 (放在 Vue 实例外面)
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