// ========================================
// 云牧智感 - API 配置
// ========================================
export const API_CONFIG = {
  BASE_URL: 'http://localhost:8080/api',
  ML_SERVICE_URL: 'http://localhost:5000/api',
  // WebSocket 鉴权令牌：与后端 yunmu.websocket.auth-token 一致。
  // 留空表示后端未开启 WS 鉴权（本地演示默认）
  WS_TOKEN: import.meta.env?.VITE_WS_TOKEN || ''
}
