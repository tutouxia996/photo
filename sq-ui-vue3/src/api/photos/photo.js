import request from '@/utils/request'

export function listPhoto(query) {
  return request({
    url: '/album/photo/list',
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

export function delPhoto(photoId) {
  return request({
    url: '/album/photo/' + photoId,
    method: 'delete'
  })
}

export function uploadPhoto(data) {
  return request({
    url: '/album/photo/upload',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    // 大视频上传可能远超默认 10s
    timeout: 600000
  })
}
