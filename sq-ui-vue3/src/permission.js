import router from './router'
import { ElMessage, ElNotification } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isHttp, isPathMatch } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/register']
const forceChangePwdRoutes = ['/user/profile']

const forceChangePwdMessages = {
  first_login: '首次登录，请立即修改密码',
  expired: '密码已过期，请修改密码'
}

const isWhiteList = (path) => {
  return whiteList.some(pattern => isPathMatch(pattern, path))
}

const isForceChangePwdRoute = (path) => {
  return forceChangePwdRoutes.some(pattern => isPathMatch(pattern, path))
}

const redirectToResetPwd = (next) => {
  const reason = useUserStore().forceChangePwdReason
  const msg = forceChangePwdMessages[reason] || '请修改密码'
  ElNotification({
    title: '密码安全提示',
    message: '<div style="font-size:16px;padding:8px 0">' + msg + '</div>',
    type: 'warning',
    dangerouslyUseHTMLString: true,
    duration: 0
  })
  next({ path: '/user/profile', query: { tab: 'resetPwd' }, replace: true })
  NProgress.done()
}

router.beforeEach((to, from, next) => {
  NProgress.start()
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else if (isWhiteList(to.path)) {
      next()
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true
        useUserStore().getInfo().then(() => {
          isRelogin.show = false
          if (useUserStore().forceChangePwd) {
            if (!isForceChangePwdRoute(to.path)) {
              redirectToResetPwd(next)
              return
            }
            next()
            NProgress.done()
            return
          }
          usePermissionStore().generateRoutes().then(accessRoutes => {
            accessRoutes.forEach(route => {
              if (!isHttp(route.path)) {
                router.addRoute(route)
              }
            })
            next({ ...to, replace: true })
          })
        }).catch(err => {
          useUserStore().logOut().then(() => {
            ElMessage.error(err)
            next({ path: '/' })
          })
        })
      } else {
        if (useUserStore().forceChangePwd) {
          if (!isForceChangePwdRoute(to.path)) {
            redirectToResetPwd(next)
            return
          }
          next()
          NProgress.done()
          return
        }
        if (usePermissionStore().routes.length === 0) {
          usePermissionStore().generateRoutes().then(accessRoutes => {
            accessRoutes.forEach(route => {
              if (!isHttp(route.path)) {
                router.addRoute(route)
              }
            })
            next({ ...to, replace: true })
          })
          return
        }
        next()
      }
    }
  } else {
    if (isWhiteList(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.fullPath}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})
