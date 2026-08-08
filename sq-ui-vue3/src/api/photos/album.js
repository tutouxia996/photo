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
    data,
    // 扫描含 MD5/缩略图，大目录可能远超默认 10s
    timeout: 600000
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
