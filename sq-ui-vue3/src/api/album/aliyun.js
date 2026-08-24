import request from '@/utils/request'

export function getAliyunSetting() {
  return request({ url: '/album/aliyun', method: 'get' })
}

export function saveAliyunSetting(data) {
  return request({ url: '/album/aliyun', method: 'put', data })
}

export function runAliyunSync() {
  return request({ url: '/album/aliyun/run', method: 'post', timeout: 60000, showActionLoading: false })
}

export function pauseAliyunSync() {
  return request({ url: '/album/aliyun/pause', method: 'post', showActionLoading: false })
}

export function resumeAliyunSync() {
  return request({ url: '/album/aliyun/resume', method: 'post', timeout: 60000, showActionLoading: false })
}

export function getAliyunProgress() {
  return request({
    url: '/album/aliyun/progress',
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

export function listAliyunAlbums(refreshToken) {
  const params = {}
  if (refreshToken) params.refreshToken = refreshToken
  return request({
    url: '/album/aliyun/albums',
    method: 'get',
    params,
    timeout: 60000,
    actionLoadingText: '正在获取云盘相册…'
  })
}
