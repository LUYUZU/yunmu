// =============================================
// 云牧智感 - 工具函数
// =============================================

export const formatTime = (date: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

export const formatISO = (date: Date): string =>
  date.toISOString().slice(0, 19);

export const convertToCSV = (data: Record<string, unknown>[]): string => {
  if (!data.length) return '';
  const headers = Object.keys(data[0]);
  const rows = data.map(row =>
    headers.map(h => {
      const v = String(row[h] ?? '');
      return v.includes(',') || v.includes('"') ? `"${v.replace(/"/g, '""')}"` : v;
    }).join(',')
  );
  return [headers.join(','), ...rows].join('\n');
};

export const downloadFile = (content: string, filename: string, mimeType: string) => {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

export const TAB_NAMES: Record<string, string> = {
  overview: '系统概览',
  animals:  '动物监测',
  location: '北斗定位',
  posture:  '姿态识别',
  steps:    '步数统计',
  data:     '数据管理',
  analysis: '分析报告',
};

export const BEHAVIORS = ['采食', '反刍', '站立', '行走', '躺卧'] as const;
export const POSTURES  = ['反刍', '采食', '站立', '行走', '躺卧'] as const;
export const ANIMAL_TYPES: Array<'cow' | 'sheep'> = ['cow', 'sheep'];

export const API_CONFIG = {
  BASE_URL:       'http://localhost:8080/api',
  ML_SERVICE_URL: 'http://localhost:5000/api',
};
