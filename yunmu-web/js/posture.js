// ========================================
// 云牧智感 - 姿态识别图表模块
// ========================================

/**
 * 加载姿态数据
 * @param {Vue} app - Vue 实例
 */
async function loadPostureData(app) {
  app.loading.posture = true;
  try {
    if (app.postureRecords.length === 0) {
      app.showNotification('info', '无姿态数据', '请先生成模拟数据');
    }
    app.renderPostureCharts();
    app.updateLastUpdate();
    if (app.postureRecords.length > 0) {
      app.showNotification('success', '加载成功', '姿态数据已加载');
    }
  } catch (error) {
    app.showNotification('error', '加载失败', error.message);
  } finally {
    app.loading.posture = false;
  }
}

/**
 * 分析姿态数据
 * @param {Vue} app - Vue 实例
 */
function analyzePosture(app) {
  app.renderPostureCharts();
  app.showNotification('success', '分析完成', '姿态数据分析完成');
}

/**
 * 渲染姿态识别图表（饼图 + 柱状图）
 * @param {Vue} app - Vue 实例
 */
function renderPostureCharts(app) {
  renderPosturePieChart(app);
  renderPostureBarChart(app);
}

/**
 * 渲染姿态分布饼图
 */
function renderPosturePieChart(app) {
  const postureCounts = {};
  app.postureRecords.forEach(record => {
    postureCounts[record.posture] = (postureCounts[record.posture] || 0) + 1;
  });

  const pieData = Object.entries(postureCounts).map(([name, value]) => ({ name, value }));

  if (app.posturePieChart) {
    app.posturePieChart.dispose();
    app.posturePieChart = null;
  }

  const pieContainer = document.getElementById('posturePieChart');
  if (pieContainer && pieContainer.clientWidth > 0 && pieContainer.clientHeight > 0) {
    app.posturePieChart = echarts.init(pieContainer);
    app.posturePieChart.setOption({
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
}

/**
 * 渲染姿态时长柱状图
 */
function renderPostureBarChart(app) {
  const postureDuration = {};
  app.postureRecords.forEach(record => {
    postureDuration[record.posture] = (postureDuration[record.posture] || 0) + record.duration;
  });

  const barData = Object.entries(postureDuration);

  if (app.postureBarChart) {
    app.postureBarChart.dispose();
  }

  const barContainer = document.getElementById('postureBarChart');
  if (barContainer) {
    app.postureBarChart = echarts.init(barContainer);
    app.postureBarChart.setOption({
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
}
