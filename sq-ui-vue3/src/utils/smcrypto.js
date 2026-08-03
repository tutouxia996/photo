import { sm2 } from 'sm-crypto'
import request from '@/utils/request'

/**
 * 国密前端工具（Vue3）
 *
 * SM2：登录密码加密（提交前向后端 /system/publicKey 拉取公钥）。
 * 已移除 remember-me 相关 SM4 本地持久化能力。
 */

let cachedPublicKey = null

/**
 * 从后端获取 SM2 公钥（hex）。
 */
export function fetchPublicKey() {
  if (cachedPublicKey) {
    return Promise.resolve(cachedPublicKey)
  }
  return request({
    url: '/system/publicKey',
    method: 'get',
    headers: { isToken: false }
  }).then(res => {
    cachedPublicKey = res.hex
    return cachedPublicKey
  })
}

/**
 * 使用 SM2 加密明文密码，输出 C1C3C2 hex（与后端 Sm2Utils.decryptHex 互通）。
 *
 * @param {string} plaintext 明文密码
 * @returns {Promise<string>} hex 密文
 */
export function encryptPassword(plaintext) {
  return fetchPublicKey().then(pubHex => {
    return sm2.doEncrypt(plaintext, pubHex, 1)
  })
}

