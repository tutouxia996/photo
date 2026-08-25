import request from '@/utils/request'

/** 后台相册列表 */
export function listAlbum(query) {
  return request({
    url: '/album/album/list',
    method: 'get',
    params: query
  })
}

/** 公开相册列表（无需登录） */
export function listPortalAlbum(query) {
  return request({
    url: '/portal/album/list',
    method: 'get',
    params: query
  })
}

export function getAlbum(albumId) {
  return request({
    url: '/album/album/' + albumId,
    method: 'get'
  })
}

export function addAlbum(data) {
  return request({
    url: '/album/album',
    method: 'post',
    data
  })
}

/** 从服务器磁盘目录创建相册并异步启动扫描（不上传/复制原图） */
export function importAlbumFromDisk(data) {
  return request({
    url: '/album/album/importFromDisk',
    method: 'post',
    data,
    // 仅创建相册并启动异步扫描，进度另接口轮询
    timeout: 60000,
    showActionLoading: false
  })
}

export function updateAlbum(data) {
  return request({
    url: '/album/album',
    method: 'put',
    data
  })
}

/** 放入回收站 */
export function delAlbum(albumId) {
  return request({
    url: '/album/album/' + albumId,
    method: 'delete'
  })
}

/** 从回收站恢复 */
export function restoreAlbum(albumId) {
  return request({
    url: '/album/album/restore/' + albumId,
    method: 'put'
  })
}

/** 彻底删除 */
export function purgeAlbum(albumId) {
  return request({
    url: '/album/album/purge/' + albumId,
    method: 'delete'
  })
}

/** 启动相册后台打包下载（原图/原视频 → zip） */
export function startAlbumDownload(albumId) {
  return request({
    url: '/album/album/' + albumId + '/download',
    method: 'post',
    timeout: 60000,
    showActionLoading: false
  })
}

/** 相册打包/下载进度 */
export function getAlbumDownloadProgress() {
  return request({
    url: '/album/album/download/progress',
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

/** 已打包 zip 文件下载地址 */
export function albumDownloadFileUrl(taskId) {
  return '/album/album/download/file?taskId=' + encodeURIComponent(taskId)
}
