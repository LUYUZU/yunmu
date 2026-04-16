// ========================================
// 云牧智感 - 模拟数据生成
// ========================================

/**
 * 生成完整模拟数据（覆盖所有模块）
 * @param {Vue} app - Vue 实例
 */
function generateMockData(app) {
  // 生成模拟统计数据
  const totalAnimals = Math.floor(Math.random() * 20) + 10;
  const alertCount = Math.floor(Math.random() * 3);
  app.stats = {
    totalAnimals: totalAnimals,
    normal: totalAnimals - alertCount,
    alert: alertCount,
    dataReceived: Math.floor(Math.random() * 1000) + 500
  };

  const behaviors = ['采食', '站立', '行走', '躺卧'];
  const animalTypes = ['cow', 'sheep'];
  app.animals = [];

  for (let i = 1; i <= Math.min(totalAnimals, 12); i++) {
    const id = `NO.${String(i).padStart(3, '0')}`;
    const isAlert = i <= alertCount;
    app.animals.push({
      id: id,
      type: animalTypes[Math.floor(Math.random() * animalTypes.length)],
      status: isAlert ? 'alert' : 'normal',
      temperature: (37 + Math.random() * 3).toFixed(1),
      heartRate: Math.floor(50 + Math.random() * 50),
      steps: Math.floor(Math.random() * 5000),
      behavior: behaviors[Math.floor(Math.random() * behaviors.length)]
    });
  }

  app.dataCount = app.stats.dataReceived;

  // 同时生成其他模块的模拟数据
  app.animalRecords = generateMockAnimalRecords(app);
  app.postureRecords = generateMockPostureRecords(app);
  app.stepStats = generateMockStepStats();
  app.stepHourlyData = generateMockHourlyData();
  app.historyData = generateMockHistoryData(app);
  app.locationData = generateMockLocationData(app);

  // 更新分析报告数据
  app.analysis = {
    healthAlerts: Math.floor(Math.random() * 5),
    postureAlerts: Math.floor(Math.random() * 8),
    stepAlerts: Math.floor(Math.random() * 6),
    locationAlerts: Math.floor(Math.random() * 3)
  };
  app.healthReport = {
    totalAnimals: app.stats.totalAnimals,
    normalAnimals: app.stats.normal,
    alertAnimals: app.stats.alert
  };

  // 重新渲染图表
  app.renderPostureCharts();
  app.renderStepCharts();
  app.renderTrendChart();

  // 更新地图标记
  if (app.mapLoaded && app.leafletMap) {
    app.updateMapMarkers();
  }

  app.updateLastUpdate();
  app.addLog('MOCK', `生成${totalAnimals}只动物的模拟监测数据`);
  app.showNotification('success', '生成成功', `已生成${totalAnimals}只动物的模拟监测数据`);
}

/**
 * 生成模拟动物行为记录
 */
function generateMockAnimalRecords(app) {
  const behaviors = ['采食', '站立', '行走', '躺卧'];
  const records = [];
  const now = new Date();

  for (let i = 0; i < 20; i++) {
    const time = new Date(now.getTime() - i * 30 * 60 * 1000);
    const isAlert = Math.random() < 0.1;
    records.push({
      time: formatTime(time),
      animalId: app.animalFilter.animalId || app.animals[Math.floor(Math.random() * app.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
      behavior: behaviors[Math.floor(Math.random() * behaviors.length)],
      temperature: (37 + Math.random() * 3).toFixed(1),
      heartRate: Math.floor(50 + Math.random() * 50),
      status: isAlert ? '异常' : '正常'
    });
  }

  return records;
}

/**
 * 生成模拟位置数据
 */
function generateMockLocationData(app) {
  const data = [];
  const latRange = [29.0, 31.5];  // 西藏地区纬度范围
  const lngRange = [88.0, 92.0];  // 西藏地区经度范围

  for (let i = 0; i < app.animals.length; i++) {
    data.push({
      id: app.animals[i].id,
      latitude: latRange[0] + Math.random() * (latRange[1] - latRange[0]),
      longitude: lngRange[0] + Math.random() * (lngRange[1] - lngRange[0]),
      timestamp: formatTime(new Date())
    });
  }
  return data;
}

/**
 * 生成模拟姿态记录
 */
function generateMockPostureRecords(app) {
  const postures = ['采食', '站立', '行走', '躺卧'];
  const records = [];
  const now = new Date();

  for (let i = 0; i < 15; i++) {
    const time = new Date(now.getTime() - i * 20 * 60 * 1000);
    records.push({
      time: formatTime(time),
      animalId: app.postureFilter.animalId || app.animals[Math.floor(Math.random() * app.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
      posture: postures[Math.floor(Math.random() * postures.length)],
      confidence: Math.floor(75 + Math.random() * 25),
      duration: Math.floor(5 + Math.random() * 55)
    });
  }

  return records;
}

/**
 * 生成模拟步数统计数据
 */
function generateMockStepStats() {
  return {
    todaySteps: Math.floor(Math.random() * 10000) + 3000,
    walkingDistance: Math.floor(Math.random() * 5000) + 2000,
    activeTime: Math.floor(Math.random() * 300) + 60,
    stepFrequency: Math.floor(Math.random() * 30) + 10
  };
}

/**
 * 生成模拟小时步数数据（24小时）
 */
function generateMockHourlyData() {
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
}

/**
 * 生成模拟历史数据
 */
function generateMockHistoryData(app) {
  const behaviors = ['采食', '站立', '行走', '躺卧'];
  const data = [];
  const now = new Date();

  for (let i = 0; i < 30; i++) {
    const time = new Date(now.getTime() - i * 60 * 60 * 1000);
    data.push({
      time: formatTime(time),
      animalId: app.animals[Math.floor(Math.random() * app.animals.length)]?.id || `NO.${String(Math.floor(Math.random() * 8) + 1).padStart(3, '0')}`,
      behavior: behaviors[Math.floor(Math.random() * behaviors.length)],
      steps: Math.floor(Math.random() * 1000),
      location: `${(30 + Math.random() * 2).toFixed(4)}°N, ${(90 + Math.random() * 2).toFixed(4)}°E`,
      status: Math.random() < 0.1 ? '异常' : '正常'
    });
  }

  return data;
}
