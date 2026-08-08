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

export function refreshAlbumStats(albumId) {
  return request({ url: '/album/album/refreshStats/' + albumId, method: 'put' })
}
