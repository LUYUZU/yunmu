// 云牧智感 - 工具函数
export function formatTime(date) {
  const y = date.getFullYear()
  const mo = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}/${mo}/${d} ${h}:${mi}:${s}`
}
export function formatISO(date) { return date.toISOString().slice(0, 19) }
export function convertToCSV(data) {
  if (!data || !data.length) return ''
  const headers = Object.keys(data[0])
  const rows = [headers.join(',')]
  data.forEach(row => {
    rows.push(headers.map(k => {
      const v = row[k]
      if (typeof v === 'string' && (v.includes(',') || v.includes('"'))) return `"${v.replace(/"/g, '""')}"`
      return v
    }).join(','))
  })
  return rows.join('\n')
}
export function downloadFile(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename
  document.body.appendChild(a); a.click()
  document.body.removeChild(a); URL.revokeObjectURL(url)
}
export function getTabName(tab) {
  const names = {overview:'系统概览',animals:'动物监测',location:'北斗定位',posture:'姿态识别',steps:'步数统计',data:'数据管理',analysis:'分析报告'}
  return names[tab] || tab
}