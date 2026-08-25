import { getActiveScanProgress, getScanProgress } from '@/api/album/scan'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import useVideoProxyStore from '@/store/modules/videoProxy'

function hasScanPerm() {
  const p = useUserStore().permissions || []
  return p.includes('*:*:*') || p.includes('album:scan:run') || p.includes('album:scan:query') || p.includes('album:scan:list')
}

const empty = () => ({
  logId: null,
  pathId: null,
  pathName: '',
  status: 1,
  totalCount: 0,
  newCount: 0,
  skipCount: 0,
  failCount: 0,
  message: '',
  running: false
})

const useDiskScanStore = defineStore('diskScan', {
  state: () => ({
    progress: empty(),
    minimized: false,
    polling: false,
    timer: null,
    finishedAt: 0
  }),
  getters: {
    visible: (state) => {
      if (!hasScanPerm()) {
        return false
      }
      const p = state.progress
      return p.running || Number(p.status) === 0
        || (state.finishedAt && Date.now() - state.finishedAt < 12000)
    },
    percent: (state) => {
      const total = Number(state.progress.totalCount) || 0
      if (total <= 0) {
        return state.progress.running || Number(state.progress.status) === 0 ? 0 : 100
      }
      const processed = (Number(state.progress.newCount) || 0)
        + (Number(state.progress.skipCount) || 0)
        + (Number(state.progress.failCount) || 0)
      return Math.min(100, Math.max(0, Math.round((processed / total) * 100)))
    }
  },
  actions: {
    apply(log, extra = {}) {
      const prevRunning = this.progress.running || Number(this.progress.status) === 0
      this.progress = {
        ...empty(),
        ...(log || {}),
        logId: log?.logId ?? this.progress.logId,
        running: Number(log?.status) === 0,
        ...extra
      }
      const nowRunning = this.progress.running
      if (prevRunning && !nowRunning) {
        this.finishedAt = Date.now()
        // 扫描结束后会异步排队浏览档转码；全局拉起转码浮层轮询（不依赖是否停在扫描页）
        this.kickVideoProxyAfterScan()
      }
    },
    kickVideoProxyAfterScan() {
      try {
        const msg = String(this.progress.message || '')
        const hinted = /转码|浏览档|proxy/i.test(msg)
        // enqueueOnScan 默认开启：即使文案被截断，也短暂轮询一次，避免漏掉转码进度
        if (hinted || Number(this.progress.newCount) > 0 || Number(this.progress.skipCount) > 0) {
          const proxyStore = useVideoProxyStore()
          const p = proxyStore.progress
          const alreadyLive = p.running || Number(p.status) === 0 || Number(p.generating) > 0
          if (!alreadyLive) {
            proxyStore.apply({
              status: 0,
              running: true,
              message: hinted ? '扫描完成，正在衔接视频转码…' : '扫描完成，正在检查是否需要转码…',
              percent: 0,
              total: 0,
              done: 0,
              failed: 0,
              generating: 0,
              remaining: 0
            })
            proxyStore.finishedAt = 0
            proxyStore.minimized = false
          }
          proxyStore.startPolling()
        }
      } catch (e) {
        // ignore
      }
    },
    async refreshByLogId(logId) {
      if (!logId) return
      try {
        const res = await getScanProgress(logId)
        this.apply(res.data || {}, { logId })
      } catch (e) {
        if (!getToken()) {
          this.stopPolling()
        }
      }
    },
    async refreshActive() {
      if (!getToken() || !hasScanPerm()) {
        return
      }
      try {
        const res = await getActiveScanProgress()
        const log = res.data
        if (log && Number(log.status) === 0) {
          this.apply(log)
        } else if (this.progress.running || Number(this.progress.status) === 0) {
          // 进行中任务刚结束时 active 可能已空，用 logId 拉最终态（含「已排队转码」文案）
          if (this.progress.logId) {
            await this.refreshByLogId(this.progress.logId)
          }
        } else if (!this.progress.running) {
          if (this.progress.logId && Number(this.progress.status) === 0) {
            await this.refreshByLogId(this.progress.logId)
          }
        }
      } catch (e) {
        if (!getToken()) {
          this.stopPolling()
        }
      }
    },
    startPolling(logId) {
      if (!getToken() || !hasScanPerm()) {
        return
      }
      if (logId) {
        this.progress.logId = logId
        this.progress.running = true
        this.progress.status = 0
        this.finishedAt = 0
        this.minimized = false
      }
      if (this.polling) {
        this.refreshByLogId(this.progress.logId)
        return
      }
      this.polling = true
      const tick = () => {
        if (!this.polling || !getToken()) {
          return
        }
        const logIdNow = this.progress.logId
        const req = logIdNow ? this.refreshByLogId(logIdNow) : this.refreshActive()
        req.finally(() => {
          if (!this.polling) {
            return
          }
          const running = this.progress.running || Number(this.progress.status) === 0
          if (!running) {
            if (this.finishedAt && Date.now() - this.finishedAt < 12000) {
              this.timer = setTimeout(tick, 2000)
            } else {
              this.stopPolling()
            }
            return
          }
          this.timer = setTimeout(tick, 800)
        })
      }
      tick()
    },
    stopPolling() {
      this.polling = false
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
    },
    track(logId, meta = {}) {
      this.apply({
        status: 0,
        totalCount: 0,
        newCount: 0,
        skipCount: 0,
        failCount: 0,
        message: '正在启动扫描…'
      }, { logId, running: true, ...meta })
      this.startPolling(logId)
    },
    dismiss() {
      if (this.progress.running || Number(this.progress.status) === 0) {
        return
      }
      this.finishedAt = 0
      this.progress = empty()
      this.stopPolling()
    }
  }
})

export default useDiskScanStore
