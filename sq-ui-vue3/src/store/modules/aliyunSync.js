import { getAliyunProgress, runAliyunSync, pauseAliyunSync, resumeAliyunSync } from '@/api/album/aliyun'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'

function hasAliyunPerm() {
  const p = useUserStore().permissions || []
  return p.includes('*:*:*') || p.includes('album:aliyun:query') || p.includes('album:aliyun:run')
}

const empty = () => ({
  status: 1,
  phase: 'done',
  total: 0,
  needDownload: 0,
  downloaded: 0,
  skipped: 0,
  failed: 0,
  remaining: 0,
  percent: 0,
  message: '',
  albumCount: 0,
  albumIndex: 0,
  currentAlbumName: '',
  overallNeed: 0,
  overallRemaining: 0,
  overallDownloaded: 0,
  overallSkipped: 0,
  overallFailed: 0,
  albumsPending: 0,
  paused: false,
  running: false,
  canPause: false,
  canResume: false,
  currentFiles: [],
  failedFiles: []
})

const useAliyunSyncStore = defineStore('aliyunSync', {
  state: () => ({
    progress: empty(),
    minimized: false,
    polling: false,
    timer: null,
    /** 刚结束时短暂保留浮层，便于看到完成态 */
    finishedAt: 0,
    /** 启动/继续请求尚未返回时，忽略轮询到的空闲快照，避免冲掉乐观进度 */
    pendingStart: false
  }),
  getters: {
    visible: (state) => {
      if (!hasAliyunPerm()) {
        return false
      }
      const p = state.progress
      const st = Number(p.status)
      if (st === 0 || st === 3 || Number(p.remaining) > 0 || p.running) return true
      if (state.finishedAt && Date.now() - state.finishedAt < 12000) return true
      return false
    }
  },
  actions: {
    apply(p) {
      const prevLive = this.progress.running || Number(this.progress.status) === 0
      this.progress = { ...empty(), ...(p || {}) }
      const nowLive = this.progress.running || Number(this.progress.status) === 0
      if (prevLive && !nowLive) {
        this.finishedAt = Date.now()
      }
    },
    isLiveProgress(p) {
      if (!p) return false
      const st = Number(p.status)
      return !!(p.running || st === 0 || st === 3 || Number(p.remaining) > 0)
    },
    async refresh() {
      if (!getToken() || !hasAliyunPerm()) {
        return
      }
      try {
        const res = await getAliyunProgress()
        const data = res.data || {}
        if (this.pendingStart && !this.isLiveProgress(data)) {
          return
        }
        this.apply(data)
      } catch (e) {
        if (!getToken()) {
          this.stopPolling()
        }
      }
    },
    startPolling() {
      if (!getToken() || !hasAliyunPerm()) {
        return
      }
      if (this.polling) {
        this.refresh()
        return
      }
      this.polling = true
      this.refresh()
      const tick = () => {
        if (!this.polling || !getToken()) {
          return
        }
        this.refresh().finally(() => {
          if (!this.polling) {
            return
          }
          const st = Number(this.progress.status)
          const rem = Number(this.progress.remaining) || 0
          const live = this.progress.running || st === 0 || this.pendingStart
          const ms = live ? 800 : ((st === 3 || rem > 0) ? 2500 : 15000)
          this.timer = setTimeout(tick, ms)
        })
      }
      this.timer = setTimeout(tick, 800)
    },
    stopPolling() {
      this.polling = false
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
    },
    async start() {
      this.finishedAt = 0
      this.minimized = false
      this.pendingStart = true
      // 先本地标为进行中，避免等接口返回前页面/浮层无反馈
      this.apply({
        status: 0,
        running: true,
        phase: 'listing',
        message: '正在启动同步…',
        percent: 0,
        canPause: true,
        canResume: false
      })
      this.startPolling()
      try {
        const res = await runAliyunSync()
        this.apply(res.data || {})
      } catch (e) {
        this.apply({
          status: 2,
          running: false,
          phase: 'failed',
          message: e?.message || '启动同步失败',
          canPause: false,
          canResume: false
        })
        throw e
      } finally {
        this.pendingStart = false
      }
    },
    async pause() {
      const res = await pauseAliyunSync()
      this.apply(res.data || {})
    },
    async resume() {
      this.finishedAt = 0
      this.minimized = false
      this.pendingStart = true
      this.apply({
        ...this.progress,
        status: 0,
        running: true,
        phase: 'downloading',
        message: this.progress.message || '继续下载…',
        canPause: true,
        canResume: false
      })
      this.startPolling()
      try {
        const res = await resumeAliyunSync()
        this.apply(res.data || {})
      } catch (e) {
        this.apply({
          ...this.progress,
          status: 2,
          running: false,
          phase: 'failed',
          message: e?.message || '继续下载失败'
        })
        throw e
      } finally {
        this.pendingStart = false
      }
    }
  }
})

export default useAliyunSyncStore
