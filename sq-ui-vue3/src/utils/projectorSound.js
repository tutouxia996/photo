/**
 * 反转片放映机换片音效
 * 素材：BigSoundBank「Slide projector #3」(Joseph Sardin) — CC0
 * https://bigsoundbank.com/slide-projector-3-s2965.html
 *
 * 通过 Vite 打进 /assets/*.mp3（合并部署时 Spring Security 已放行 /assets/**）。
 * public/sfx 作回退，避免旧包缺资源时完全无声。
 */

import slideSfxUrl from '@/assets/sfx/slide-projector.mp3'

const FALLBACK_SRC = `${import.meta.env.BASE_URL}sfx/slide-projector.mp3`
const SRC = slideSfxUrl || FALLBACK_SRC

let unlocked = false
/** 预加载用；真正播放用 clone，避免互相打断 */
let preloadEl = null

function getPreload() {
  if (typeof window === 'undefined') return null
  if (!preloadEl) {
    preloadEl = new Audio(SRC)
    preloadEl.preload = 'auto'
    preloadEl.volume = 0.85
  }
  return preloadEl
}

/** 浏览器需用户手势后才能出声；只做一次静音解锁 + 预加载 */
export function unlockProjectorSound() {
  if (typeof window === 'undefined') return
  const a = getPreload()
  if (!a) return

  if (unlocked) return
  unlocked = true

  const prev = a.volume
  a.volume = 0
  const p = a.play()
  if (p && typeof p.then === 'function') {
    p.then(() => {
      a.pause()
      a.currentTime = 0
      a.volume = prev
    }).catch(() => {
      a.volume = prev
    })
  } else {
    a.volume = prev
  }
}

/**
 * 播放完整换片机械声。
 * kind 保留兼容；录音已含整段机械动作，只在 close 播一次。
 * @param {'close'|'drop'|'open'|string} [kind]
 */
export function playProjectorSound(kind = 'close') {
  if (typeof window === 'undefined') return
  if (kind !== 'close') return

  if (!unlocked) unlockProjectorSound()

  try {
    const base = getPreload()
    const a = base ? /** @type {HTMLAudioElement} */ (base.cloneNode(true)) : new Audio(SRC)
    a.volume = 0.85
    a.currentTime = 0
    const p = a.play()
    if (p && typeof p.catch === 'function') {
      p.catch(() => {
        const fallback = new Audio(SRC)
        fallback.volume = 0.85
        fallback.play().catch(() => {})
      })
    }
  } catch (_) { /* ignore */ }
}
