import request from '@/utils/request'

/** 创建相册分享（需登录） */
export function createAlbumShare(data) {
  return request({
    url: '/album/share',
    method: 'post',
    data
  })
}

/** 更新相册分享 */
export function updateAlbumShare(data) {
  return request({
    url: '/album/share',
    method: 'put',
    data
  })
}

/** 相册下的分享列表 */
export function listAlbumShare(albumId) {
  return request({
    url: '/album/share/list',
    method: 'get',
    params: { albumId }
  })
}

/** 删除分享 */
export function delAlbumShare(shareId) {
  return request({
    url: '/album/share/' + shareId,
    method: 'delete'
  })
}

/** 访客：解析分享（无需登录） */
export function resolveShare(token) {
  return request({
    url: '/share/' + encodeURIComponent(token),
    method: 'get',
    headers: { isToken: false }
  })
}

/** 访客：分享内媒体列表 */
export function listSharePhotos(token, query = {}) {
  return request({
    url: '/share/' + encodeURIComponent(token) + '/photos',
    method: 'get',
    params: query,
    headers: { isToken: false }
  })
}

/** 访客：地图点位（可选 albumId） */
export function listShareMapPoints(token, query = {}) {
  return request({
    url: '/share/' + encodeURIComponent(token) + '/mapPoints',
    method: 'get',
    params: query,
    headers: { isToken: false }
  })
}

/** 访客：轨迹列表（可选 albumId） */
export function listShareTracks(token, query = {}) {
  return request({
    url: '/share/' + encodeURIComponent(token) + '/tracks',
    method: 'get',
    params: query,
    headers: { isToken: false }
  })
}

/** 访客：轨迹点 */
export function getShareTrackPoints(token, trackId) {
  return request({
    url: '/share/' + encodeURIComponent(token) + '/tracks/' + trackId + '/points',
    method: 'get',
    headers: { isToken: false }
  })
}
