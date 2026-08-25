/**
 * 视频浏览档 URL（代理片在服务端 cache/proxy，不入库）。
 * 支持 480p30 / 720p30 / 1080p30。
 */
export function videoPlaySrc(photoId, quality = '480p', fps = 30) {
  if (!photoId) return ''
  const base = import.meta.env.VITE_APP_BASE_API || ''
  if (quality === 'original') {
    return `${base}/album/photo/media/${photoId}?original=true`
  }
  const q = encodeURIComponent(quality || '480p')
  return `${base}/album/photo/media/${photoId}?quality=${q}&fps=30`
}

export const VIDEO_QUALITY_OPTIONS = [
  { value: '480p', label: '480p（外网）' },
  { value: '720p', label: '720p' },
  { value: '1080p', label: '1080p' },
  { value: 'original', label: '原片' }
]
