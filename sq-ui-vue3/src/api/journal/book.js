import request from '@/utils/request'

/** 获取或创建当前用户默认日记本 */
export function getMineBook() {
  return request({
    url: '/journal/book/mine',
    method: 'get'
  })
}

export function getJournalBook(bookId) {
  return request({
    url: '/journal/book/' + bookId,
    method: 'get'
  })
}
