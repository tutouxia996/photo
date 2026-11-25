/**
 * 日记本火车票图层桥接：
 * 渲染逻辑来自 FoskyM/train-ticket-generator（AGPL-3.0）。
 *
 * 图层：{ type:'ticket', id, space, x,y,w,h,rot, aspect, data, style, face }
 *
 * 注意：官方渲染器（含 pinyin-pro）改为懒加载，避免拖垮日记本首屏。
 */

import { receiptConfig } from '@/vendor/train-ticket/receiptConfig.js'

/** A5 单页宽高比（宽/高） */
const PAGE_ASPECT = 148 / 210

export const TICKET_CANVAS_W = 876
export const TICKET_CANVAS_H = 539
export const TICKET_ASPECT = TICKET_CANVAS_W / TICKET_CANVAS_H

export { FONT_FAMILY_OPTIONS } from '@/vendor/train-ticket/fonts.js'
export { receiptConfig }

function clampRect(x, y, w, h, { maxW = 0.92, maxH = 0.85 } = {}) {
  let nw = Math.min(maxW, Math.max(0.08, w || 0.32))
  let nh = Math.min(maxH, Math.max(0.06, h || 0.28))
  const nx = Math.min(1 - nw, Math.max(0, x || 0))
  const ny = Math.min(1 - nh, Math.max(0, y || 0))
  return { x: nx, y: ny, w: nw, h: nh }
}

export function defaultTicketData(seed = '') {
  const n = String(seed || Date.now()).replace(/\D/g, '').slice(-6) || '088888'
  return {
    id: `32537301731021L${n}`,
    redId: `Z160L${n}`,
    ticketOffice: '杭州东',
    startStation: '杭州东',
    endStation: '北京南',
    trainNumber: 'G176',
    date: '2024-10-21',
    time: '18:57',
    price: 623.0,
    seatType: '二等座',
    seatCarriage: '08',
    seatNumber: '08F',
    passengerName: '傅四霁',
    passengerId: '330102200401011234',
    seatTypeCustom: '二等座始发改签',
    checkGate: '18B',
    identity: 'adult',
    isDiscount: true,
    payMethod: ''
  }
}

export function defaultStyleConfig() {
  return JSON.parse(JSON.stringify(receiptConfig.defaultStyleConfig))
}

/** 兼容旧版日记字段 → 官方 TicketData */
export function normalizeTicketData(raw) {
  const base = defaultTicketData()
  const d = { ...(raw || {}) }

  if (d.ticketId && !d.id) d.id = d.ticketId
  if (d.tip && !d.ticketOffice) d.ticketOffice = d.tip
  if (d.from && !d.startStation) d.startStation = d.from
  if (d.to && !d.endStation) d.endStation = d.to
  if (d.train && !d.trainNumber) d.trainNumber = d.train
  if (d.coach != null && d.seatCarriage == null) d.seatCarriage = String(d.coach)
  if (d.seat && !d.seatNumber) d.seatNumber = d.seat
  if (d.passenger && !d.passengerName) d.passengerName = d.passenger
  if (d.idCard && !d.passengerId) d.passengerId = d.idCard
  if (d.gate && !d.checkGate) d.checkGate = d.gate
  if (d.passengerType && !d.identity) {
    const map = {
      成人: 'adult',
      学生: 'student',
      儿童: 'child',
      军人: 'military',
      残疾: 'disabled',
      优惠: 'adult'
    }
    d.identity = map[d.passengerType] || 'adult'
    if (d.passengerType === '优惠' || d.passengerType === '学生') d.isDiscount = true
  }
  if (d.payType && d.payMethod === undefined) {
    const map = { 微信: '微', 支付宝: '支', 现金: '现', 银行卡: '银' }
    d.payMethod = map[d.payType] || ''
  }
  if (typeof d.price === 'string') d.price = Number(d.price) || 0

  const out = { ...base, ...d }
  out.price = Number(out.price) || 0
  out.seatCarriage = String(out.seatCarriage ?? '08')
  out.seatNumber = String(out.seatNumber ?? '')
  out.checkGate = out.checkGate ?? ''
  out.seatTypeCustom = out.seatTypeCustom ?? ''
  out.identity = out.identity || 'adult'
  out.isDiscount = !!out.isDiscount
  out.payMethod = out.payMethod || ''
  return out
}

export function normalizeStyleConfig(raw) {
  const def = defaultStyleConfig()
  if (!raw) return def
  return {
    ...def,
    ...raw,
    fonts: { ...def.fonts, ...(raw.fonts || {}) },
    wearEffect: { ...def.wearEffect, ...(raw.wearEffect || {}) },
    showProtrusions: raw.showProtrusions ?? def.showProtrusions,
    backgroundOpacity: raw.backgroundOpacity ?? def.backgroundOpacity,
    qrContent: raw.qrContent ?? def.qrContent
  }
}

export function ticketHeightForWidth(w, spread = false) {
  const scale = spread ? 2 : 1
  return (w * scale * PAGE_ASPECT) / TICKET_ASPECT
}

export function defaultTicketSize(spread = false) {
  const w = spread ? 0.38 : 0.78
  const h = ticketHeightForWidth(w, spread)
  return { w, h, aspect: TICKET_ASPECT }
}

export function createTicketLayer({ id, pageX = 0.1, pageY = 0.18, rot = 0.01, data, style } = {}) {
  const size = defaultTicketSize(false)
  const box = clampRect(pageX, pageY, size.w, size.h)
  return {
    id: id || `tk_${Date.now().toString(36)}`,
    type: 'ticket',
    space: 'page',
    ...box,
    aspect: TICKET_ASPECT,
    rot: rot || 0,
    data: normalizeTicketData(data),
    style: normalizeStyleConfig(style),
    face: 'front'
  }
}

let rendererModPromise = null

function loadRenderer() {
  if (!rendererModPromise) {
    rendererModPromise = import('@/vendor/train-ticket/renderer2d.js').catch((err) => {
      rendererModPromise = null
      throw err
    })
  }
  return rendererModPromise
}

/**
 * 把官方渲染器画到指定 canvas（异步：背景图 + 二维码）
 */
export async function renderTicketCanvas(canvas, ticketData, styleConfig, face = 'front') {
  if (!canvas) throw new Error('no canvas')
  const { canvasWidth, canvasHeight, drawTicket, drawTicketBack } = await loadRenderer()
  canvas.width = canvasWidth
  canvas.height = canvasHeight
  const data = normalizeTicketData(ticketData)
  const style = normalizeStyleConfig(styleConfig)

  return new Promise((resolve, reject) => {
    let settled = false
    const done = () => {
      if (settled) return
      settled = true
      resolve(canvas)
    }
    const timer = setTimeout(done, 2500)
    const wrap = () => {
      clearTimeout(timer)
      done()
    }
    try {
      if (face === 'back') drawTicketBack(canvas, style, wrap)
      else drawTicket(canvas, data, style, wrap)
    } catch (e) {
      clearTimeout(timer)
      reject(e)
    }
  })
}

export async function bakeTicketImage(layer) {
  const cv = document.createElement('canvas')
  await renderTicketCanvas(cv, layer?.data, layer?.style, layer?.face || 'front')
  return cv
}

const ticketBakeCache = new Map()
const ticketBakePending = new Map()

function cacheKeyOf(layer) {
  return `${layer.id || ''}|${layer.face || 'front'}|${stableHash(layer.data)}|${stableHash(layer.style)}`
}

function stableHash(obj) {
  try {
    return JSON.stringify(obj || {})
  } catch {
    return String(obj)
  }
}

function roundRect(g, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2)
  g.beginPath()
  g.moveTo(x + rr, y)
  g.arcTo(x + w, y, x + w, y + h, rr)
  g.arcTo(x + w, y + h, x, y + h, rr)
  g.arcTo(x, y + h, x, y, rr)
  g.arcTo(x, y, x + w, y, rr)
  g.closePath()
}

/** 在页画布上绘制火车票（使用缓存的官方渲染结果） */
export function drawTicketLayerOnCtx(g, layer, W, H) {
  if (!layer || layer.type !== 'ticket') return
  const baked = ticketBakeCache.get(cacheKeyOf(layer))
  const x = (layer.x || 0) * W
  const y = (layer.y || 0) * H
  const w = Math.max(8, (layer.w || 0.4) * W)
  const h = Math.max(8, (layer.h || 0.25) * H)
  const rot = layer.rot || 0
  const r = Math.min(12, w * 0.028)

  g.save()
  g.translate(x + w / 2, y + h / 2)
  g.rotate(rot)
  g.translate(-w / 2, -h / 2)

  // 贴近纸面的轻微落影（几乎正下，很短）
  if (baked) {
    g.save()
    g.shadowColor = 'rgba(40, 32, 24, 0.22)'
    g.shadowBlur = Math.max(1.5, Math.min(3.5, w * 0.008))
    g.shadowOffsetX = 0
    g.shadowOffsetY = Math.max(0.8, Math.min(1.8, h * 0.008))
    g.drawImage(baked, 0, 0, w, h)
    g.restore()
    // 再画一遍本体，避免阴影糊进票面
    g.drawImage(baked, 0, 0, w, h)
  } else {
    g.fillStyle = 'rgba(173, 216, 230, 0.35)'
    roundRect(g, 0, 0, w, h, r)
    g.fill()
  }

  g.restore()
}

export function drawSpreadTicketOnPage(g, layer, W, H, side) {
  if (!layer || layer.type !== 'ticket') return
  const x0 = layer.x || 0
  const y0 = layer.y || 0
  const w0 = layer.w || 0.4
  const h0 = layer.h || 0.25
  const pageX0 = side === 'left' ? 0 : 0.5
  const pageX1 = side === 'left' ? 0.5 : 1
  if (x0 + w0 < pageX0 - 0.01 || x0 > pageX1 + 0.01) return
  const local = {
    ...layer,
    x: side === 'left' ? x0 * 2 : (x0 - 0.5) * 2,
    w: w0 * 2,
    y: y0,
    h: h0
  }
  g.save()
  g.beginPath()
  g.rect(0, 0, W, H)
  g.clip()
  drawTicketLayerOnCtx(g, local, W, H)
  g.restore()
}

/** 确保票面已烘焙；完成后回调（用于触发日记纸面 rebake） */
export function ensureTicketBake(layer, onReady) {
  if (!layer || layer.type !== 'ticket') return null
  const key = cacheKeyOf(layer)
  const hit = ticketBakeCache.get(key)
  if (hit) return hit
  if (ticketBakePending.has(key)) return null
  ticketBakePending.set(key, true)
  bakeTicketImage(layer)
    .then((cv) => {
      ticketBakeCache.set(key, cv)
      ticketBakePending.delete(key)
      onReady?.(cv, key)
    })
    .catch((err) => {
      console.warn('[ticket] bake failed', err)
      ticketBakePending.delete(key)
    })
  return null
}

export const styleFieldGroups = receiptConfig.styleFieldGroups

export const SEAT_TYPE_LIST = ['商务座', '一等座', '二等座', '无座', '硬座', '硬卧', '软卧']

export const IDENTITY_OPTIONS = [
  { label: '成人', value: 'adult' },
  { label: '学生', value: 'student' },
  { label: '儿童', value: 'child' },
  { label: '军人', value: 'military' },
  { label: '残疾', value: 'disabled' }
]

export const PAY_METHOD_OPTIONS = [
  { label: '微信', value: '微' },
  { label: '支付宝', value: '支' },
  { label: '现金', value: '现' },
  { label: '银行卡', value: '银' }
]
