import request from '@/utils/request'

export function listTrack(query) {
  return request({ url: '/album/track/list', method: 'get', params: query })
}

export function getTrack(trackId) {
  return request({ url: '/album/track/' + trackId, method: 'get' })
}

export function generateTrack(params) {
  return request({ url: '/album/track/generate', method: 'post', params })
}

export function updateTrack(data) {
  return request({ url: '/album/track', method: 'put', data })
}

export function delTrack(trackId) {
  return request({ url: '/album/track/' + trackId, method: 'delete' })
}
