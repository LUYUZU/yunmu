// ========================================
// 云牧智感 - 分析报告图表模块
// ========================================

/**
 * 生成健康报告
 * @param {Vue} app - Vue 实例
 */
function generateHealthReport(app) {
  if (app.healthReport.totalAnimals === 0) {
    app.showNotification('warning', '无数据', '请先生成模拟数据');
    return;
  }
  app.showNotification('success', '生成成功', '健康报告已生成');
  app.addLog('REPORT', '健康报告生成完成');
}

/**
 * 渲染趋势图表
 * @param {Vue} app - Vue 实例
 */
function renderTrendChart(app) {
  if (app.trendChart) {
    app.trendChart.dispose();
  }

  const container = document.getElementById('trendChart');
  if (container && app.healthReport.totalAnimals > 0) {
    app.trendChart = echarts.init(container);

    const days = [];
    const behaviorData = [];
    for (let i = 6; i >= 0; i--) {
      const date = new Date();
      date.setDate(date.getDate() - i);
      days.push(`${date.getMonth() + 1}/${date.getDate()}`);
      behaviorData.push(Math.floor(Math.random() * 100) + 50);
    }

    app.trendChart.setOption({
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
}
