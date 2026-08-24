import request from '@/utils/request'

export function listScanPath(query) {
  return request({ url: '/album/scan/list', method: 'get', params: query })
}

export function getScanPath(pathId) {
  return request({ url: '/album/scan/' + pathId, method: 'get' })
}

export function addScanPath(data) {
  return request({ url: '/album/scan', method: 'post', data })
}

export function updateScanPath(data) {
  return request({ url: '/album/scan', method: 'put', data })
}

export function delScanPath(pathId) {
  return request({ url: '/album/scan/' + pathId, method: 'delete' })
}

/** 异步启动扫描，立即返回扫描日志 */
export function runScan(pathId, fullScan = false) {
  return request({
    url: '/album/scan/run/' + pathId,
    method: 'post',
    params: { fullScan },
    timeout: 60000,
    actionLoadingText: '正在启动扫描…'
  })
}

/** 为缺少封面的视频补截帧（大疆 HEVC 等）；pathId 可空=全部目录；force=强制重截 */
export function repairVideoThumbs(pathId, force = false) {
  const params = {}
  if (pathId != null) params.pathId = pathId
  if (force) params.force = true
  return request({
    url: '/album/scan/repairVideoThumbs',
    method: 'post',
    params,
    timeout: 600000,
    actionLoadingText: force ? '正在强制重截视频缩略图，请稍候…' : '正在补视频缩略图，请稍候…'
  })
}

/** 查询视频转码资格统计（不入队） */
export function getVideoProxyStats(pathId) {
  const params = {}
  if (pathId != null) params.pathId = pathId
  return request({
    url: '/album/scan/videoProxyStats',
    method: 'get',
    params,
    timeout: 120000,
    showActionLoading: false
  })
}

/** 一键排队生成视频浏览档（720p30/1080p30）；后台转码，立即返回 */
export function generateVideoProxies(pathId, force = false) {
  const params = {}
  if (pathId != null) params.pathId = pathId
  if (force) params.force = true
  return request({
    url: '/album/scan/generateVideoProxies',
    method: 'post',
    params,
    timeout: 30000,
    showActionLoading: false
  })
}

export function listScanLog(query) {
  return request({ url: '/album/scan/log/list', method: 'get', params: query })
}

/** 查询扫描进度（进行中/已结束） */
export function getScanProgress(logId) {
  return request({
    url: '/album/scan/progress/' + logId,
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

/** 当前进行中的扫描任务 */
export function getActiveScanProgress() {
  return request({
    url: '/album/scan/active',
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

/**
 * 轮询扫描进度直到结束
 * @returns {Promise<object>} 最终扫描日志
 */
export function pollScanProgress(logId, { interval = 800, onProgress, signal } = {}) {
  return new Promise((resolve, reject) => {
    let timer = null
    const clear = () => {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
    }
    const tick = () => {
      if (signal?.aborted) {
        clear()
        reject(new Error('aborted'))
        return
      }
      getScanProgress(logId)
        .then(res => {
          const log = res.data || {}
          onProgress && onProgress(log)
          if (log.status === 0 || log.status === '0') {
            timer = setTimeout(tick, interval)
          } else {
            clear()
            resolve(log)
          }
        })
        .catch(err => {
          clear()
          reject(err)
        })
    }
    tick()
  })
}
