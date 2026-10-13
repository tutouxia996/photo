/**
 * 分享链接工具：统一用公网域名生成可访问地址，避免出现内网 IP 与端口。
 *
 * 配置：VITE_APP_SHARE_BASE（如 https://album.pengorbit.top，勿带末尾 /）
 */

const DEFAULT_SHARE_BASE = 'https://album.pengorbit.top'

export function normalizeSharePath(path) {
  const p = String(path || '').trim()
  if (!p) return ''
  return p.startsWith('/') ? p : `/${p}`
}

/** 公网根地址（无端口）；优先环境变量 */
export function getShareBase() {
  const configured = String(import.meta.env.VITE_APP_SHARE_BASE || '').trim().replace(/\/$/, '')
  if (configured) return configured
  return DEFAULT_SHARE_BASE
}

/**
 * 生成可分享的完整链接（复制与二维码共用）。
 * 形如：https://album.pengorbit.top/#/share/{token}
 */
export function buildShareUrl(path) {
  const p = normalizeSharePath(path)
  if (!p) return ''
  return `${getShareBase()}/#${p}`
}
