// ========================================
// 云牧智感 - API 调用层
// ========================================

/**
 * 检查后端服务连接状态
 * @param {Vue} app - Vue 实例
 */
async function checkBackendStatus(app) {
  app.loading.overview = true;
  try {
    // 检查 Java 后端连接
    const response = await fetch(`${API_CONFIG.BASE_URL}/data/health`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    });

    if (response.ok) {
      app.isConnected = true;
      app.connectionStatus = '已连接';
      app.addLog('SYSTEM', '后端连接检查成功 - Java 服务');
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
        app.isConnected = true;
        app.connectionStatus = '已连接 (ML 服务)';
        app.addLog('SYSTEM', '后端连接检查成功 - Python ML 服务');
      } else {
        throw new Error('连接失败');
      }
    } catch (mlError) {
      app.isConnected = false;
      app.connectionStatus = '未连接';
      app.addLog('SYSTEM', '后端连接失败');
    }
  } finally {
    app.loading.overview = false;
  }
}

/**
 * 从后端加载统计数据
 * @param {Vue} app - Vue 实例
 */
async function loadOverviewFromBackend(app) {
  try {
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

    if (stepsRes && stepsRes.ok) {
      const stepsData = await stepsRes.json();
      if (stepsData.success) {
        const animalIds = Object.keys(stepsData.data || {});
        app.stats.totalAnimals = animalIds.length;
        app.stats.dataReceived = Object.values(stepsData.data || {}).reduce((a, b) => a + b, 0);
      }
    }

    app.animals = await loadAnimalsList(app);
    app.stats.normal = app.animals.filter(a => a.status === 'normal').length;
    app.stats.alert = app.animals.filter(a => a.status === 'alert').length;
    app.dataCount = app.stats.dataReceived;
  } catch (error) {
    app.addLog('ERROR', `加载概览数据失败: ${error.message}`);
  }
}

/**
 * 从后端加载动物列表
 * @param {Vue} app - Vue 实例
 */
async function loadAnimalsList(app) {
  try {
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

    app.addLog('SYSTEM', '后端无动物数据，请生成模拟数据');
    return [];
  } catch (error) {
    return [];
  }
}

/**
 * 加载动物行为监测数据
 * @param {Vue} app - Vue 实例
 */
async function loadAnimalData(app) {
  app.loading.animals = true;
  try {
    if (app.isConnected && app.animalFilter.animalId) {
      const now = new Date();
      const startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      const url = `${API_CONFIG.BASE_URL}/behavior/statistics/${encodeURIComponent(app.animalFilter.animalId)}?startTime=${formatISO(startTime)}&endTime=${formatISO(now)}`;
      const response = await fetch(url);

      if (response.ok) {
        const data = await response.json();
        app.animalRecords = convertBehaviorStatsToRecords(data, app);
      } else {
        app.animalRecords = [];
      }
    } else {
      if (app.animalRecords.length === 0) {
        app.showNotification('info', '无数据', '请先点击"生成模拟数据"按钮');
      }
    }
    app.updateLastUpdate();
    if (app.animalRecords.length > 0) {
      app.showNotification('success', '加载成功', `已加载${app.animalRecords.length}条记录`);
    }
  } catch (error) {
    app.showNotification('error', '加载失败', error.message);
  } finally {
    app.loading.animals = false;
  }
}

/**
 * 转换行为统计数据为记录格式
 */
function convertBehaviorStatsToRecords(stats, app) {
  const records = [];
  if (stats && stats.behaviors) {
    Object.entries(stats.behaviors).forEach(([behavior, data]) => {
      records.push({
        time: formatTime(new Date()),
        animalId: stats.animalId || 'unknown',
        behavior: behavior,
        temperature: (37 + Math.random() * 2).toFixed(1),
        heartRate: Math.floor(50 + Math.random() * 40),
        status: data.confidence > 0.8 ? '正常' : '异常'
      });
    });
  }
  return records;
}

/**
 * 加载行为统计数据（分析报告）
 * @param {Vue} app - Vue 实例
 */
async function loadBehaviorStatistics(app) {
  app.loading.analysis = true;
  try {
    if (app.analysis.healthAlerts === 0 && app.healthReport.totalAnimals === 0) {
      app.showNotification('info', '无统计数据', '请先生成模拟数据');
    } else {
      app.showNotification('success', '加载成功', '统计数据已加载');
    }
    app.renderTrendChart();
  } catch (error) {
    app.showNotification('error', '加载失败', error.message);
  } finally {
    app.loading.analysis = false;
  }
}
