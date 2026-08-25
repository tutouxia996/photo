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
    params: query,
    timeout: 120000
  })
}

/** 足迹图：已访问省/市/区县高德边界 GeoJSON */
export function getVisitedRegionGeo() {
  return request({
    url: '/album/photo/visitedRegionGeo',
    method: 'get',
    timeout: 120000
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
    headers: {
      'Content-Type': 'multipart/form-data',
      // 并发多文件上传时 FormData 会被序列化成同一签名，需关闭防重复提交
      repeatSubmit: false
    },
    data,
    // 大视频上传可能远超默认 10s
    timeout: 600000,
    showActionLoading: options.showActionLoading !== false,
    actionLoadingText: '正在处理照片/视频，请勿关闭…',
    onUploadProgress: options.onUploadProgress
  })
}

/** 查询视频浏览档状态（480p/720p/1080p，不入库） */
export function getVideoProxyStatus(photoId, quality = '480p', fps = 30) {
  return request({
    url: '/album/photo/videoProxy/' + photoId,
    method: 'get',
    params: { quality, fps }
  })
}

/** 确保浏览档就绪：缺失则后台 ffmpeg 转码 */
export function ensureVideoProxy(photoId, quality = '480p', fps = 30) {
  return request({
    url: '/album/photo/videoProxy/' + photoId + '/ensure',
    method: 'post',
    params: { quality, fps },
    timeout: 60000,
    showActionLoading: false
  })
}

/** 按同相册权威 GPS 时间插值，为无坐标媒体补估计位置 */
export function fallbackLocateAlbum(albumId) {
  return request({
    url: '/album/photo/fallbackLocate/' + albumId,
    method: 'post'
  })
}

/** 按国家/省/市/区为无 GPS 媒体写入区域中心粗定位 */
export function regionLocateAlbum(albumId, data) {
  return request({
    url: '/album/photo/regionLocate/' + albumId,
    method: 'post',
    data
  })
}

/** AI 识别相册照片地标（耗时较长；传 photoIds 点选识别） */
export function aiLandmarkAlbum(albumId, data) {
  return request({
    url: '/album/photo/aiLandmark/' + albumId,
    method: 'post',
    data: data || {},
    timeout: 300000,
    showActionLoading: true,
    actionLoadingText: '正在 AI 识别地标，请稍候…'
  })
}

/** 行政区/地址地理编码预览 */
export function geocodeAddress(address) {
  return request({
    url: '/album/photo/geocode',
    method: 'get',
    params: { address }
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

/** 纠正媒体定位（设备 GPS 漂移等 → 手工坐标，并同步轨迹） */
export function correctPhotoPosition(data) {
  return request({
    url: '/album/photo/correctPosition',
    method: 'put',
    data
  })
}

/** 启动相册照片后台打分（不阻塞页面） */
export function scoreAlbumPhotos(albumId, data) {
  return request({
    url: '/album/photo/score/' + albumId,
    method: 'post',
    data: data || {},
    timeout: 30000,
    showActionLoading: false
  })
}

/** 照片质量打分进度 */
export function getPhotoScoreProgress() {
  return request({
    url: '/album/photo/score/progress',
    method: 'get',
    headers: { repeatSubmit: false },
    silent: true
  })
}

/** AI 出图可用预设 */
export function listDrawPresets() {
  return request({
    url: '/album/photo/draw/presets',
    method: 'get'
  })
}

/** 单张照片 AI 出图（万相，耗时较长） */
export function drawPhoto(photoId, data) {
  return request({
    url: '/album/photo/draw/' + photoId,
    method: 'post',
    data: data || {},
    timeout: 300000,
    showActionLoading: true,
    actionLoadingText: '正在 AI 出图，请稍候（约 15–60 秒）…'
  })
}

/** 批量 AI 出图 */
export function drawPhotoBatch(albumId, data) {
  const count = data?.photoIds?.length || 1
  return request({
    url: '/album/photo/draw/batch/' + albumId,
    method: 'post',
    data: data || {},
    timeout: Math.min(3600000, 300000 * count),
    showActionLoading: true,
    actionLoadingText: `正在批量 AI 出图（${count} 张），请勿关闭…`
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

/** 相册内全部待确认估计点一键上主轨迹 */
export function confirmEstimatedBatch(albumId) {
  return request({
    url: '/album/photo/confirmEstimatedBatch/' + albumId,
    method: 'post',
    timeout: 120000,
    showActionLoading: true,
    actionLoadingText: '正在全部确认上主轨迹…'
  })
}
