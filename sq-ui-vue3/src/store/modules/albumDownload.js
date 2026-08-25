import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { startAlbumDownload, getAlbumDownloadProgress, albumDownloadFileUrl } from '@/api/photos/album'
import axios from 'axios'
import { saveAs } from 'file-saver'
import { ElMessage } from 'element-plus'

function hasDownloadPerm() {
  const p = useUserStore().permissions || []
  return p.includes('*:*:*')
    || p.includes('album:album:query')
    || p.includes('album:album:list')
    || p.includes('album:photo:list')
}

const empty = () => ({
  status: 1,
  running: false,
  phase: 'done',
  taskId: null,
  albumId: null,
  albumName: '',
  fileName: '',
  total: 0,
  packed: 0,
  skipped: 0,
  percent: 0,
  bytesTotal: 0,
  bytesWritten: 0,
  currentFile: '',
  message: '',
  /** client: album | items */
  mode: 'album',
  done: 0,
  failed: 0
})

const useAlbumDownloadStore = defineStore('albumDownload', {
  state: () => ({
    progress: empty(),
    minimized: false,
    polling: false,
    timer: null,
    finishedAt: 0,
    transferring: false,
    abortController: null
  }),
  getters: {
    visible: (state) => {
      if (!hasDownloadPerm()) return false
      const p = state.progress
      if (p.running || state.transferring || Number(p.status) === 0) return true
      if (p.phase === 'ready' || p.phase === 'transferring') return true
      if (Number(p.status) === 2) return true
      if (state.finishedAt && Date.now() - state.finishedAt < 12000) return true
      return false
    }
  },
  actions: {
    apply(p) {
      const prevRunning = this.progress.running || Number(this.progress.status) === 0
      this.progress = { ...empty(), mode: this.progress.mode || 'album', ...(p || {}) }
      const nowRunning = this.progress.running || Number(this.progress.status) === 0
      if (prevRunning && !nowRunning && this.progress.phase !== 'ready') {
        this.finishedAt = Date.now()
      }
    },
    async refresh() {
      if (!getToken() || !hasDownloadPerm()) return
      if (this.progress.mode === 'items') return
      try {
        const res = await getAlbumDownloadProgress()
        const data = res.data || {}
        this.apply({ ...data, mode: 'album' })
        if (data.phase === 'ready' && data.taskId && !this.transferring) {
          this.fetchReadyFile(data.taskId, data.fileName)
        }
      } catch (e) {
        if (!getToken()) this.stopPolling()
      }
    },
    startPolling() {
      if (!getToken() || !hasDownloadPerm()) return
      if (this.polling) {
        this.refresh()
        return
      }
      this.polling = true
      const tick = () => {
        if (!this.polling || !getToken()) return
        this.refresh().finally(() => {
          if (!this.polling) return
          const p = this.progress
          const active = p.running || Number(p.status) === 0 || p.phase === 'ready' || this.transferring
          if (active) {
            this.timer = setTimeout(tick, 800)
          } else if (this.finishedAt && Date.now() - this.finishedAt < 12000) {
            this.timer = setTimeout(tick, 2000)
          } else {
            this.stopPolling()
          }
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
    async startAlbum(albumId, albumName) {
      if (!albumId) return
      if (this.progress.running || this.transferring) {
        ElMessage.warning('已有下载任务进行中，请稍候')
        this.startPolling()
        return
      }
      this.finishedAt = 0
      this.minimized = false
      this.apply({
        status: 0,
        running: true,
        phase: 'packing',
        albumId,
        albumName: albumName || '',
        message: '正在启动打包…',
        percent: 0,
        mode: 'album'
      })
      try {
        const res = await startAlbumDownload(albumId)
        this.apply({ ...(res.data || {}), mode: 'album' })
        this.startPolling()
        ElMessage.success('已开始打包，可在右下角查看进度')
      } catch (e) {
        this.apply({
          status: 2,
          running: false,
          phase: 'failed',
          albumName: albumName || '',
          message: e?.message || '启动下载失败',
          mode: 'album'
        })
        this.finishedAt = Date.now()
      }
    },
    async fetchReadyFile(taskId, fileName) {
      if (!taskId || this.transferring) return
      this.transferring = true
      this.apply({
        ...this.progress,
        phase: 'transferring',
        running: true,
        status: 0,
        message: '正在下载到本地…',
        percent: Math.max(Number(this.progress.percent) || 0, 99)
      })
      const url = import.meta.env.VITE_APP_BASE_API + albumDownloadFileUrl(taskId)
      try {
        const res = await axios({
          method: 'get',
          url,
          responseType: 'blob',
          timeout: 0,
          headers: { Authorization: 'Bearer ' + getToken() },
          onDownloadProgress: (evt) => {
            const total = evt.total || Number(this.progress.bytesWritten) || 0
            const loaded = evt.loaded || 0
            let pct = 99
            if (total > 0) {
              pct = Math.min(99, Math.round((loaded / total) * 100))
            }
            this.progress = {
              ...this.progress,
              phase: 'transferring',
              running: true,
              status: 0,
              bytesTotal: total || this.progress.bytesTotal,
              bytesWritten: loaded,
              percent: pct,
              message: total > 0
                ? `正在下载 ${formatBytes(loaded)} / ${formatBytes(total)}`
                : `正在下载 ${formatBytes(loaded)}`
            }
          }
        })
        const data = res.data
        const type = data?.type || ''
        if (type.includes('application/json') || type.includes('text/')) {
          try {
            const text = await data.text()
            const obj = JSON.parse(text)
            throw new Error(obj.msg || '下载失败')
          } catch (err) {
            throw err instanceof Error ? err : new Error('下载失败')
          }
        }
        saveAs(data, fileName || this.progress.fileName || 'album.zip')
        this.apply({
          ...this.progress,
          status: 1,
          running: false,
          phase: 'done',
          percent: 100,
          message: '下载完成'
        })
        this.finishedAt = Date.now()
        ElMessage.success('相册下载完成')
      } catch (e) {
        console.error(e)
        this.apply({
          ...this.progress,
          status: 2,
          running: false,
          phase: 'failed',
          message: e?.message || '下载到本地失败'
        })
        this.finishedAt = Date.now()
        ElMessage.error(e?.message || '下载失败')
      } finally {
        this.transferring = false
      }
    },
    /** 选中项逐个下载原图/原视频（不挡页面） */
    async startItems(items, buildUrl) {
      if (!items?.length) return
      if (this.progress.running || this.transferring) {
        ElMessage.warning('已有下载任务进行中，请稍候')
        return
      }
      this.finishedAt = 0
      this.minimized = false
      this.transferring = true
      const total = items.length
      this.apply({
        status: 0,
        running: true,
        phase: 'transferring',
        mode: 'items',
        total,
        done: 0,
        failed: 0,
        packed: 0,
        percent: 0,
        message: `正在下载原文件 0/${total}`,
        albumName: '',
        fileName: ''
      })
      ElMessage.success(total === 1 ? '开始下载原文件' : `开始下载 ${total} 项原文件`)
      let done = 0
      let failed = 0
      try {
        for (const item of items) {
          const name = item.fileName || `photo_${item.photoId}`
          this.progress = {
            ...this.progress,
            currentFile: name,
            message: `正在下载 ${done + 1}/${total}：${name}`,
            percent: Math.min(99, Math.round((done / total) * 100))
          }
          try {
            const url = buildUrl(item)
            const res = await axios({
              method: 'get',
              url,
              responseType: 'blob',
              timeout: 0,
              headers: { Authorization: 'Bearer ' + getToken() },
              onDownloadProgress: (evt) => {
                const base = (done + failed) / total
                const fileShare = 1 / total
                let filePct = 0
                if (evt.total > 0) {
                  filePct = Math.min(1, (evt.loaded || 0) / evt.total)
                }
                const pct = Math.min(99, Math.round((base + fileShare * filePct) * 100))
                const loaded = evt.loaded || 0
                const totalBytes = evt.total || 0
                this.progress = {
                  ...this.progress,
                  currentFile: name,
                  percent: pct,
                  bytesWritten: loaded,
                  bytesTotal: totalBytes,
                  message: totalBytes > 0
                    ? `正在下载 ${done + 1}/${total}：${name}（${formatBytes(loaded)} / ${formatBytes(totalBytes)}）`
                    : `正在下载 ${done + 1}/${total}：${name}`
                }
              }
            })
            saveAs(res.data, name)
            done++
          } catch (e) {
            console.error(e)
            failed++
          }
          this.progress = {
            ...this.progress,
            done,
            failed,
            packed: done,
            percent: Math.min(99, Math.round(((done + failed) / total) * 100)),
            message: `已完成 ${done}/${total}${failed ? `，失败 ${failed}` : ''}`
          }
        }
        this.apply({
          ...this.progress,
          status: failed && !done ? 2 : 1,
          running: false,
          phase: 'done',
          percent: 100,
          message: failed
            ? `下载结束：成功 ${done}，失败 ${failed}`
            : `已下载 ${done} 项原图/原视频`
        })
        this.finishedAt = Date.now()
        if (done) ElMessage.success(failed ? `成功 ${done}，失败 ${failed}` : `已下载 ${done} 项`)
        else ElMessage.error('下载失败')
      } finally {
        this.transferring = false
      }
    },
    dismiss() {
      if (this.progress.running || this.transferring || Number(this.progress.status) === 0) return
      this.finishedAt = 0
      this.progress = empty()
      this.stopPolling()
    }
  }
})

function formatBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return `${v} B`
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`
  if (v < 1024 * 1024 * 1024) return `${(v / 1024 / 1024).toFixed(1)} MB`
  return `${(v / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export default useAlbumDownloadStore
