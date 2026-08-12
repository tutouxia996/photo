import request from '@/utils/request'

export function listPhoto(query) {
  return request({
    url: '/album/photo/list',
    method: 'get',
    params: query
  })
}

/** 地图点位（含 GPS），用于缩放距离聚合 */
export function listPhotoMapPoints(query) {
  return request({
    url: '/album/photo/mapPoints',
    method: 'get',
    params: query
  })
}

export function getPhoto(photoId) {
  return request({
    url: '/album/photo/' + photoId,
    method: 'get'
  })
}

export function updatePhoto(data) {
  return request({
    url: '/album/photo',
    method: 'put',
    data
  })
}

/** 放入回收站 */
export function delPhoto(photoId) {
  return request({
    url: '/album/photo/' + photoId,
    method: 'delete'
  })
}

/** 从回收站恢复 */
export function restorePhoto(photoId) {
  return request({
    url: '/album/photo/restore/' + photoId,
    method: 'put'
  })
}

/** 彻底删除 */
export function purgePhoto(photoId) {
  return request({
    url: '/album/photo/purge/' + photoId,
    method: 'delete'
  })
}

export function uploadPhoto(data, options = {}) {
  return request({
    url: '/album/photo/upload',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    // 大视频上传可能远超默认 10s
    timeout: 600000,
    showActionLoading: options.showActionLoading !== false,
    actionLoadingText: '正在处理照片/视频，请勿关闭…',
    onUploadProgress: options.onUploadProgress
  })
}

/** 按同相册权威 GPS 时间插值，为无坐标媒体补估计位置 */
export function fallbackLocateAlbum(albumId) {
  return request({
    url: '/album/photo/fallbackLocate/' + albumId,
    method: 'post'
  })
}

/** 微调估计坐标（仍不上主轨迹） */
export function updateEstimatedPosition(data) {
  return request({
    url: '/album/photo/estimatedPosition',
    method: 'put',
    data
  })
}

/** 确认估计坐标并同步主轨迹 */
export function confirmEstimatedPhoto(data) {
  return request({
    url: '/album/photo/confirmEstimated',
    method: 'post',
    data
  })
}
