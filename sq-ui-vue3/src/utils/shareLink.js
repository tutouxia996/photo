/**
 * 分享链接工具：统一用当前可访问的公网地址生成链接，避免出现内网地址。
 *
 * 配置：VITE_APP_SHARE_BASE（如 http://101.200.88.62:8080，勿带末尾 /）
 */

const DEFAULT_SHARE_BASE = 'http://101.200.88.62:8080'

export function normalizeSharePath(path) {
  const p = String(path || '').trim()
  if (!p) return ''
  return p.startsWith('/') ? p : `/${p}`
}

/** 公网根地址；优先环境变量 */
export function getShareBase() {
  const configured = String(import.meta.env.VITE_APP_SHARE_BASE || '').trim().replace(/\/$/, '')
  if (configured) return configured
  return DEFAULT_SHARE_BASE
}

/**
 * 生成可分享的完整链接（复制与二维码共用）。
 * 形如：http://101.200.88.62:8080/#/share/{token}
 */
export function buildShareUrl(path) {
  const p = normalizeSharePath(path)
  if (!p) return ''
  return `${getShareBase()}/#${p}`
}
