/**
 * 时间展示工具。
 * 后端统一 LocalDateTime（无时区），JSON 形如 2026-11-06T14:30:00；
 * new Date() 会按浏览器本地时区解析，和项目「界面时间 = 本地时间」的口径一致。
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return '—'
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}