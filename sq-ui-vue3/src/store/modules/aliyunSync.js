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
    timer: null
  }),
  getters: {
    visible: (state) => {
      if (!hasAliyunPerm()) {
        return false
      }
      const p = state.progress
      const st = Number(p.status)
      return st === 0 || st === 3 || Number(p.remaining) > 0 || p.running
    }
  },
  actions: {
    apply(p) {
      this.progress = { ...empty(), ...(p || {}) }
    },
    async refresh() {
      if (!getToken() || !hasAliyunPerm()) {
        return
      }
      try {
        const res = await getAliyunProgress()
        this.apply(res.data || {})
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
          const ms = st === 0 ? 800 : ((st === 3 || rem > 0) ? 2500 : 15000)
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
      const res = await runAliyunSync()
      this.apply(res.data || {})
      this.minimized = false
      this.startPolling()
    },
    async pause() {
      const res = await pauseAliyunSync()
      this.apply(res.data || {})
    },
    async resume() {
      const res = await resumeAliyunSync()
      this.apply(res.data || {})
      this.minimized = false
      this.startPolling()
    }
  }
})

export default useAliyunSyncStore
