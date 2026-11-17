/**
 * 相册媒体 URL：网格用小图，查看器用中图，下载/1:1/全景用原图。
 */

function withBase(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) return url
  return (import.meta.env.VITE_APP_BASE_API || '') + url
}

/** 网格 / 标记：静态 thumbUrl 或按需截帧 */
export function photoThumbSrc(item, resolveUrl = withBase) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (item.photoId) return resolveUrl('/album/photo/thumb/' + item.photoId)
  return ''
}

/** 查看器浏览档（约 800px）；缺失时后端回退小图 */
export function photoMediumSrc(item, resolveUrl = withBase) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?size=medium')
}

/** 原图 / 原视频 */
export function photoOriginalSrc(item, resolveUrl = withBase) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}
