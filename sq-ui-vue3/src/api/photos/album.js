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

/** 从服务器磁盘目录创建相册并扫描（不上传/复制原图） */
export function importAlbumFromDisk(data) {
  return request({
    url: '/album/album/importFromDisk',
    method: 'post',
    data
  })
}

export function updateAlbum(data) {
  return request({
    url: '/album/album',
    method: 'put',
    data
  })
}

export function delAlbum(albumId) {
  return request({
    url: '/album/album/' + albumId,
    method: 'delete'
  })
}
