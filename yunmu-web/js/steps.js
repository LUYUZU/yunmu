// ========================================
// 云牧智感 - 步数统计图表模块
// ========================================

/**
 * 加载步数数据
 * @param {Vue} app - Vue 实例
 */
async function loadStepData(app) {
  app.loading.steps = true;
  try {
    if (app.stepStats.todaySteps === 0) {
      app.showNotification('info', '无步数数据', '请先生成模拟数据');
    }
    app.renderStepCharts();
    app.updateLastUpdate();
    if (app.stepStats.todaySteps > 0) {
      app.showNotification('success', '加载成功', '步数统计数据已加载');
    }
  } catch (error) {
    app.showNotification('error', '加载失败', error.message);
  } finally {
    app.loading.steps = false;
  }
}

/**
 * 刷新步数统计
 * @param {Vue} app - Vue 实例
 */
function refreshStepStats(app) {
  if (app.stepStats.todaySteps === 0) {
    app.showNotification('warning', '无数据', '请先生成模拟数据');
    return;
  }
  loadStepData(app);
}

/**
 * 渲染步数图表（趋势图 + 活跃饼图）
 * @param {Vue} app - Vue 实例
 */
function renderStepCharts(app) {
  renderStepTrendChart(app);
  renderActivityPieChart(app);
}

/**
 * 渲染24小时步数趋势图
 */
function renderStepTrendChart(app) {
  if (app.stepTrendChart) {
    app.stepTrendChart.dispose();
  }

  const trendContainer = document.getElementById('stepTrendChart');
  if (trendContainer && app.stepHourlyData.length > 0) {
    app.stepTrendChart = echarts.init(trendContainer);
    app.stepTrendChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: app.stepHourlyData.map(d => `${d.hour}:00`),
        axisLabel: { color: '#ecf0f1' }
      },
      yAxis: {
        type: 'value',
        name: '步数',
        axisLabel: { color: '#ecf0f1' },
        nameTextStyle: { color: '#ecf0f1' }
      },
      series: [{
        data: app.stepHourlyData.map(d => d.steps),
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
}

/**
 * 渲染活跃程度饼图
 */
function renderActivityPieChart(app) {
  if (app.activityPieChart) {
    app.activityPieChart.dispose();
  }

  const activityContainer = document.getElementById('activityPieChart');
  if (activityContainer) {
    app.activityPieChart = echarts.init(activityContainer);
    app.activityPieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: {
        top: '5%',
        left: 'center',
        textStyle: { color: '#ecf0f1' }
      },
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
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            color: '#ecf0f1'
          }
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
}
