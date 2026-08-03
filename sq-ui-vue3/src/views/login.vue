<template>
  <div class="login">
    <div class="poly-bg"></div>
    <div class="login-panel">
      <div class="brand-pane">
        <div class="tech-grid"></div>
        <div class="tech-scan"></div>
        <div class="tech-corner tc-tl"></div>
        <div class="tech-corner tc-tr"></div>
        <div class="tech-corner tc-bl"></div>
        <div class="tech-corner tc-br"></div>
        <div class="tech-ring tech-ring-outer"></div>
        <div class="tech-ring tech-ring-inner"></div>
        <div class="tech-orbit">
          <span class="orbit-dot od-1"></span>
          <span class="orbit-dot od-2"></span>
          <span class="orbit-dot od-3"></span>
        </div>
        <span class="tech-particle tp-1"></span>
        <span class="tech-particle tp-2"></span>
        <span class="tech-particle tp-3"></span>
        <span class="tech-particle tp-4"></span>
        <div class="brand-inner">
          <div class="brand-mark">
            <svg viewBox="0 0 64 64" width="64" height="64" fill="none" stroke="currentColor" stroke-width="2.5">
              <circle cx="32" cy="20" r="6"/>
              <circle cx="18" cy="40" r="6"/>
              <circle cx="46" cy="40" r="6"/>
              <path d="M32 26v8M27 36l-7 2M37 36l7 2" stroke-linecap="round"/>
            </svg>
          </div>
          <h1 class="brand-title">{{ appTitle }}</h1>
          <p class="brand-subtitle">INTELLIGENT BUSINESS MANAGEMENT PLATFORM</p>
          <div class="brand-divider"></div>
          <p class="brand-tagline">智能协同 · 数据驱动 · 安全高效</p>
        </div>
      </div>

      <div class="form-pane">
        <div class="tech-corner tc-tl"></div>
        <div class="tech-corner tc-tr"></div>
        <div class="tech-corner tc-bl"></div>
        <div class="tech-corner tc-br"></div>
        <div class="form-scan"></div>
        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
          <div class="form-header">
            <h3 class="form-title">欢迎登录</h3>
            <p class="form-subtitle">{{ appTitle }}</p>
            <div class="form-title-line">
              <span class="line"></span>
              <span class="dot"></span>
              <span class="line"></span>
            </div>
          </div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              size="large"
              auto-complete="off"
              placeholder="请输入您的用户名"
            >
              <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              auto-complete="off"
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            >
              <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="code" class="captcha-item" v-if="captchaEnabled">
            <el-input
              v-model="loginForm.code"
              size="large"
              auto-complete="off"
              placeholder="验证码"
              @keyup.enter="handleLogin"
            >
              <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
            </el-input>
            <div class="login-code">
              <img :src="codeUrl" @click="getCode" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              :loading="loading"
              size="large"
              type="primary"
              class="login-btn"
              @click.prevent="handleLogin"
            >
              <span class="btn-deco btn-deco-l"></span>
              <span class="btn-text">
                <span v-if="!loading">立即登录</span>
                <span v-else>登录中...</span>
              </span>
              <span class="btn-deco btn-deco-r"></span>
            </el-button>
            <div class="form-links" v-if="register">
              <router-link class="link-type" :to="'/register'">立即注册</router-link>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>
    <div class="login-footer">
      <span class="foot-line"></span>
      <span class="foot-text">COPYRIGHT &copy; 2026 SQ ALL RIGHTS RESERVED</span>
      <span class="foot-line"></span>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login";
import useUserStore from '@/store/modules/user'

const appTitle = import.meta.env.VITE_APP_TITLE
const userStore = useUserStore()
const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance();

const loginForm = ref({
  username: "",
  password: "",
  code: "",
  uuid: ""
});

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
};

const codeUrl = ref("");
const loading = ref(false);
const captchaEnabled = ref(true);
const register = ref(false);
const redirect = ref(undefined);

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect;
}, { immediate: true });

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true;
      userStore.login(loginForm.value).then(() => {
        const query = route.query;
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur];
          }
          return acc;
        }, {});
        router.push({ path: redirect.value || "/", query: otherQueryParams });
      }).catch(() => {
        loading.value = false;
        if (captchaEnabled.value) {
          getCode();
        }
      });
    }
  });
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled;
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img;
      loginForm.value.uuid = res.uuid;
    }
  });
}

getCode();
</script>

<style lang="scss" scoped>
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(18px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}

@keyframes spinReverse {
  from { transform: translate(-50%, -50%) rotate(360deg); }
  to   { transform: translate(-50%, -50%) rotate(0deg); }
}

@keyframes orbit {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}

@keyframes scanY {
  0%   { transform: translateY(-30%); opacity: 0; }
  20%  { opacity: 1; }
  100% { transform: translateY(140%); opacity: 0; }
}

@keyframes floatY {
  0%, 100% { transform: translateY(0); opacity: .55; }
  50%      { transform: translateY(-14px); opacity: 1; }
}

@keyframes pulseDot {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(255,255,255,.55); }
  50%      { opacity: .55; box-shadow: 0 0 0 8px rgba(255,255,255,0); }
}

.login {
  position: relative;
  height: 100%;
  min-height: 620px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 28%, rgba(64,158,255,.12), transparent 38%),
    radial-gradient(circle at 82% 72%, rgba(64,158,255,.1), transparent 42%),
    linear-gradient(180deg, #eef3fb 0%, #e6edf8 100%);
}

.poly-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(120,150,200,.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(120,150,200,.07) 1px, transparent 1px),
    url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1600 900'><g fill='none' stroke='%23a8b6cc' stroke-width='1' opacity='0.55'><polygon points='1380,40 1520,90 1480,210 1340,180'/><polygon points='1480,210 1580,260 1540,360 1420,340'/><polygon points='1340,180 1480,210 1420,340 1300,300'/><polygon points='1300,300 1420,340 1380,460 1260,420'/><polygon points='40,540 180,500 240,620 120,660'/><polygon points='120,660 240,620 320,720 200,780'/><polygon points='180,500 320,470 360,580 240,620'/><circle cx='1480' cy='210' r='3' fill='%2379bbff' stroke='none'/><circle cx='1340' cy='180' r='3' fill='%2379bbff' stroke='none'/><circle cx='240' cy='620' r='3' fill='%2379bbff' stroke='none'/><circle cx='180' cy='500' r='3' fill='%2379bbff' stroke='none'/></g></svg>");
  background-size: 60px 60px, 60px 60px, cover;
  background-position: 0 0, 0 0, center;
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 1000px;
  height: 480px;
  display: flex;
  background: #fff;
  border-radius: 4px;
  border: 1px solid rgba(64,158,255,.14);
  box-shadow:
    0 16px 48px rgba(31,72,140,.14),
    0 2px 6px rgba(31,72,140,.05),
    0 0 0 1px rgba(64,158,255,.08);
  overflow: hidden;
  animation: fadeInUp .55s ease-out;
}

.brand-pane {
  width: 50%;
  background:
    radial-gradient(circle at 30% 25%, rgba(255,255,255,.18), transparent 55%),
    radial-gradient(circle at 75% 80%, rgba(0,0,0,.18), transparent 55%),
    linear-gradient(135deg, #1a4ea8 0%, var(--el-color-primary, #409eff) 55%, #79bbff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 32px;
  position: relative;
  overflow: hidden;
}

.tech-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.07) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
  -webkit-mask-image: radial-gradient(ellipse at center, #000 35%, transparent 80%);
  mask-image: radial-gradient(ellipse at center, #000 35%, transparent 80%);
}

.tech-scan {
  position: absolute;
  left: 0;
  right: 0;
  height: 90px;
  top: 0;
  pointer-events: none;
  background: linear-gradient(180deg, transparent, rgba(255,255,255,.18), transparent);
  animation: scanY 5s ease-in-out infinite;
}

.tech-corner {
  position: absolute;
  width: 26px;
  height: 26px;
  pointer-events: none;
}

.brand-pane .tc-tl {
  top: 16px;
  left: 16px;
  border-top: 2px solid rgba(255,255,255,.55);
  border-left: 2px solid rgba(255,255,255,.55);
}

.brand-pane .tc-tr {
  top: 16px;
  right: 16px;
  border-top: 2px solid rgba(255,255,255,.55);
  border-right: 2px solid rgba(255,255,255,.55);
}

.brand-pane .tc-bl {
  bottom: 16px;
  left: 16px;
  border-bottom: 2px solid rgba(255,255,255,.55);
  border-left: 2px solid rgba(255,255,255,.55);
}

.brand-pane .tc-br {
  bottom: 16px;
  right: 16px;
  border-bottom: 2px solid rgba(255,255,255,.55);
  border-right: 2px solid rgba(255,255,255,.55);
}

.form-pane .tc-tl {
  top: 14px;
  left: 14px;
  border-top: 2px solid var(--el-color-primary, #409eff);
  border-left: 2px solid var(--el-color-primary, #409eff);
  opacity: .7;
}

.form-pane .tc-tr {
  top: 14px;
  right: 14px;
  border-top: 2px solid var(--el-color-primary, #409eff);
  border-right: 2px solid var(--el-color-primary, #409eff);
  opacity: .7;
}

.form-pane .tc-bl {
  bottom: 14px;
  left: 14px;
  border-bottom: 2px solid var(--el-color-primary, #409eff);
  border-left: 2px solid var(--el-color-primary, #409eff);
  opacity: .7;
}

.form-pane .tc-br {
  bottom: 14px;
  right: 14px;
  border-bottom: 2px solid var(--el-color-primary, #409eff);
  border-right: 2px solid var(--el-color-primary, #409eff);
  opacity: .7;
}

.tech-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  pointer-events: none;
}

.tech-ring-outer {
  width: 380px;
  height: 380px;
  border: 1px dashed rgba(255,255,255,.28);
  animation: spin 28s linear infinite;
}

.tech-ring-inner {
  width: 260px;
  height: 260px;
  border: 1px solid rgba(255,255,255,.2);
  animation: spinReverse 22s linear infinite;
}

.tech-ring-inner::before,
.tech-ring-inner::after {
  content: '';
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 10px rgba(255,255,255,.85);
}

.tech-ring-inner::before { top: -3px;  left: 50%; transform: translateX(-50%); }
.tech-ring-inner::after  { bottom: -3px; left: 50%; transform: translateX(-50%); }

.tech-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  pointer-events: none;
  animation: orbit 14s linear infinite;
}

.orbit-dot {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 12px rgba(255,255,255,.9);
}

.od-1 { top: -4px; left: 50%; transform: translateX(-50%); }
.od-2 { top: 50%; right: -4px; transform: translateY(-50%); width: 5px; height: 5px; opacity: .8; }
.od-3 { bottom: -4px; left: 30%; width: 4px; height: 4px; opacity: .7; }

.tech-particle {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff;
  pointer-events: none;
  animation: floatY 5s ease-in-out infinite;
}

.tp-1 { top: 18%;  left: 14%; }
.tp-2 { top: 30%;  right: 16%; width: 3px; height: 3px; animation-duration: 6s; animation-delay: .8s; }
.tp-3 { bottom: 22%; left: 22%; width: 5px; height: 5px; animation-duration: 7s; animation-delay: 1.4s; }
.tp-4 { bottom: 30%; right: 18%; width: 3px; height: 3px; animation-duration: 5.5s; animation-delay: .4s; }

.brand-inner {
  position: relative;
  z-index: 2;
  text-align: center;
  color: #fff;
}

.brand-mark {
  width: 70px;
  height: 70px;
  margin: 0 auto 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  filter: drop-shadow(0 0 14px rgba(255,255,255,.5));
  animation: pulseDot 3s ease-in-out infinite;
}

.brand-title {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 4px;
  line-height: 1.3;
  text-shadow: 0 2px 14px rgba(0,0,0,.28), 0 0 18px rgba(255,255,255,.18);
}

.brand-subtitle {
  margin: 0;
  font-size: 12px;
  color: rgba(255,255,255,.85);
  letter-spacing: 3px;
}

.brand-divider {
  width: 48px;
  height: 2px;
  margin: 22px auto 14px;
  background: linear-gradient(90deg, transparent, #fff, transparent);
  border-radius: 1px;
  opacity: .8;
}

.brand-tagline {
  margin: 0;
  font-size: 13px;
  color: rgba(255,255,255,.78);
  letter-spacing: 4px;
}

.form-pane {
  position: relative;
  width: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 50px;
  background:
    radial-gradient(circle at 100% 0%, rgba(64,158,255,.06), transparent 50%),
    radial-gradient(circle at 0% 100%, rgba(64,158,255,.05), transparent 50%),
    #ffffff;
  overflow: hidden;
}

.form-scan {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 80px;
  background: linear-gradient(180deg, transparent, rgba(64,158,255,.18), transparent);
  pointer-events: none;
  animation: scanY 6s ease-in-out infinite;
  animation-delay: 1.2s;
}

.login-form {
  width: 100%;
  max-width: 380px;

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-input) {
    height: 38px;
  }

  :deep(.el-input__wrapper) {
    border-radius: 3px;
    background: #fff;
    box-shadow: 0 0 0 1px #d9dfe6 inset;
    padding-left: 12px;
    transition: box-shadow .2s;
  }

  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--el-color-primary, #409eff) inset;
  }

  :deep(.el-input__inner) {
    color: #333;
    font-size: 14px;
  }

  :deep(.el-input__icon) {
    color: #97a0ad;
  }
}

.form-header {
  text-align: center;
  margin-bottom: 28px;
}

.form-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: #1a3a6c;
  letter-spacing: 4px;
  line-height: 1.2;
  background: linear-gradient(135deg, #1a3a6c 0%, var(--el-color-primary, #409eff) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.form-subtitle {
  margin: 0 0 14px;
  font-size: 12px;
  color: #8a9bb4;
  letter-spacing: 3px;
}

.form-title-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  .line {
    width: 32px;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--el-color-primary, #409eff));
  }

  .line:last-child {
    background: linear-gradient(90deg, var(--el-color-primary, #409eff), transparent);
  }

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--el-color-primary, #409eff);
    box-shadow: 0 0 8px rgba(64,158,255,.6);
  }
}

.captcha-item {
  :deep(.el-form-item__content) {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  :deep(.el-input) {
    flex: 1;
  }
}

.login-code {
  width: 130px;
  height: 38px;
  flex-shrink: 0;

  img {
    width: 130px;
    height: 38px;
    display: block;
    cursor: pointer;
    border-radius: 3px;
  }
}

.login-btn {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 3px;
  font-size: 15px;
  letter-spacing: 6px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(90deg, #1a4ea8 0%, var(--el-color-primary, #409eff) 50%, #79bbff 100%);
  background-size: 200% 100%;
  background-position: 0% 0;
  box-shadow: 0 6px 20px rgba(64,158,255,.35);
  transition: background-position .5s ease, box-shadow .3s, transform .15s;

  :deep(span) {
    position: relative;
    z-index: 1;
  }

  .btn-deco {
    width: 14px;
    height: 1px;
    background: rgba(255,255,255,.7);
    display: inline-block;
    vertical-align: middle;
    margin: 0 10px;
  }

  .btn-text {
    display: inline-block;
    vertical-align: middle;
  }

  &:hover {
    background-position: 100% 0;
    box-shadow: 0 8px 28px rgba(64,158,255,.5);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

.form-links {
  width: 100%;
  text-align: center;
  margin-top: 14px;

  .link-type {
    color: #888;
    text-decoration: none;
  }
}

.login-footer {
  position: relative;
  z-index: 1;
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #6b7c95;
  font-size: 11px;
  letter-spacing: 4px;

  .foot-line {
    width: 80px;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(64,158,255,.45), transparent);
  }

  .foot-text {
    position: relative;
    padding: 0 6px;

    &::before,
    &::after {
      content: '';
      position: absolute;
      top: 50%;
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: var(--el-color-primary, #409eff);
      transform: translateY(-50%);
      opacity: .55;
    }

    &::before { left: -4px; }
    &::after  { right: -4px; }
  }
}

@media (max-width: 768px) {
  .login {
    min-height: 100%;
    padding: 24px;
    box-sizing: border-box;
  }

  .login-panel {
    width: 100%;
    height: auto;
    flex-direction: column;
  }

  .brand-pane {
    width: 100%;
    padding: 36px 24px;
  }

  .brand-title {
    font-size: 22px;
    letter-spacing: 2px;
  }

  .brand-subtitle {
    font-size: 11px;
    letter-spacing: 2px;
  }

  .form-pane {
    width: 100%;
    padding: 32px 24px;
  }
}
</style>
