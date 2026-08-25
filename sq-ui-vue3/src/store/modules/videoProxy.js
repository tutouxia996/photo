import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { generateVideoProxies } from '@/api/album/scan'
import { getVideoProxyProgress } from '@/api/album/photo'

function hasVideoProxyPerm() {
  const p = useUserStore().permissions || []
  return p.includes('*:*:*') || p.includes('album:scan:run') || p.includes('album:photo:list')
}

const empty = () => ({
  status: 1,
  running: false,
  total: 0,
  done: 0,
  failed: 0,
  generating: 0,
  remaining: 0,
  percent: 0,
  skippedVideos: 0,
  message: '',
  currentJobs: [],
  failedJobs: []
})

const useVideoProxyStore = defineStore('videoProxy', {
  state: () => ({
    progress: empty(),
    minimized: false,
    polling: false,
    timer: null,
    finishedAt: 0
  }),
  getters: {
    visible: (state) => {
      if (!hasVideoProxyPerm()) {
        return false
      }
      const p = state.progress
      const total = Number(p.total) || 0
      if (p.running || Number(p.generating) > 0 || Number(p.status) === 0) {
        return true
      }
      if (total <= 0) {
        // 扫描衔接阶段可能尚无 total，保留短时可见
        if (state.finishedAt && Date.now() - state.finishedAt < 8000) {
          return true
        }
        return false
      }
      if (Number(p.status) === 2) {
        return true
      }
      if (state.finishedAt && Date.now() - state.finishedAt < 12000) {
        return true
      }
      return false
    }
  },
  actions: {
    apply(p) {
      const prevRunning = this.progress.running || Number(this.progress.status) === 0
      this.progress = { ...empty(), ...(p || {}) }
      const total = Number(this.progress.total) || 0
      const nowRunning = this.progress.running || Number(this.progress.status) === 0
      if (prevRunning && !nowRunning) {
        this.finishedAt = Date.now()
      } else if (Number(this.progress.status) === 2 && total > 0 && !nowRunning) {
        this.finishedAt = Date.now()
      }
    },
    async refresh() {
      if (!getToken() || !hasVideoProxyPerm()) {
        return
      }
      try {
        const res = await getVideoProxyProgress()
        this.apply(res.data || {})
      } catch (e) {
        if (!getToken()) {
          this.stopPolling()
        }
      }
    },
    startPolling() {
      if (!getToken() || !hasVideoProxyPerm()) {
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
          const p = this.progress
          const active = p.running || Number(p.generating) > 0 || Number(p.status) === 0
          let ms = 15000
          if (active) {
            ms = 1200
          } else if (this.finishedAt && Date.now() - this.finishedAt < 15000) {
            ms = 2000
          } else {
            this.stopPolling()
            return
          }
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
    async enqueue(pathId, force = false) {
      await generateVideoProxies(pathId, force)
      this.finishedAt = 0
      this.minimized = false
      this.startPolling()
      await this.refresh()
      return this.progress
    },
    dismiss() {
      this.finishedAt = 0
      this.progress = empty()
      this.stopPolling()
    }
  }
})

export default useVideoProxyStore
