// ========================================
// 云牧智感 - 工具函数
// ========================================

/**
 * 格式化时间为 YYYY/MM/DD HH:mm:ss
 */
function formatTime(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`;
}

/**
 * 格式化时间为 ISO 字符串（不含毫秒）
 */
function formatISO(date) {
  return date.toISOString().slice(0, 19);
}

/**
 * 将数据数组转换为 CSV 格式字符串
 */
function convertToCSV(data) {
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
}

/**
 * 下载文件
 */
function downloadFile(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * 显示通知
 */
function showNotificationApp(type, title, message, app) {
  app.notification = {
    show: true,
    type,
    title,
    message
  };
  setTimeout(() => {
    app.notification.show = false;
  }, 3000);
}

/**
 * 获取标签页名称
 */
function getTabName(tab) {
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
}
