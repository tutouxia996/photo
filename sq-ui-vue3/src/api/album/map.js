import request from '@/utils/request'

/** 高德 JS API 配置（分享页匿名可访问） */
export function getMapJsConfig() {
  return request({
    url: '/portal/map/js-config',
    method: 'get',
    headers: { isToken: false }
  })
}
