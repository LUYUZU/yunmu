// ========================================
// 云牧智感 - 高德地图模块
// ========================================

/**
 * 初始化高德地图
 * @param {Vue} app - Vue 实例
 */
function initMap(app) {
  try {
    // 防止重复初始化
    if (app.leafletMap) {
      app.leafletMap.setZoom(app.leafletMap.getZoom()); // 触发重渲染
      app.addLog('MAP', '地图已存在，跳过重复初始化');
      return;
    }

    const mapContainer = document.getElementById('locationMap');
    if (!mapContainer) {
      console.error('地图容器不存在');
      return;
    }

    // 设置容器尺寸
    const parent = mapContainer.parentElement;
    if (parent && parent.clientHeight > 0) {
      mapContainer.style.height = parent.clientHeight + 'px';
    } else if (mapContainer.clientHeight === 0 || !mapContainer.clientHeight) {
      mapContainer.style.height = '500px';
    }

    // 西藏高原中心点
    const CENTER = [90.0, 30.0];

    // 创建高德地图实例
    app.leafletMap = new AMap.Map('locationMap', {
      zoom: 6,
      center: CENTER,
      viewMode: '2D',
      mapStyle: 'amap://styles/light', // 浅色清新风格
      pitch: 0,
      features: ['bg', 'road', 'building', 'point']
    });

    app.mapLoaded = true;
    app.addLog('MAP', '高德地图初始化成功');

    // 加载位置数据
    loadLocationData(app);
  } catch (error) {
    console.error('高德地图初始化失败:', error);
    app.mapLoaded = false;
    app.leafletMap = null;
    app.addLog('MAP', `地图初始化失败: ${error.message}`);
  }
}

/**
 * 更新地图标记（清除旧标记，重新绘制）
 * @param {Vue} app - Vue 实例
 */
function updateMapMarkers(app) {
  if (!app.leafletMap || !app.mapLoaded) return;

  // 清除旧标记
  if (app.leafletMarkers && app.leafletMarkers.length > 0) {
    app.leafletMap.remove(app.leafletMarkers);
  }
  app.leafletMarkers = [];

  if (!app.locationData || app.locationData.length === 0) return;

  app.locationData.forEach(loc => {
    const animal = app.animals.find(a => a.id === loc.id);
    const isAlert = animal?.status === 'alert';
    const markerColor = isAlert ? '#e74c3c' : '#3498db';
    const animalEmoji = animal?.type === 'cow' ? '🐄' : '🐑';
    const statusText = isAlert ? '⚠️ 异常' : '✅ 正常';
    const tempValue = animal?.temperature ? `${animal.temperature}°C` : '暂无';
    const heartValue = animal?.heartRate ? `${animal.heartRate} bpm` : '暂无';
    const stepsValue = animal?.steps || '0';

    // 弹窗内容 HTML
    const popupContent = `
      <div class="amap-popup">
        <h4 style="margin: 0 0 10px 0; color: ${markerColor}; display: flex; align-items: center; gap: 6px;">
          <span style="font-size: 18px;">${animalEmoji}</span>
          <span style="font-weight: bold;">${loc.id}</span>
        </h4>
        <p style="margin: 5px 0; font-size: 13px;">
          <strong>📍 位置:</strong><br>
          <span style="color: #aaa; font-family: monospace;">${loc.latitude.toFixed(6)}°N, ${loc.longitude.toFixed(6)}°E</span>
        </p>
        <p style="margin: 5px 0; font-size: 13px;">
          <strong>💚 状态:</strong>
          <span style="color: ${markerColor}; font-weight: bold;">${statusText}</span>
        </p>
        <p style="margin: 5px 0; font-size: 13px;">
          <strong>🌡️ 体温:</strong> ${tempValue}
        </p>
        <p style="margin: 5px 0; font-size: 13px;">
          <strong>💓 心率:</strong> ${heartValue}
        </p>
        <p style="margin: 5px 0; font-size: 13px;">
          <strong>👣 步数:</strong> ${stepsValue}
        </p>
        <p style="margin: 5px 0; font-size: 12px; color: #888;">
          <strong>🕐 更新:</strong> ${loc.timestamp || app.lastUpdate}
        </p>
      </div>
    `;

    // 创建 Marker 实例
    const marker = new AMap.Marker({
      position: new AMap.LngLat(loc.longitude, loc.latitude),
      title: loc.id,
      content: `<div class="amap-custom-marker" style="
        background: ${markerColor};
        width: 42px; height: 42px;
        border-radius: 50%;
        border: 3px solid white;
        display: flex; align-items: center; justify-content: center;
        font-size: 20px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.35);
        cursor: pointer;
        line-height: 42px;
        text-align: center;
      ">${animalEmoji}</div>`,
      extData: { id: loc.id, isAlert }
    });

    // 绑定信息窗体
    marker.on('click', () => {
      const infoWindow = new AMap.InfoWindow({
        content: popupContent,
        offset: new AMap.Pixel(0, -30),
        showShadow: true,
        closeWhenClickMap: true
      });
      infoWindow.open(app.leafletMap, marker.getPosition());
    });

    // 悬停显示
    marker.on('mouseover', () => {
      if (!app._hoverWindow) {
        app._hoverWindow = new AMap.InfoWindow({
          content: popupContent,
          offset: new AMap.Pixel(0, -30),
          closeWhenClickMap: true
        });
      }
      app._hoverWindow.setContent(popupContent);
      app._hoverWindow.open(app.leafletMap, marker.getPosition());
    });

    marker.on('mouseout', () => {
      if (app._hoverWindow) {
        app._hoverWindow.close();
      }
    });

    // 添加到地图
    app.leafletMap.add(marker);
    app.leafletMarkers.push(marker);
  });

  // 自动调整视野覆盖所有标记
  if (app.leafletMarkers.length > 0) {
    app.leafletMap.setFitView(app.leafletMarkers, false, [50, 50, 50, 50]);
  }

  app.addLog('MAP', `已更新 ${app.leafletMarkers.length} 个动物标记`);
}

/**
 * 加载位置数据（调用 updateMapMarkers 重新渲染）
 * @param {Vue} app - Vue 实例
 */
async function loadLocationData(app) {
  app.loading.location = true;
  try {
    if (app.locationData.length === 0) {
      app.showNotification('info', '无位置数据', '请先生成模拟数据');
    }
    app.updateLastUpdate();

    if (app.mapLoaded && app.leafletMap && app.locationData.length > 0) {
      updateMapMarkers(app);
    }

    if (app.locationData.length > 0) {
      app.showNotification('success', '加载成功', `已加载 ${app.locationData.length} 个位置数据`);
      app.addLog('LOCATION', `位置数据已更新，共 ${app.locationData.length} 个点位`);
    }
  } catch (error) {
    app.showNotification('error', '加载失败', error.message);
  } finally {
    app.loading.location = false;
  }
}

/**
 * 清除位置轨迹
 * @param {Vue} app - Vue 实例
 */
function clearLocationTrace(app) {
  app.locationTrace = [];
  if (app.polyline && app.leafletMap) {
    app.leafletMap.remove(app.polyline);
    app.polyline = null;
  }
  app.showNotification('success', '清除成功', '轨迹已清除');
  app.addLog('MAP', '轨迹已清除');
}

/**
 * 地图初始化重试机制
 * @param {Vue} app - Vue 实例
 * @param {number} retryCount - 当前重试次数
 */
function initMapWithRetry(app, retryCount = 0) {
  if (app.leafletMap && app.mapLoaded) return;

  const maxRetries = 20;
  app.$nextTick(() => {
    const mapContainer = document.getElementById('locationMap');
    const mapWrapper = document.querySelector('.map-container');

    if (!mapContainer || !mapWrapper) {
      if (retryCount < maxRetries) setTimeout(() => initMapWithRetry(app, retryCount + 1), 300);
      return;
    }

    // 确保容器高度
    if (mapWrapper.clientHeight === 0) mapWrapper.style.height = '500px';
    if (!mapContainer.style.height || mapContainer.clientHeight === 0) {
      mapContainer.style.height = '500px';
    }

    // 已初始化则只重渲染
    if (app.leafletMap && app.mapLoaded) {
      app.leafletMap.setZoom(app.leafletMap.getZoom());
      return;
    }

    // 确保容器可见
    if (mapWrapper.offsetParent !== null) {
      initMap(app);
    } else {
      if (retryCount < maxRetries) setTimeout(() => initMapWithRetry(app, retryCount + 1), 200);
    }
  });
}

/**
 * 窗口大小变化处理
 * @param {Vue} app - Vue 实例
 */
function handleMapResize(app) {
  if (app.leafletMap) {
    setTimeout(() => {
      app.leafletMap.setZoom(app.leafletMap.getZoom());
    }, 200);
  }
}
