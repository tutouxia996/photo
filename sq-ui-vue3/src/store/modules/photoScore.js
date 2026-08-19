import { scoreAlbumPhotos, getPhotoScoreProgress } from '@/api/photos/photo'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'

function hasPhotoScorePerm() {
  const p = useUserStore().permissions || []
  return p.includes('*:*:*') || p.includes('album:photo:list') || p.includes('album:photo:edit')
}

const empty = () => ({
  status: 1,
  albumId: null,
  albumName: '',
  total: 0,
  done: 0,
  passed: 0,
  failed: 0,
  skipped: 0,
  remaining: 0,
  percent: 0,
  running: false,
  currentFile: '',
  message: ''
})

const usePhotoScoreStore = defineStore('photoScore', {
  state: () => ({
    progress: empty(),
    minimized: false,
    polling: false,
    timer: null,
    finishedAt: 0
  }),
  getters: {
    visible: (state) => {
      if (!hasPhotoScorePerm()) {
        return false
      }
      const p = state.progress
      const st = Number(p.status)
      if (p.running || st === 0) {
        return true
      }
      if (st === 2) {
        return true
      }
      if (st === 1 && state.finishedAt && Date.now() - state.finishedAt < 12000) {
        return true
      }
      return false
    }
  },
  actions: {
    apply(p) {
      const prevRunning = this.progress.running || Number(this.progress.status) === 0
      this.progress = { ...empty(), ...(p || {}) }
      const nowRunning = this.progress.running || Number(this.progress.status) === 0
      if (prevRunning && !nowRunning && Number(this.progress.status) === 1) {
        this.finishedAt = Date.now()
      }
      if (Number(this.progress.status) === 2) {
        this.finishedAt = Date.now()
      }
    },
    async refresh() {
      if (!getToken() || !hasPhotoScorePerm()) {
        return
      }
      try {
        const res = await getPhotoScoreProgress()
        this.apply(res.data || {})
      } catch (e) {
        if (!getToken()) {
          this.stopPolling()
        }
      }
    },
    startPolling() {
      if (!getToken() || !hasPhotoScorePerm()) {
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
          const st = Number(p.status)
          const active = p.running || st === 0
          let ms = 15000
          if (active) {
            ms = 600
          } else if (this.finishedAt && Date.now() - this.finishedAt < 15000) {
            ms = 1500
          } else {
            this.stopPolling()
            return
          }
          this.timer = setTimeout(tick, ms)
        })
      }
      this.timer = setTimeout(tick, 600)
    },
    stopPolling() {
      this.polling = false
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
    },
    async start(albumId, data) {
      const res = await scoreAlbumPhotos(albumId, data)
      const body = res.data || {}
      this.apply(body.progress || body)
      this.finishedAt = 0
      this.minimized = false
      this.startPolling()
      return res
    },
    dismiss() {
      this.finishedAt = 0
      this.progress = empty()
      this.stopPolling()
    }
  }
})

export default usePhotoScoreStore
