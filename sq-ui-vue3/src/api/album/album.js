import request from '@/utils/request'

export function listAlbum(query) {
  return request({ url: '/album/album/list', method: 'get', params: query })
}

export function getAlbum(albumId) {
  return request({ url: '/album/album/' + albumId, method: 'get' })
}

export function addAlbum(data) {
  return request({ url: '/album/album', method: 'post', data })
}

export function updateAlbum(data) {
  return request({ url: '/album/album', method: 'put', data })
}

export function delAlbum(albumId) {
  return request({ url: '/album/album/' + albumId, method: 'delete' })
}

export function restoreAlbum(albumId) {
  return request({ url: '/album/album/restore/' + albumId, method: 'put' })
}

export function purgeAlbum(albumId) {
  return request({ url: '/album/album/purge/' + albumId, method: 'delete' })
}

export function refreshAlbumStats(albumId) {
  return request({ url: '/album/album/refreshStats/' + albumId, method: 'put' })
}

/** 启动相册后台打包下载 */
export function startAlbumDownload(albumId) {
  return request({
    url: '/album/album/' + albumId + '/download',
    method: 'post',
    timeout: 60000,
    showActionLoading: false
  })
}

export function getAlbumDownloadProgress() {
  return request({
    url: '/album/album/download/progress',
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

export function albumDownloadFileUrl(taskId) {
  return '/album/album/download/file?taskId=' + encodeURIComponent(taskId)
}
