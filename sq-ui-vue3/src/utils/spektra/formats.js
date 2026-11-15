/**
 * Ported from SpektraLab `engine/src/pipeline/overscan.cpp` `kFormats` / KS constants
 * and `app/.../Model/FilmEdge.swift` format table.
 * Source: https://github.com/JamesQiu2005/SpektraLab (GPL-3.0-or-later)
 */

export const FORMATS = {
  '135': { id: '135', filmW: 35.0, across: 24.3, along: 36.25, perforated: true, label: '135' },
  '135_half': { id: '135_half', filmW: 35.0, across: 24.0, along: 18.0, perforated: true, label: 'Half' },
  '135_xpan': { id: '135_xpan', filmW: 35.0, across: 24.0, along: 65.0, perforated: true, label: 'XPan' },
  '135_sprocket': { id: '135_sprocket', filmW: 35.0, across: 36.0, along: 72.0, perforated: true, label: 'Full Width' },
  '120_645': { id: '120_645', filmW: 61.0, across: 56.0, along: 41.5, perforated: false, label: '645' },
  '120_6x6': { id: '120_6x6', filmW: 61.0, across: 56.0, along: 56.0, perforated: false, label: '6×6' },
  '120_6x7': { id: '120_6x7', filmW: 61.0, across: 56.0, along: 69.5, perforated: false, label: '6×7' },
  '120_6x8': { id: '120_6x8', filmW: 61.0, across: 56.0, along: 76.0, perforated: false, label: '6×8' },
  '120_6x9': { id: '120_6x9', filmW: 61.0, across: 56.0, along: 84.0, perforated: false, label: '6×9' },
  '120_6x12': { id: '120_6x12', filmW: 61.0, across: 56.0, along: 112.0, perforated: false, label: '6×12' },
  '120_6x17': { id: '120_6x17', filmW: 61.0, across: 56.0, along: 168.0, perforated: false, label: '6×17' }
}

/** 135 KS perforations (overscan.cpp) */
export const PERF_135 = {
  pitch: 4.75,
  w: 1.98,
  h: 2.8,
  edge: 2.0,
  r: 0.5
}

/** 65mm KS on slit 120 (SMPTE 145 / API-SPEC §21.3) */
export const PERF_KS65 = {
  pitch: 4.75,
  w: 1.981,
  h: 2.794,
  edge: 0.985,
  r: 0.51
}

/** Fujifilm factory stripes (overscan.cpp) */
export const FUJI_STRIPES = {
  g: 2.83,
  gw: 0.33,
  r: 3.73,
  rw: 0.65,
  period: 38.0
}

/** FilmPainter developed colours (FilmCanvasView.swift) */
export const COLORS = {
  rebatePrint: '#0E0B09',
  holeWhite: '#F6F4F0',
  holeBlack: '#000000',
  dateLight: 'rgb(255, 102, 36)'
}

export function getFormat(id) {
  return FORMATS[id] || FORMATS['135']
}
