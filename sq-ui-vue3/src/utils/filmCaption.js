/** 胶片边框信息条：日期 / 地点文案 */

const ESTIMATED_LOCATION = new Set(['time_interp', 'ai_landmark', 'region_center'])

export function formatFilmDate(time) {
  if (!time) return ''
  const d = new Date(typeof time === 'string' ? time.replace(/-/g, '/') : time)
  if (Number.isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
}

export function formatFilmLocation(photo) {
  if (!photo) return ''
  const parts = [photo.province, photo.city, photo.district].filter(Boolean)
  let text = parts.length ? parts.join(' · ') : (photo.address || '')
  if (!text && photo.latitude != null && photo.longitude != null) {
    text = `${Number(photo.latitude).toFixed(4)}, ${Number(photo.longitude).toFixed(4)}`
  }
  if (!text) return ''
  if (ESTIMATED_LOCATION.has(photo.locationSource)) {
    return `${text} · 估`
  }
  return text
}

export function formatFilmExifLine(photo) {
  if (!photo) return ''
  const bits = []
  if (photo.cameraModel) bits.push(String(photo.cameraModel).trim())
  if (photo.aperture) bits.push(String(photo.aperture).trim())
  if (photo.shutterSpeed) bits.push(String(photo.shutterSpeed).trim())
  if (photo.iso != null && photo.iso !== '') bits.push(`ISO ${photo.iso}`)
  return bits.join(' · ')
}

export function buildFilmCaption(photo, { withExif = false } = {}) {
  const date = formatFilmDate(photo?.shootTime)
  const place = formatFilmLocation(photo)
  const primary = [date, place].filter(Boolean).join('  ·  ')
  const exif = withExif ? formatFilmExifLine(photo) : ''
  return { primary, exif, empty: !primary && !exif }
}
