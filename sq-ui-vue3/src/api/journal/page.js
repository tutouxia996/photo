import request from '@/utils/request'

/** 批量拉取页码范围内的日记页 */
export function listJournalPages(bookId, from, to) {
  return request({
    url: '/journal/page/range',
    method: 'get',
    params: { bookId, from, to },
    headers: { repeatSubmit: false },
    silent: true
  })
}

/** upsert 单页 content_json */
export function saveJournalPage(data) {
  return request({
    url: '/journal/page',
    method: 'put',
    data,
    headers: { repeatSubmit: false },
    showActionLoading: false,
    silent: true
  })
}
