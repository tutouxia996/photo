/**
 * 日记文字字体加载。
 * TTF 内嵌族名是「绿木屐JXP行楷」；对外 id 仍用 JingXiaopeng。
 */
import jingUrl from '@/assets/fonts/JingXiaopeng.ttf?url'

/** 对外 id（存进 textbox.fontFamily） */
export const JING_FONT_ID = 'JingXiaopeng'

/** TTF name table 里的真实 family */
export const JING_FONT_REAL_NAME = '绿木屐JXP行楷'

export const DIARY_TEXT_FONTS = [
  {
    id: 'default',
    label: '默认',
    css: '"Zen Kaku Gothic New", "Noto Sans SC", "Microsoft YaHei", sans-serif'
  },
  {
    id: JING_FONT_ID,
    label: '荆霄鹏',
    css: `"${JING_FONT_ID}", "${JING_FONT_REAL_NAME}", "Microsoft YaHei", sans-serif`
  }
]

const DEFAULT_TEXT_FONT = DIARY_TEXT_FONTS[0]

/** 解析 textbox 字体为 CSS font-family 串 */
export function resolveTextFontCss(fontFamily) {
  if (!fontFamily || fontFamily === 'default') return DEFAULT_TEXT_FONT.css
  const hit = DIARY_TEXT_FONTS.find((f) => f.id === fontFamily)
  return hit ? hit.css : DEFAULT_TEXT_FONT.css
}

let loadPromise = null
let loadedOk = false

function injectFaceStyles(srcUrl) {
  const id = 'diary-font-jingxiaopeng'
  if (document.getElementById(id)) return
  const style = document.createElement('style')
  style.id = id
  // 别名 + 真实族名都指向同一文件；主加载走 FontFace(JING_FONT_ID)
  style.textContent = `
@font-face {
  font-family: '${JING_FONT_ID}';
  src: url('${srcUrl}') format('truetype');
  font-weight: normal;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: '${JING_FONT_REAL_NAME}';
  src: url('${srcUrl}') format('truetype');
  font-weight: normal;
  font-style: normal;
  font-display: swap;
}
.diary-font-jing,
.diary-font-jing * {
  font-family: '${JING_FONT_ID}', '${JING_FONT_REAL_NAME}', 'Microsoft YaHei', sans-serif !important;
}`
  document.head.appendChild(style)
}

async function resolveFontUrl() {
  const candidates = [jingUrl, '/fonts/JingXiaopeng.ttf']
  let lastErr = null
  for (const url of candidates) {
    try {
      const res = await fetch(url)
      if (!res.ok) {
        lastErr = new Error(`HTTP ${res.status} for ${url}`)
        continue
      }
      // 只探活，真正装载用 url（避免 ArrayBuffer 被多次 FontFace 用坏）
      const len = Number(res.headers.get('content-length') || 0)
      if (len > 0 && len < 1000) {
        lastErr = new Error(`font too small: ${url}`)
        continue
      }
      // 部分代理不给 content-length，再读一小段确认不是 HTML 错误页
      if (!len) {
        const buf = await res.arrayBuffer()
        if (buf.byteLength < 1000) {
          lastErr = new Error(`font too small: ${url}`)
          continue
        }
      }
      return url
    } catch (e) {
      lastErr = e
    }
  }
  throw lastErr || new Error('font fetch failed')
}

/** 确保自定义字体已加入 document.fonts（可重复调用） */
export function ensureDiaryFonts() {
  if (typeof document === 'undefined' || !document.fonts) {
    return Promise.resolve(false)
  }
  if (loadedOk) return Promise.resolve(true)
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    const url = await resolveFontUrl()
    injectFaceStyles(url)

    // 只注册一次别名，成功即视为可用；不再用 fonts.check 当成败依据（易误判）
    const face = new FontFace(JING_FONT_ID, `url(${url})`, {
      style: 'normal',
      weight: 'normal'
    })
    const loaded = await face.load()
    document.fonts.add(loaded)
    try {
      await document.fonts.load(`24px "${JING_FONT_ID}"`, '汉字')
    } catch {
      /* load 探测失败也不挡：face.load 已成功 */
    }

    loadedOk = true
    return true
  })().catch((err) => {
    console.warn('[diary] JingXiaopeng font load failed', err)
    loadPromise = null
    loadedOk = false
    return false
  })

  return loadPromise
}

export function isDiaryFontReady() {
  return loadedOk
}
