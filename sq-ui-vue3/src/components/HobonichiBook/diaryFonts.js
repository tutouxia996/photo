/**
 * 日记文字字体：仅在日记页按需加载（勿挂全局 CSS，3.6MB TTF 会拖垮整站首屏）。
 * 文件放在 public/fonts/JingXiaopeng.ttf。
 */

/** 对外 id（存进 textbox.fontFamily） */
export const JING_FONT_ID = 'JingXiaopeng'

/** TTF name table 里的真实 family */
export const JING_FONT_REAL_NAME = '绿木屐JXP行楷'

/** 公共路径，不经 Vite 打包，避免进主包 */
const FONT_URL = '/fonts/JingXiaopeng.ttf'

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

/** 确保自定义字体已加入 document.fonts（可重复调用） */
export function ensureDiaryFonts() {
  if (typeof document === 'undefined' || !document.fonts) {
    return Promise.resolve(false)
  }
  if (loadedOk) return Promise.resolve(true)
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    injectFaceStyles(FONT_URL)
    const face = new FontFace(JING_FONT_ID, `url(${FONT_URL})`, {
      style: 'normal',
      weight: 'normal'
    })
    const loaded = await face.load()
    document.fonts.add(loaded)
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
