import request from '@/utils/request'

export function listScanPath(query) {
  return request({ url: '/album/scan/list', method: 'get', params: query })
}

export function getScanPath(pathId) {
  return request({ url: '/album/scan/' + pathId, method: 'get' })
}

export function addScanPath(data) {
  return request({ url: '/album/scan', method: 'post', data })
}

export function updateScanPath(data) {
  return request({ url: '/album/scan', method: 'put', data })
}

export function delScanPath(pathId) {
  return request({ url: '/album/scan/' + pathId, method: 'delete' })
}

export function runScan(pathId, fullScan = false) {
  return request({ url: '/album/scan/run/' + pathId, method: 'post', params: { fullScan } })
}

export function listScanLog(query) {
  return request({ url: '/album/scan/log/list', method: 'get', params: query })
}
