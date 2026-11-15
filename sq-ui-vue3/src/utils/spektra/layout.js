/**
 * Strip layout port of SpektraLab `FilmLayout.layout` / `overscan_layout`
 * (strip mode, carrier=none). GPL-3.0-or-later.
 *
 * 关键：片窗尺寸固定为格式表（135: 36.25×24.3mm），照片 cover 进片窗。
 * 不再用照片像素比去撑大 gateAcross，否则会顶进齿孔（用户反馈的问题）。
 */

import { FUJI_STRIPES, getFormat, PERF_135, PERF_KS65 } from './formats'
import { getEdgeLook, getEdgeText } from './stocks'

/**
 * @param {object} opts
 * @param {string} opts.stockId
 * @param {string} opts.formatId
 * @param {number} opts.frameW px of source image
 * @param {number} opts.frameH
 * @param {number} [opts.frameNo]
 * @param {boolean} [opts.holesWhite]
 * @param {string} [opts.dateText]
 * @param {string} [opts.placeText]
 */
export function buildStripLayout(opts) {
  const fmt = getFormat(opts.formatId)
  const look = getEdgeLook(opts.stockId)
  const gauge = fmt.perforated ? '135' : '120'
  const edgeText = getEdgeText(opts.stockId, gauge)

  const frameW = Math.max(1, opts.frameW | 0)
  const frameH = Math.max(1, opts.frameH | 0)
  const pictureIsWide = frameW >= frameH

  // 格式门的物理尺寸（mm）——与 overscan.cpp kFormats 一致
  const gateAlong = fmt.along
  const gateAcross = fmt.across

  // SpektraLab：竖图整条片竖过来（film runs down）
  const gateIsWide = gateAlong >= gateAcross
  const vertical = gateIsWide !== pictureIsWide

  const filmW = fmt.filmW
  // 片窗居中于片宽；与齿孔留出实测空隙（~0.55mm）
  // 齿孔：edge=2.0, across=2.8 → 占 2.0..4.8；gateT0 对 24.3 门 = (35-24.3)/2 = 5.35
  let gateT0 = 0.5 * (filmW - gateAcross)
  if (fmt.perforated) {
    const holeInner = PERF_135.edge + PERF_135.h // 4.8
    const minClearance = 0.45
    if (gateT0 < holeInner + minClearance) {
      // 极端宽画幅（如 sprocket）才可能；夹紧避免压齿孔
      gateT0 = holeInner + minClearance
    }
  }
  const gateS0 = 0

  const marginAlong = fmt.perforated ? (fmt.along < 20 ? 0.45 : 0.85) : 1.7
  const tLo = 0
  const tHi = filmW

  const perf = fmt.perforated ? { ...PERF_135 } : null
  const perfPhase = perf ? -3.2 : 0

  const holesWhite = opts.holesWhite !== false
  const frameNo = Math.max(0, Number(opts.frameNo) || 1)

  const canvasAlong = gateAlong + 2 * marginAlong
  const canvasAcross = tHi - tLo
  const sLo = -marginAlong
  const sHi = gateAlong + marginAlong

  const stripes =
    look.stripes135 && fmt.perforated
      ? {
          g: FUJI_STRIPES.g,
          gw: FUJI_STRIPES.gw,
          r: FUJI_STRIPES.r,
          rw: FUJI_STRIPES.rw
        }
      : null

  // 仅用于参考：源图像素对应的 mm/px（按片窗长边）
  const px = Math.max(fmt.along, fmt.across) / Math.max(frameW, frameH)

  return {
    fmt,
    look,
    edgeText,
    gauge,
    px,
    vertical,
    filmW,
    gateAlong,
    gateAcross,
    gateS0,
    gateT0,
    marginAlong,
    tLo,
    tHi,
    sLo,
    sHi,
    canvasAlong,
    canvasAcross,
    perf,
    perfPhase,
    holesWhite,
    frameNo,
    stripes,
    dateText: opts.dateText || '',
    placeText: opts.placeText || '',
    picture: {
      s0: gateS0,
      t0: gateT0,
      along: gateAlong,
      across: gateAcross
    }
  }
}

/**
 * Map film (s along, t across) → canvas pixels.
 * Landscape: strip horizontal (s→x, t→y).
 * Portrait: strip upright (t→x, s→y).
 */
export function filmToCanvasPx(L, ppm) {
  if (L.vertical) {
    return {
      x: (t) => (t - L.tLo) * ppm,
      y: (s) => (s - L.sLo) * ppm,
      mapS: (s) => (s - L.sLo) * ppm,
      mapT: (t) => (t - L.tLo) * ppm,
      w: L.canvasAcross * ppm,
      h: L.canvasAlong * ppm,
      vertical: true
    }
  }
  return {
    x: (s) => (s - L.sLo) * ppm,
    y: (t) => (t - L.tLo) * ppm,
    mapS: (s) => (s - L.sLo) * ppm,
    mapT: (t) => (t - L.tLo) * ppm,
    w: L.canvasAlong * ppm,
    h: L.canvasAcross * ppm,
    vertical: false
  }
}

export { PERF_KS65 }
