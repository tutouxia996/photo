/**
 * 缩略图胶片合成队列：限制并发，避免一屏几十张同时 renderFilmStrip 卡死主线程。
 */

const MAX_CONCURRENT = 2
const queue = []
let active = 0

function pump() {
  while (active < MAX_CONCURRENT && queue.length) {
    const job = queue.shift()
    if (!job || job.cancelled) continue
    active += 1
    Promise.resolve()
      .then(() => job.run())
      .then(
        (v) => {
          if (!job.cancelled) job.resolve(v)
        },
        (e) => {
          if (!job.cancelled) job.reject(e)
        }
      )
      .finally(() => {
        active -= 1
        pump()
      })
  }
}

/**
 * @param {() => (void|Promise<void>)} run
 * @returns {{ promise: Promise<any>, cancel: () => void }}
 */
export function enqueueThumbPaint(run) {
  let cancelled = false
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  const job = {
    run,
    resolve,
    reject,
    get cancelled() {
      return cancelled
    }
  }
  queue.push(job)
  pump()
  return {
    promise,
    cancel() {
      cancelled = true
      const i = queue.indexOf(job)
      if (i >= 0) queue.splice(i, 1)
    }
  }
}
