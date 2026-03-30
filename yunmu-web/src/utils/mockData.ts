// =============================================
// 云牧智感 - 模拟数据生成
// =============================================
import type {
  Animal, AnimalRecord, PostureRecord, StepStats,
  HourlyStep, HistoryRecord, LocationPoint, Stats,
  Analysis, HealthReport,
} from '../types';
import { formatTime, BEHAVIORS, POSTURES, ANIMAL_TYPES } from './index';

const rand = (min: number, max: number) => Math.random() * (max - min) + min;
const randInt = (min: number, max: number) => Math.floor(rand(min, max));
const randItem = <T>(arr: readonly T[]): T => arr[randInt(0, arr.length)];
const animalId = (i: number) => `NO.${String(i).padStart(3, '0')}`;

export const generateAnimals = (count = randInt(10, 20)): Animal[] => {
  const alertCount = randInt(0, 3);
  return Array.from({ length: Math.min(count, 12) }, (_, i) => ({
    id:          animalId(i + 1),
    type:        randItem(ANIMAL_TYPES),
    status:      i < alertCount ? 'alert' : 'normal',
    temperature: rand(37, 40).toFixed(1),
    heartRate:   randInt(50, 100),
    steps:       randInt(0, 5000),
    behavior:    randItem(BEHAVIORS),
  }));
};

export const generateStats = (animals: Animal[]): Stats => ({
  totalAnimals: animals.length,
  normal:       animals.filter(a => a.status === 'normal').length,
  alert:        animals.filter(a => a.status === 'alert').length,
  dataReceived: randInt(500, 1500),
});

export const generateAnimalRecords = (animals: Animal[], animalId = ''): AnimalRecord[] => {
  const now = new Date();
  return Array.from({ length: 20 }, (_, i) => {
    const time = new Date(now.getTime() - i * 30 * 60 * 1000);
    const isAlert = Math.random() < 0.1;
    return {
      time:       formatTime(time),
      animalId:   animalId || randItem(animals)?.id || 'NO.001',
      behavior:   randItem(BEHAVIORS),
      temperature: rand(37, 40).toFixed(1),
      heartRate:  randInt(50, 100),
      status:     isAlert ? '异常' : '正常',
    };
  });
};

export const generatePostureRecords = (animals: Animal[], animalId = ''): PostureRecord[] => {
  const now = new Date();
  return Array.from({ length: 15 }, (_, i) => {
    const time = new Date(now.getTime() - i * 20 * 60 * 1000);
    return {
      time:      formatTime(time),
      animalId:  animalId || randItem(animals)?.id || 'NO.001',
      posture:   randItem(POSTURES),
      confidence: randInt(75, 100),
      duration:  randInt(5, 55),
    };
  });
};

export const generateStepStats = (): StepStats => ({
  todaySteps:      randInt(3000, 10000),
  walkingDistance: randInt(2000, 5000),
  activeTime:      randInt(60, 300),
  stepFrequency:   randInt(10, 40),
});

export const generateHourlyData = (): HourlyStep[] =>
  Array.from({ length: 24 }, (_, hour) => ({
    hour:       String(hour).padStart(2, '0'),
    steps:      randInt(50, 500),
    distance:   randInt(20, 200),
    activeTime: randInt(5, 40),
  }));

export const generateHistoryData = (animals: Animal[]): HistoryRecord[] => {
  const now = new Date();
  return Array.from({ length: 30 }, (_, i) => {
    const time = new Date(now.getTime() - i * 60 * 60 * 1000);
    return {
      time:      formatTime(time),
      animalId:  randItem(animals)?.id || 'NO.001',
      behavior:  randItem(BEHAVIORS),
      steps:     randInt(0, 1000),
      location:  `${(30 + Math.random() * 2).toFixed(4)}°N, ${(90 + Math.random() * 2).toFixed(4)}°E`,
      status:    Math.random() < 0.1 ? '异常' : '正常',
    };
  });
};

export const generateLocationData = (animals: Animal[]): LocationPoint[] =>
  animals.map(a => ({
    id:        a.id,
    latitude:  rand(29.0, 31.5),
    longitude: rand(88.0, 92.0),
    timestamp: formatTime(new Date()),
  }));

export const generateAnalysis = (): Analysis => ({
  healthAlerts:   randInt(0, 5),
  postureAlerts:  randInt(0, 8),
  stepAlerts:     randInt(0, 6),
  locationAlerts: randInt(0, 3),
});

export const generateHealthReport = (stats: Stats): HealthReport => ({
  totalAnimals:  stats.totalAnimals,
  normalAnimals: stats.normal,
  alertAnimals:  stats.alert,
});
