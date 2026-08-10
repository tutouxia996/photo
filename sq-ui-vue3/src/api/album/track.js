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

export function updateTrackPoints(points) {
  return request({ url: '/album/track/points', method: 'put', data: points })
}

/** 预览按出行方式规划的真实路线 */
export function previewTrackRoute(data) {
  return request({ url: '/album/track/route/preview', method: 'post', data })
}

/** 按照片坐标批量贴合路网；strategy=photo 会修复错误的公交/高铁绕路 */
export function resolveTrackRoutes(trackId, force = false, strategy = 'photo') {
  return request({
    url: '/album/track/' + trackId + '/resolve-routes',
    method: 'post',
    params: { force, strategy },
    timeout: 120000,
    // 轨迹查看页已有 v-loading，避免与全局「处理中」叠成两个转圈
    showActionLoading: false
  })
}

export function delTrackPoint(pointId) {
  return request({ url: '/album/track/point/' + pointId, method: 'delete' })
}

export function delTrack(trackId) {
  return request({ url: '/album/track/' + trackId, method: 'delete' })
}

/** 高德地名/POI 搜索 */
export function searchTrackPlace(params) {
  return request({
    url: '/album/track/place/search',
    method: 'get',
    params
  })
}

/** 自定义增补路段（插入两个无照片途经点 + 真实折线） */
export function addCustomSegment(trackId, data) {
  return request({
    url: '/album/track/' + trackId + '/custom-segment',
    method: 'post',
    data,
    timeout: 60000
  })
}

/** 火车时刻表 API 状态（无 Key 仍可手工经停） */
export function getTrainApiStatus() {
  return request({ url: '/album/track/train/status', method: 'get' })
}

/** 站到站查询班次 */
export function queryTrains(params) {
  return request({
    url: '/album/track/train/query',
    method: 'get',
    params,
    timeout: 30000
  })
}

/** 按车次拉取经停 */
export function getTrainStops(params) {
  return request({
    url: '/album/track/train/stops',
    method: 'get',
    params,
    timeout: 30000
  })
}

/** 车次/手工经停贴轨预览 */
export function planTrainRoute(data) {
  return request({
    url: '/album/track/train/plan',
    method: 'post',
    data,
    timeout: 180000,
    showActionLoading: false
  })
}

/** 按经停插入多站途经点 */
export function addTrainSegment(trackId, data) {
  return request({
    url: '/album/track/' + trackId + '/train-segment',
    method: 'post',
    data,
    timeout: 180000,
    showActionLoading: false
  })
}
