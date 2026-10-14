import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from "@/utils/validate"
import { encryptPassword } from '@/utils/smcrypto'
import defAva from '@/assets/images/profile.jpg'

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: [],
      forceChangePwd: false,
      forceChangePwdReason: ''
    }),
    actions: {
      // 登录
      login(userInfo) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise((resolve, reject) => {
          encryptPassword(password).then(cipher => {
            login(username, cipher, code, uuid).then(res => {
              setToken(res.token)
              this.token = res.token
              this.forceChangePwd = res.forceChangePwd === true
              this.forceChangePwdReason = res.forceChangePwdReason || ''
              resolve(res)
            }).catch(error => {
              reject(error)
            })
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(res => {
            const user = res.user
            let avatar = user.avatar || ""
            if (!isHttp(avatar)) {
              avatar = (isEmpty(avatar)) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) {
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName || user.userName
            this.avatar = avatar
            this.forceChangePwd = res.forceChangePwd === true
            this.forceChangePwdReason = res.forceChangePwdReason || ''
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统（接口失败也清本地态，避免会话过期时卡死在加载中）
      logOut() {
        const clearLocal = () => {
          this.token = ''
          this.roles = []
          this.permissions = []
          this.id = ''
          this.name = ''
          this.nickName = ''
          this.forceChangePwd = false
          this.forceChangePwdReason = ''
          removeToken()
        }
        return new Promise((resolve) => {
          const finish = () => {
            clearLocal()
            import('@/store/modules/aliyunSync').then(mod => {
              try {
                mod.default().stopPolling()
              } catch (e) { /* ignore */ }
            }).catch(() => {}).finally(() => resolve())
          }
          // 无 token 时不必再打登出接口
          if (!this.token && !getToken()) {
            finish()
            return
          }
          logout().then(finish).catch(finish)
        })
      }
    }
  })

export default useUserStore
