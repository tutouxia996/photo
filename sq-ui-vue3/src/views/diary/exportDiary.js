/**
 * 日记本导出：把有内容的纸面烘焙成图，再写入 PDF / Word。
 * 视觉内容（笔画/文字/贴图/美纹纸/车票）以页图形式保留。
 */
import { saveAs } from 'file-saver'
import { jsPDF } from 'jspdf'
import {
  Document,
  Packer,
  Paragraph,
  ImageRun,
  AlignmentType,
  PageBreak
} from 'docx'
import { paintBlank, makeCanvas } from '@/components/HobonichiBook/hobonichiEngine.js'
import {
  PAGE_ASPECT,
  ensureDiaryFonts,
  ensureMediaImage,
  hasContent,
  mediaBakeSrc,
  pageSide,
  partnerPageNo,
  paintLayers
} from '@/components/HobonichiBook/pageContent.js'
import { ensureMaskingTapeImage } from '@/components/HobonichiBook/maskingTape.js'
import { ensureTicketBake } from '@/components/HobonichiBook/trainTicket.js'

/** 导出分辨率：约 150dpi A5 */
const EXPORT_W = 1240
const EXPORT_H = Math.round(EXPORT_W / PAGE_ASPECT)

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

function contentOf(pagesMap, pageNo) {
  if (!pageNo || !pagesMap) return null
  if (pagesMap instanceof Map) return pagesMap.get(pageNo) || null
  return pagesMap[pageNo] || null
}

function collectAssetUrls(content) {
  const urls = []
  for (const l of content?.layers || []) {
    if (!l) continue
    if (l.type === 'media') {
      const u = mediaBakeSrc(l)
      if (u) urls.push(u)
      if (l.thumbSrc && l.thumbSrc !== u) urls.push(l.thumbSrc)
    }
  }
  return urls
}

function waitImage(url, timeoutMs = 12000) {
  return new Promise((resolve) => {
    if (!url) {
      resolve(null)
      return
    }
    const hit = ensureMediaImage(url)
    if (hit) {
      resolve(hit)
      return
    }
    let done = false
    const finish = (img) => {
      if (done) return
      done = true
      resolve(img || null)
    }
    const t = setTimeout(() => finish(null), timeoutMs)
    ensureMediaImage(url, (img) => {
      clearTimeout(t)
      finish(img)
    })
  })
}

function waitTape(timeoutMs = 8000) {
  return new Promise((resolve) => {
    const hit = ensureMaskingTapeImage()
    if (hit) {
      resolve(hit)
      return
    }
    let done = false
    const finish = (img) => {
      if (done) return
      done = true
      resolve(img || null)
    }
    const t = setTimeout(() => finish(null), timeoutMs)
    ensureMaskingTapeImage((img) => {
      clearTimeout(t)
      finish(img)
    })
  })
}

async function preloadTickets(content) {
  const tickets = (content?.layers || []).filter((l) => l && l.type === 'ticket')
  await Promise.all(
    tickets.map(
      (layer) =>
        new Promise((resolve) => {
          try {
            const hit = ensureTicketBake(layer, () => resolve())
            if (hit) {
              resolve()
              return
            }
            // 可能已有 pending 且未挂上回调：轮询缓存
            const t0 = Date.now()
            const timer = setInterval(() => {
              const again = ensureTicketBake(layer)
              if (again || Date.now() - t0 > 12000) {
                clearInterval(timer)
                resolve()
              }
            }, 120)
          } catch {
            resolve()
          }
        })
    )
  )
}

async function preloadPageAssets(content) {
  const urls = collectAssetUrls(content)
  await Promise.all(urls.map((u) => waitImage(u)))
  if ((content?.layers || []).some((l) => l?.type === 'tape')) {
    await waitTape()
  }
  await preloadTickets(content)
}

function paintPageOnce(canvas, pageNo, content, partnerContent) {
  const side = pageSide(pageNo) || 'right'
  paintBlank(canvas, side, 11 + pageNo)
  paintLayers(canvas, content, {
    pageSide: side,
    extraSpreadContent: side === 'right' ? partnerContent : null
  })
}

/**
 * 渲染单页为 JPEG dataURL（多刷几遍，等异步贴图/车票进缓存）
 */
async function renderPageDataUrl(pageNo, pagesMap) {
  const content = contentOf(pagesMap, pageNo)
  if (!hasContent(content)) return null

  const partner = partnerPageNo(pageNo)
  const partnerContent = contentOf(pagesMap, partner)
  await preloadPageAssets(content)
  if (partnerContent) await preloadPageAssets(partnerContent)

  const canvas = makeCanvas(EXPORT_W, EXPORT_H)
  // 车票/贴图可能在首刷后才进缓存，多刷保证导出完整
  for (let i = 0; i < 3; i++) {
    paintPageOnce(canvas, pageNo, content, partnerContent)
    if (i < 2) await sleep(120)
  }
  return canvas.toDataURL('image/jpeg', 0.92)
}

function dataUrlToUint8Array(dataUrl) {
  const base64 = dataUrl.split(',')[1] || ''
  const bin = atob(base64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

/** 收集有内容的页码（升序） */
export function listExportPageNos(pagesMap) {
  const nos = []
  if (!pagesMap) return nos
  const entries = pagesMap instanceof Map ? pagesMap.entries() : Object.entries(pagesMap)
  for (const [k, content] of entries) {
    const n = Number(k)
    if (!Number.isFinite(n) || n < 1) continue
    if (hasContent(content)) nos.push(n)
  }
  nos.sort((a, b) => a - b)
  return nos
}

async function renderAllPages(pagesMap, onProgress) {
  await ensureDiaryFonts()
  const nos = listExportPageNos(pagesMap)
  const images = []
  for (let i = 0; i < nos.length; i++) {
    const pageNo = nos[i]
    onProgress?.({ current: i + 1, total: nos.length, pageNo })
    const dataUrl = await renderPageDataUrl(pageNo, pagesMap)
    if (dataUrl) images.push({ pageNo, dataUrl })
  }
  return images
}

function safeFileBase(title) {
  const t = String(title || '相册日记').replace(/[\\/:*?"<>|]+/g, '_').trim()
  return t || '相册日记'
}

/** 导出 PDF（A5，一页纸面对应一页 PDF） */
export async function exportDiaryPdf({ pagesMap, title, onProgress } = {}) {
  const images = await renderAllPages(pagesMap, onProgress)
  if (!images.length) throw new Error('没有可导出的日记页')

  // A5 mm
  const pdf = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: [148, 210],
    compress: true
  })

  images.forEach((item, idx) => {
    if (idx > 0) pdf.addPage([148, 210], 'portrait')
    pdf.addImage(item.dataUrl, 'JPEG', 0, 0, 148, 210, undefined, 'FAST')
  })

  const name = `${safeFileBase(title)}.pdf`
  pdf.save(name)
  return { count: images.length, name }
}

/** 导出 Word（每页一张整页插图） */
export async function exportDiaryDocx({ pagesMap, title, onProgress } = {}) {
  const images = await renderAllPages(pagesMap, onProgress)
  if (!images.length) throw new Error('没有可导出的日记页')

  // A5：148mm x 210mm → DXA（1mm ≈ 56.7 DXA）
  const pageW = Math.round(148 * 56.7)
  const pageH = Math.round(210 * 56.7)
  // 页内图片宽（EMU：1 inch = 914400；96dpi 下 1px ≈ 9525 EMU）
  // docx ImageRun 用 px 变换：按约 96dpi 把 EXPORT_W 缩到可排版宽度
  const imgDisplayW = 480
  const imgDisplayH = Math.round(imgDisplayW / PAGE_ASPECT)

  const children = []
  images.forEach((item, idx) => {
    if (idx > 0) children.push(new Paragraph({ children: [new PageBreak()] }))
    children.push(
      new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          new ImageRun({
            type: 'jpg',
            data: dataUrlToUint8Array(item.dataUrl),
            transformation: { width: imgDisplayW, height: imgDisplayH },
            altText: {
              title: `第 ${item.pageNo} 页`,
              description: `${title || '日记'} 第 ${item.pageNo} 页`,
              name: `page-${item.pageNo}`
            }
          })
        ]
      })
    )
  })

  const doc = new Document({
    creator: '相册日记',
    title: title || '相册日记',
    sections: [
      {
        properties: {
          page: {
            size: { width: pageW, height: pageH },
            margin: {
              top: 360,
              bottom: 360,
              left: 360,
              right: 360
            }
          }
        },
        children
      }
    ]
  })

  const blob = await Packer.toBlob(doc)
  const name = `${safeFileBase(title)}.docx`
  saveAs(blob, name)
  return { count: images.length, name }
}
