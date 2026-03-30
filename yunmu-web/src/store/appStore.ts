// =============================================
// 云牧智感 - Zustand 全局状态
// =============================================
import { create } from 'zustand';
import type {
  Animal, LocationPoint, StreamLog, AnimalRecord, PostureRecord,
  StepStats, HourlyStep, HistoryRecord, Stats, Analysis,
  HealthReport, Notification, NotificationType, DataFilter,
} from '../types';
import { formatTime, API_CONFIG } from '../utils';
import {
  generateAnimals, generateStats, generateAnimalRecords,
  generatePostureRecords, generateStepStats, generateHourlyData,
  generateHistoryData, generateLocationData, generateAnalysis,
  generateHealthReport,
} from '../utils/mockData';

// ---- 通知计时器 ----
let notifTimer: ReturnType<typeof setTimeout> | null = null;

interface AppState {
  // 连接
  isConnected: boolean;
  connectionStatus: string;

  // 最后更新
  lastUpdate: string;

  // 加载
  loading: Record<string, boolean>;

  // 统计
  stats: Stats;

  // 动物
  animals: Animal[];
  animalFilter: { animalId: string };
  animalRecords: AnimalRecord[];

  // 位置
  locationData: LocationPoint[];

  // 姿态
  postureFilter: { animalId: string };
  postureRecords: PostureRecord[];

  // 步数
  stepStats: StepStats;
  stepHourlyData: HourlyStep[];

  // 数据管理
  dataFilter: DataFilter;
  historyData: HistoryRecord[];

  // 分析
  analysis: Analysis;
  healthReport: HealthReport;

  // 数据流
  dataStreamLogs: StreamLog[];

  // 通知
  notification: Notification;

  // Actions
  setLoading: (key: string, val: boolean) => void;
  addLog: (source: string, message: string) => void;
  clearLogs: () => void;
  showNotification: (type: NotificationType, title: string, message: string) => void;
  closeNotification: () => void;
  updateLastUpdate: () => void;
  setAnimalFilter: (animalId: string) => void;
  setPostureFilter: (animalId: string) => void;
  setDataFilter: (f: Partial<DataFilter>) => void;

  checkBackendStatus: () => Promise<void>;
  generateMockData: () => void;
  refreshOverview: () => Promise<void>;
  loadAnimalData: () => Promise<void>;
  loadLocationData: () => Promise<void>;
  loadPostureData: () => Promise<void>;
  loadStepData: () => Promise<void>;
  filterData: () => Promise<void>;
  loadBehaviorStatistics: () => Promise<void>;
  exportAnimalData: () => void;
  exportAllData: () => void;
  exportFilteredData: () => void;
  generateHealthReport: () => void;
  clearLocationTrace: () => void;
}

// ---- 导入 CSV 工具（避免循环依赖写在这里）----
import { convertToCSV, downloadFile } from '../utils';

const emptyStats: Stats = { totalAnimals: 0, normal: 0, alert: 0, dataReceived: 0 };
const emptyStepStats: StepStats = { todaySteps: 0, walkingDistance: 0, activeTime: 0, stepFrequency: 0 };
const emptyAnalysis: Analysis = { healthAlerts: 0, postureAlerts: 0, stepAlerts: 0, locationAlerts: 0 };
const emptyHealthReport: HealthReport = { totalAnimals: 0, normalAnimals: 0, alertAnimals: 0 };

export const useAppStore = create<AppState>((set, get) => ({
  isConnected: false,
  connectionStatus: '未连接',
  lastUpdate: '--:--:--',
  loading: {},
  stats: emptyStats,
  animals: [],
  animalFilter: { animalId: '' },
  animalRecords: [],
  locationData: [],
  postureFilter: { animalId: '' },
  postureRecords: [],
  stepStats: emptyStepStats,
  stepHourlyData: [],
  dataFilter: { startDate: '', endDate: '', dataType: '' },
  historyData: [],
  analysis: emptyAnalysis,
  healthReport: emptyHealthReport,
  dataStreamLogs: [],
  notification: { show: false, type: 'info', title: '', message: '' },

  // ---- 基础 ----
  setLoading: (key, val) =>
    set(s => ({ loading: { ...s.loading, [key]: val } })),

  addLog: (source, message) => {
    const log: StreamLog = {
      id: Date.now() + Math.random(),
      time: formatTime(new Date()),
      source,
      message,
    };
    set(s => ({
      dataStreamLogs: [log, ...s.dataStreamLogs].slice(0, 100),
    }));
  },

  clearLogs: () => {
    set({ dataStreamLogs: [] });
    get().addLog('SYSTEM', '数据流已清空');
    get().showNotification('info', '清空成功', '数据流已清空');
  },

  showNotification: (type, title, message) => {
    if (notifTimer) clearTimeout(notifTimer);
    set({ notification: { show: true, type, title, message } });
    notifTimer = setTimeout(() => set(s => ({ notification: { ...s.notification, show: false } })), 3000);
  },

  closeNotification: () => set(s => ({ notification: { ...s.notification, show: false } })),

  updateLastUpdate: () => set({ lastUpdate: formatTime(new Date()) }),

  setAnimalFilter: animalId => set(s => ({ animalFilter: { ...s.animalFilter, animalId } })),
  setPostureFilter: animalId => set(s => ({ postureFilter: { ...s.postureFilter, animalId } })),
  setDataFilter: f => set(s => ({ dataFilter: { ...s.dataFilter, ...f } })),

  // ---- 检查连接 ----
  checkBackendStatus: async () => {
    const { setLoading, showNotification, addLog } = get();
    setLoading('overview', true);
    try {
      const res = await fetch(`${API_CONFIG.BASE_URL}/data/health`).catch(() => null);
      if (res?.ok) {
        set({ isConnected: true, connectionStatus: '已连接' });
        showNotification('success', '连接成功', '后端服务连接正常');
        addLog('SYSTEM', '后端连接检查成功 - Java 服务');
        return;
      }
      const mlRes = await fetch(`${API_CONFIG.ML_SERVICE_URL}/health`).catch(() => null);
      if (mlRes?.ok) {
        set({ isConnected: true, connectionStatus: '已连接 (ML 服务)' });
        showNotification('success', '连接成功', 'Python ML 服务连接正常');
        addLog('SYSTEM', '后端连接检查成功 - Python ML 服务');
        return;
      }
      throw new Error('all failed');
    } catch {
      set({ isConnected: false, connectionStatus: '未连接' });
      showNotification('warning', '连接失败', '后端服务未启动或不可用');
      addLog('SYSTEM', '后端连接失败');
    } finally {
      setLoading('overview', false);
    }
  },

  // ---- 生成模拟数据 ----
  generateMockData: () => {
    const { showNotification, addLog } = get();
    const animals = generateAnimals();
    const stats   = generateStats(animals);

    set({
      animals,
      stats,
      animalRecords:  generateAnimalRecords(animals),
      postureRecords: generatePostureRecords(animals),
      stepStats:      generateStepStats(),
      stepHourlyData: generateHourlyData(),
      historyData:    generateHistoryData(animals),
      locationData:   generateLocationData(animals),
      analysis:       generateAnalysis(),
      healthReport:   generateHealthReport(stats),
      lastUpdate:     formatTime(new Date()),
    });

    addLog('MOCK', `生成 ${animals.length} 只动物的模拟监测数据`);
    showNotification('success', '生成成功', `已生成 ${animals.length} 只动物的模拟监测数据`);
  },

  // ---- 刷新概览 ----
  refreshOverview: async () => {
    const { isConnected, setLoading, showNotification, updateLastUpdate } = get();
    setLoading('overview', true);
    try {
      if (isConnected) {
        // 后端在线时可在此处加真实 API 请求
      } else {
        showNotification('info', '等待数据', '后端未连接，请点击"生成模拟数据"按钮');
      }
      updateLastUpdate();
    } finally {
      setLoading('overview', false);
    }
  },

  // ---- 加载动物数据 ----
  loadAnimalData: async () => {
    const { setLoading, showNotification, animals, animalFilter } = get();
    setLoading('animals', true);
    try {
      if (get().animalRecords.length === 0) {
        if (animals.length > 0) {
          set({ animalRecords: generateAnimalRecords(animals, animalFilter.animalId) });
        } else {
          showNotification('info', '无数据', '请先点击"生成模拟数据"按钮');
        }
      }
      get().updateLastUpdate();
      if (get().animalRecords.length > 0) {
        showNotification('success', '加载成功', `已加载 ${get().animalRecords.length} 条记录`);
      }
    } finally {
      setLoading('animals', false);
    }
  },

  // ---- 位置数据 ----
  loadLocationData: async () => {
    const { setLoading, showNotification, locationData } = get();
    setLoading('location', true);
    try {
      if (locationData.length === 0) {
        showNotification('info', '无位置数据', '请先生成模拟数据');
      } else {
        showNotification('success', '加载成功', `已加载 ${locationData.length} 个位置数据`);
      }
      get().updateLastUpdate();
    } finally {
      setLoading('location', false);
    }
  },

  clearLocationTrace: () => {
    get().showNotification('success', '清除成功', '轨迹已清除');
    get().addLog('MAP', '轨迹已清除');
  },

  // ---- 姿态数据 ----
  loadPostureData: async () => {
    const { setLoading, showNotification, animals, postureFilter } = get();
    setLoading('posture', true);
    try {
      if (get().postureRecords.length === 0) {
        if (animals.length > 0) {
          set({ postureRecords: generatePostureRecords(animals, postureFilter.animalId) });
        } else {
          showNotification('info', '无姿态数据', '请先生成模拟数据');
        }
      } else {
        showNotification('success', '加载成功', '姿态数据已加载');
      }
      get().updateLastUpdate();
    } finally {
      setLoading('posture', false);
    }
  },

  // ---- 步数数据 ----
  loadStepData: async () => {
    const { setLoading, showNotification } = get();
    setLoading('steps', true);
    try {
      if (get().stepStats.todaySteps === 0) {
        showNotification('info', '无步数数据', '请先生成模拟数据');
      } else {
        showNotification('success', '加载成功', '步数统计数据已加载');
      }
      get().updateLastUpdate();
    } finally {
      setLoading('steps', false);
    }
  },

  // ---- 数据管理 ----
  filterData: async () => {
    const { setLoading, showNotification, historyData } = get();
    setLoading('data', true);
    try {
      if (historyData.length === 0) {
        showNotification('info', '无历史数据', '请先生成模拟数据');
      } else {
        showNotification('success', '筛选成功', `找到 ${historyData.length} 条记录`);
      }
    } finally {
      setLoading('data', false);
    }
  },

  exportAnimalData: () => {
    const { animalRecords, showNotification } = get();
    if (!animalRecords.length) { showNotification('warning', '无数据', '请先生成模拟数据'); return; }
    downloadFile(convertToCSV(animalRecords as unknown as Record<string, unknown>[]), 'animal_data.csv', 'text/csv');
    showNotification('success', '导出成功', '动物监测数据已导出');
  },

  exportAllData: () => {
    const { historyData, showNotification } = get();
    if (!historyData.length) { showNotification('warning', '无数据', '请先生成模拟数据'); return; }
    downloadFile(convertToCSV(historyData as unknown as Record<string, unknown>[]), 'all_data.csv', 'text/csv');
    showNotification('success', '导出成功', '全部数据已导出');
  },

  exportFilteredData: () => {
    const { historyData, showNotification } = get();
    if (!historyData.length) { showNotification('warning', '无数据', '请先生成模拟数据'); return; }
    downloadFile(convertToCSV(historyData as unknown as Record<string, unknown>[]), 'filtered_data.csv', 'text/csv');
    showNotification('success', '导出成功', '筛选结果已导出');
  },

  // ---- 分析报告 ----
  loadBehaviorStatistics: async () => {
    const { setLoading, showNotification, analysis, healthReport } = get();
    setLoading('analysis', true);
    try {
      if (analysis.healthAlerts === 0 && healthReport.totalAnimals === 0) {
        showNotification('info', '无统计数据', '请先生成模拟数据');
      } else {
        showNotification('success', '加载成功', '统计数据已加载');
      }
    } finally {
      setLoading('analysis', false);
    }
  },

  generateHealthReport: () => {
    const { healthReport, showNotification, addLog } = get();
    if (healthReport.totalAnimals === 0) { showNotification('warning', '无数据', '请先生成模拟数据'); return; }
    showNotification('success', '生成成功', '健康报告已生成');
    addLog('REPORT', '健康报告生成完成');
  },
}));
