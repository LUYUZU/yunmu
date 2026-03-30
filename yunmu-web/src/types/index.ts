// =============================================
// 云牧智感 - 类型定义
// =============================================

export type AnimalType = 'cow' | 'sheep';
export type StatusType = 'normal' | 'alert';
export type NotificationType = 'success' | 'error' | 'warning' | 'info';
export type TabKey =
  | 'overview'
  | 'animals'
  | 'location'
  | 'posture'
  | 'steps'
  | 'data'
  | 'analysis';

// === 动物 ===
export interface Animal {
  id: string;
  type: AnimalType;
  status: StatusType;
  temperature: string;
  heartRate: number;
  steps: number;
  behavior: string;
}

// === 位置 ===
export interface LocationPoint {
  id: string;
  latitude: number;
  longitude: number;
  timestamp: string;
}

// === 日志流 ===
export interface StreamLog {
  id: number;
  time: string;
  source: string;
  message: string;
}

// === 动物监测记录 ===
export interface AnimalRecord {
  time: string;
  animalId: string;
  behavior: string;
  temperature: string;
  heartRate: number;
  status: '正常' | '异常';
}

// === 姿态记录 ===
export interface PostureRecord {
  time: string;
  animalId: string;
  posture: string;
  confidence: number;
  duration: number;
}

// === 步数统计 ===
export interface StepStats {
  todaySteps: number;
  walkingDistance: number;
  activeTime: number;
  stepFrequency: number;
}

// === 小时步数 ===
export interface HourlyStep {
  hour: string;
  steps: number;
  distance: number;
  activeTime: number;
}

// === 历史数据 ===
export interface HistoryRecord {
  time: string;
  animalId: string;
  behavior: string;
  steps: number;
  location: string;
  status: '正常' | '异常';
}

// === 统计 ===
export interface Stats {
  totalAnimals: number;
  normal: number;
  alert: number;
  dataReceived: number;
}

// === 分析 ===
export interface Analysis {
  healthAlerts: number;
  postureAlerts: number;
  stepAlerts: number;
  locationAlerts: number;
}

// === 健康报告 ===
export interface HealthReport {
  totalAnimals: number;
  normalAnimals: number;
  alertAnimals: number;
}

// === 通知 ===
export interface Notification {
  show: boolean;
  type: NotificationType;
  title: string;
  message: string;
}

// === 过滤条件 ===
export interface DataFilter {
  startDate: string;
  endDate: string;
  dataType: string;
}
