import request from '@/utils/request'

export function listDrawPreset(query) {
  return request({ url: '/album/drawPreset/list', method: 'get', params: query })
}

export function getDrawPreset(presetId) {
  return request({ url: '/album/drawPreset/' + presetId, method: 'get' })
}

export function addDrawPreset(data) {
  return request({ url: '/album/drawPreset', method: 'post', data })
}

export function updateDrawPreset(data) {
  return request({ url: '/album/drawPreset', method: 'put', data })
}

export function delDrawPreset(presetId) {
  return request({ url: '/album/drawPreset/' + presetId, method: 'delete' })
}

/** 解析 skill：传 FormData，含 file 或 text */
export function parseDrawSkill(formData) {
  return request({
    url: '/album/drawPreset/parseSkill',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
