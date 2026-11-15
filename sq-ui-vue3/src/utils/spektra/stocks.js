/**
 * Ported from SpektraLab `FilmEdgeStock.swift` — EdgeLook / EdgeWords tables
 * (measured strips, overscan.cpp kEdgeLooks). GPL-3.0-or-later.
 */

/** @typedef {'kodak'|'fujiSlide'|'fujiNeg'|'cine'|'ilford'} Edge135 */
/** @typedef {'kodak'|'fuji'} Edge120 */

/**
 * @param {object} o
 * @param {Edge135} o.k135
 * @param {Edge120} o.k120
 */
function look(o) {
  return {
    k135: o.k135,
    k120: o.k120,
    lightNumbers: !!o.lightNumbers,
    code: o.code || '',
    dx: o.dx ?? null,
    ink135: o.ink135 || [1, 0.8, 0.5],
    ink120: o.ink120 || [1, 0.8, 0.55],
    stripes: !!o.stripes,
    get stripes135() {
      return this.k135 === 'fujiNeg' || this.stripes
    }
  }
}

export const EDGE_LOOKS = {
  kodak_gold_200: look({ k135: 'kodak', k120: 'kodak', dx: 1548, ink120: [1, 0.83, 0.68] }),
  kodak_portra_160: look({ k135: 'kodak', k120: 'kodak', dx: 1534, ink135: [1, 0.82, 0.43] }),
  kodak_portra_400: look({ k135: 'kodak', k120: 'kodak', dx: 1277, ink120: [1, 0.83, 0.6] }),
  kodak_portra_800: look({ k135: 'kodak', k120: 'kodak', dx: 1278, ink135: [1, 0.74, 0.35] }),
  kodak_ultramax_400: look({ k135: 'kodak', k120: 'kodak', dx: 1313, ink135: [1, 0.8, 0.57] }),
  kodak_ektar_100: look({ k135: 'kodak', k120: 'kodak', dx: 1307, ink120: [1, 0.77, 0.46] }),
  kodak_ektachrome_100: look({
    k135: 'kodak',
    k120: 'kodak',
    dx: 382,
    ink135: [1, 0.95, 0.92],
    ink120: [1, 0.95, 0.92]
  }),
  fujifilm_c200: look({ k135: 'kodak', k120: 'kodak', lightNumbers: true, dx: 1550, ink135: [1, 0.77, 0.51] }),
  fujifilm_xtra_400: look({
    k135: 'fujiNeg',
    k120: 'fuji',
    dx: 628,
    ink135: [1, 0.95, 0.57],
    ink120: [1, 0.95, 0.57]
  }),
  fujifilm_pro_400h: look({
    k135: 'fujiNeg',
    k120: 'fuji',
    code: 'EFCDCD',
    dx: 584,
    ink135: [1, 0.78, 0.21],
    ink120: [1, 0.78, 0.21]
  }),
  fujifilm_provia_100f: look({
    k135: 'fujiSlide',
    k120: 'fuji',
    dx: 557,
    ink135: [1, 0.8, 0.32],
    ink120: [1, 0.65, 0.25]
  }),
  fujifilm_velvia_100: look({
    k135: 'fujiSlide',
    k120: 'fuji',
    dx: 523,
    ink135: [1, 0.89, 0.41],
    ink120: [1, 0.62, 0.18]
  }),
  kodak_tri_x_400: look({
    k135: 'kodak',
    k120: 'kodak',
    lightNumbers: true,
    ink135: [1, 1, 1],
    ink120: [1, 1, 1]
  }),
  kodak_tmax_100: look({ k135: 'kodak', k120: 'kodak', ink135: [1, 1, 1], ink120: [1, 1, 1] }),
  fujifilm_neopan_acros_100_ii: look({
    k135: 'fujiSlide',
    k120: 'fuji',
    ink135: [1, 1, 1],
    ink120: [1, 1, 1]
  }),
  ilford_hp5_plus_400: look({
    k135: 'ilford',
    k120: 'kodak',
    dx: 1753,
    ink135: [1, 1, 1],
    ink120: [1, 1, 1]
  }),
  kodak_vision3_50d: look({ k135: 'cine', k120: 'kodak', ink135: [0.86, 0.86, 1], ink120: [0.86, 0.86, 1] }),
  kodak_vision3_250d: look({ k135: 'cine', k120: 'kodak', ink135: [0.86, 0.86, 1], ink120: [0.86, 0.86, 1] }),
  kodak_vision3_200t: look({ k135: 'cine', k120: 'kodak', ink135: [0.86, 0.86, 1], ink120: [0.86, 0.86, 1] }),
  kodak_vision3_500t: look({ k135: 'cine', k120: 'kodak', ink135: [0.86, 0.86, 1], ink120: [0.86, 0.86, 1] })
}

export const EDGE_WORDS = {
  kodak_gold_200: { k135: 'KODAK GB 200-7', k120: 'KODAK 200' },
  kodak_portra_800: { k135: 'KODAK PORTRA 800-3', k120: 'KODAK PORTRA 800' },
  kodak_ultramax_400: { k135: 'KODAK GC 400', k120: 'KODAK GC 400' },
  kodak_ektachrome_100: { k135: 'KODAK E100', k120: 'KODAK E100' },
  fujifilm_c200: { k135: 'FUJI 200', k120: 'FUJI 200' },
  fujifilm_xtra_400: { k135: 'S-400', k120: 'FUJI S-400' },
  fujifilm_pro_400h: { k135: 'PRO400H', k120: 'FUJI PRO400H' },
  fujifilm_provia_100f: { k135: 'FUJI RDPIII', k120: 'FUJI RDPIII' },
  fujifilm_velvia_100: { k135: 'RVP100', k120: 'FUJI RVP100' },
  kodak_vision3_50d: { k135: 'EASTMAN 5203', k120: 'EASTMAN 5203' },
  kodak_vision3_250d: { k135: 'EASTMAN 5207', k120: 'EASTMAN 5207' },
  kodak_vision3_200t: { k135: 'EASTMAN 5213', k120: 'EASTMAN 5213' },
  kodak_vision3_500t: { k135: 'EASTMAN 5219', k120: 'EASTMAN 5219' },
  kodak_tri_x_400: { k135: 'KODAK 400TX', k120: 'KODAK 400TX' },
  kodak_tmax_100: { k135: 'KODAK 100TMX', k120: 'KODAK 100TMX' },
  fujifilm_neopan_acros_100_ii: { k135: 'FUJI 100 ACROS II', k120: 'FUJI 100 ACROS II' },
  ilford_hp5_plus_400: { k135: 'ILFORD HP5 PLUS', k120: 'ILFORD HP5 PLUS' },
  kodak_portra_160: { k135: 'KODAK PORTRA 160', k120: 'KODAK PORTRA 160' },
  kodak_portra_400: { k135: 'KODAK PORTRA 400', k120: 'KODAK PORTRA 400' },
  kodak_ektar_100: { k135: 'KODAK EKTAR 100', k120: 'KODAK EKTAR 100' }
}

/** Picker list for album UI (subset of SpektraLab stocks + formats) */
export const STOCK_PRESETS = [
  { id: 'kodak_portra_400', formatId: '135', label: 'Portra 400 · 135' },
  { id: 'kodak_portra_160', formatId: '135', label: 'Portra 160 · 135' },
  { id: 'fujifilm_pro_400h', formatId: '135', label: 'Pro 400H · 135' },
  { id: 'fujifilm_xtra_400', formatId: '135', label: 'X-Tra 400 · 135' },
  { id: 'kodak_vision3_500t', formatId: '135', label: 'Vision3 500T · 135' },
  { id: 'ilford_hp5_plus_400', formatId: '135', label: 'HP5 Plus · 135' },
  { id: 'fujifilm_provia_100f', formatId: '120_6x9', label: 'Provia · 6×9' },
  { id: 'kodak_portra_400', formatId: '120_645', label: 'Portra 400 · 645' },
  { id: 'kodak_portra_400', formatId: '120_6x6', label: 'Portra 400 · 6×6' },
  { id: 'kodak_vision3_500t', formatId: '135_xpan', label: 'Vision3 500T · XPan' }
]

export function getEdgeLook(stockId) {
  return EDGE_LOOKS[stockId] || look({ k135: 'kodak', k120: 'kodak' })
}

export function getEdgeText(stockId, gauge = '135') {
  const own = EDGE_WORDS[stockId]
  if (own) return gauge === '120' ? own.k120 : own.k135
  return String(stockId || 'FILM').replace(/_/g, ' ').toUpperCase()
}

export function inkCss(rgb, printScale = 0.86) {
  const [r, g, b] = rgb
  const R = Math.round(r * printScale * 255)
  const G = Math.round(g * printScale * 255)
  const B = Math.round(b * printScale * 255)
  return `rgb(${R},${G},${B})`
}

export function getStockPreset(presetKey) {
  const hit = STOCK_PRESETS.find(s => `${s.id}|${s.formatId}` === presetKey)
  return hit || STOCK_PRESETS[0]
}

export function presetKey(stockId, formatId) {
  return `${stockId}|${formatId}`
}
