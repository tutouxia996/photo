
<template>
  <div class="welcome">
    <div class="welcome-bg"></div>

    <div class="main-card">
      <span class="card-corner cc-tl"></span>
      <span class="card-corner cc-tr"></span>
      <span class="card-corner cc-bl"></span>
      <span class="card-corner cc-br"></span>
      <div class="card-scan"></div>

      <div class="left-pane">
        <div class="hud-ring outer"></div>
        <div class="hud-ring inner"></div>
        <div class="orbit">
          <span class="dot dot-1"></span>
          <span class="dot dot-2"></span>
          <span class="dot dot-3"></span>
        </div>
        <div class="particle p1"></div>
        <div class="particle p2"></div>
        <div class="particle p3"></div>
        <div class="particle p4"></div>

        <div class="hero-badge">
          <span class="badge-dot"></span>
          SYSTEM ONLINE
        </div>
        <h1 class="hero-title">{{ appTitle }}</h1>
        <p class="hero-subtitle">INTELLIGENT BUSINESS MANAGEMENT PLATFORM</p>
        <div class="hero-line">
          <span class="line"></span>
          <span class="glow-dot"></span>
          <span class="line"></span>
        </div>
      </div>

      <div class="right-pane">
        <div class="right-corner rc-tl"></div>
        <div class="right-corner rc-tr"></div>
        <div class="right-corner rc-bl"></div>
        <div class="right-corner rc-br"></div>
        <div class="right-scan"></div>

        <div class="greet-row">
          <div class="avatar-wrapper">
            <img :src="userStore.avatar" class="avatar" />
          </div>
          <div class="greeting-text">
            <p class="greeting-sub">{{ greetMsg }}</p>
            <p class="greeting">{{ userStore.nickName }}<span class="punc">，</span><span class="welcome-text">欢迎回来</span></p>
          </div>
          <div class="greet-divider"></div>
          <div class="greet-meta">
            <p class="greet-meta-label">登录账号</p>
            <p class="greet-meta-value">{{ userStore.name || '-' }}</p>
          </div>
          <div class="greet-divider"></div>
          <div class="greet-meta">
            <p class="greet-meta-label">用户角色</p>
            <p class="greet-meta-value">{{ roleText }}</p>
          </div>
        </div>

        <div class="stats-list">
          <div class="stat-item">
            <div class="stat-icon">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2" stroke-linecap="round"/></svg>
            </div>
            <div class="stat-body">
              <p class="stat-label">当前时间</p>
              <p class="stat-value time-value">{{ timeStr }}</p>
              <p class="stat-foot">{{ dateStr }} · {{ weekStr }}</p>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/></svg>
            </div>
            <div class="stat-body">
              <p class="stat-label">系统版本</p>
              <p class="stat-value">v1.1.0</p>
              <p class="stat-foot">stable release</p>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
            </div>
            <div class="stat-body">
              <p class="stat-label">系统状态</p>
              <p class="stat-value status-value">运行正常</p>
              <p class="stat-foot">all services online</p>
            </div>
          </div>
        </div>

        <div class="right-glow"></div>
      </div>
    </div>

    <div class="welcome-foot">
      <span class="foot-line">
        <span class="foot-dot"></span>
      </span>
      <span class="foot-text">SQ INTELLIGENT MANAGEMENT · v1.1.0</span>
      <span class="foot-line">
        <span class="foot-dot right"></span>
      </span>
    </div>
  </div>
</template>


<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import useUserStore from '@/store/modules/user'

const appTitle = import.meta.env.VITE_APP_TITLE
const userStore = useUserStore()
const timeStr = ref('')
const dateStr = ref('')
const weekStr = ref('')

const weekMap = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

const greetMsg = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const roleText = computed(() => {
  const roles = userStore.roles
  if (!roles || roles.length === 0) return '--'
  const r = roles[0]
  if (r === 'admin' || r === 'ROLE_admin') return '管理员'
  if (r === 'ROLE_DEFAULT') return '普通用户'
  return '业务用户'
})

function updateTime() {
  const now = new Date()
  timeStr.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  dateStr.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  weekStr.value = weekMap[now.getDay()]
}

let timer

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped lang="scss">
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes pulseDot {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(64,158,255,.55); }
  50%      { opacity: .55; box-shadow: 0 0 0 8px rgba(64,158,255,0); }
}
@keyframes scanY {
  0%   { transform: translateY(-30%); opacity: 0; }
  20%  { opacity: 1; }
  100% { transform: translateY(140%); opacity: 0; }
}
@keyframes spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}
@keyframes spinReverse {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(-360deg); }
}
@keyframes orbit {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-8px); }
}
@keyframes glowDot {
  0%, 100% { box-shadow: 0 0 8px rgba(64,158,255,.6), 0 0 16px rgba(64,158,255,.3); }
  50%      { box-shadow: 0 0 14px rgba(64,158,255,.9), 0 0 28px rgba(64,158,255,.5); }
}
@keyframes footDot {
  0%, 100% { box-shadow: 0 0 6px rgba(64,158,255,.7), 0 0 12px rgba(64,158,255,.4); }
  50%      { box-shadow: 0 0 12px rgba(64,158,255,1), 0 0 24px rgba(64,158,255,.6); }
}

.welcome {
  position: relative;
  width: 100%;
  min-height: calc(100vh - 120px);
  padding: 24px 64px;
  box-sizing: border-box;
  animation: fadeInUp .6s ease-out;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.welcome-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 28%, rgba(64,158,255,.08), transparent 38%),
    radial-gradient(circle at 82% 72%, rgba(64,158,255,.06), transparent 42%),
    linear-gradient(rgba(64,158,255,.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64,158,255,.04) 1px, transparent 1px);
  background-size: auto, auto, 50px 50px, 50px 50px;
}

.main-card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto 20px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 12px 40px rgba(64,158,255,.15);
  border: 1px solid rgba(64,158,255,.22);
  overflow: hidden;
}

.card-corner {
  position: absolute;
  width: 24px;
  height: 24px;
  border-color: #fff;
  border-style: solid;
  border-width: 2px;
  pointer-events: none;
  z-index: 10;
}

.cc-tl { top: 16px; left: 16px; border-right: none; border-bottom: none; }
.cc-tr { top: 16px; right: 16px; border-left: none; border-bottom: none; }
.cc-bl { bottom: 16px; left: 16px; border-right: none; border-top: none; }
.cc-br { bottom: 16px; right: 16px; border-left: none; border-top: none; }

.card-scan {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 60px;
  background: linear-gradient(180deg, transparent, rgba(64,158,255,.12), transparent);
  pointer-events: none;
  z-index: 1;
  animation: scanY 6s ease-in-out infinite;
}

.left-pane {
  position: relative;
  width: 100%;
  height: auto;
  background: linear-gradient(135deg, #1a3a6c 0%, var(--el-color-primary, #409eff) 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 36px 28px;
  box-sizing: border-box;
  text-align: center;
}

.left-pane::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255,255,255,.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.04) 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: .9;
  pointer-events: none;
}

.hud-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,.18);
  pointer-events: none;
}

.outer { width: 200px; height: 200px; animation: spin 28s linear infinite; }
.inner { width: 140px; height: 140px; animation: spinReverse 22s linear infinite; }

.orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 170px;
  height: 170px;
  animation: orbit 32s linear infinite;
  pointer-events: none;
}

.dot {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,.8);
  box-shadow: 0 0 12px rgba(255,255,255,.7);
}

.dot-1 { top: 0; left: 50%; transform: translateX(-50%); }
.dot-2 { top: 50%; right: 0; transform: translateY(-50%); }
.dot-3 { bottom: 0; left: 50%; transform: translateX(-50%); }

.particle {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255,255,255,.25);
  pointer-events: none;
  animation: floatY 4s ease-in-out infinite;
}

.p1 { top: 20%; left: 20%; animation-delay: 0s; }
.p2 { top: 70%; left: 25%; animation-delay: .8s; }
.p3 { top: 25%; right: 22%; animation-delay: 1.6s; }
.p4 { top: 75%; right: 18%; animation-delay: 2.4s; }

.hero-badge {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 3px;
  color: #fff;
  background: rgba(255,255,255,.12);
  border: 1px solid rgba(255,255,255,.25);
  border-radius: 20px;
  margin-bottom: 14px;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: pulseDot 2s ease-in-out infinite;
}

.hero-title {
  position: relative;
  z-index: 2;
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #fff;
  line-height: 1.3;
  text-shadow: 0 2px 8px rgba(0,0,0,.15);
}

.hero-subtitle {
  position: relative;
  z-index: 2;
  margin: 0 0 12px;
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 3px;
  color: rgba(255,255,255,.7);
}

.hero-line {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 0;
}

.hero-line .line {
  width: 60px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.5));
}

.hero-line .line:last-child {
  background: linear-gradient(90deg, rgba(255,255,255,.5), transparent);
}

.hero-line .glow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  animation: glowDot 2s ease-in-out infinite;
}

.greet-row {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  margin-bottom: 18px;
  background: linear-gradient(135deg, rgba(64,158,255,.06), rgba(64,158,255,.02));
  border: 1px solid rgba(64,158,255,.15);
  border-radius: 12px;
}

.greet-divider {
  width: 1px;
  height: 32px;
  background: linear-gradient(180deg, transparent, rgba(64,158,255,.3), transparent);
  flex-shrink: 0;
}

.greet-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
  min-width: 70px;
}

.greet-meta-label {
  margin: 0;
  font-size: 11px;
  color: #8a9bb4;
  letter-spacing: 1px;
}

.greet-meta-value {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary, #409eff);
  letter-spacing: .5px;
  line-height: 1.3;
}

.avatar-wrapper {
  position: relative;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
  padding: 2px;
  background: linear-gradient(135deg, var(--el-color-primary, #409eff), var(--el-color-primary-light-4, #a0cfff));
  box-shadow: 0 4px 12px rgba(64,158,255,.25);
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
  border: 2px solid #fff;
  box-sizing: border-box;
  display: block;
}

.greeting-text {
  text-align: left;
  min-width: 0;
  flex: 1;
}

.greeting-sub {
  margin: 0 0 2px;
  font-size: 11px;
  letter-spacing: 1px;
  color: #8a9bb4;
}

.greeting {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: .5px;
  color: #1a3a6c;
}

.punc {
  margin: 0 2px;
  color: #aaa;
  font-weight: 400;
}

.welcome-text {
  font-size: 13px;
  font-weight: 400;
  color: #5a6c8a;
}

.right-pane {
  position: relative;
  width: 100%;
  height: auto;
  padding: 36px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.right-corner {
  position: absolute;
  width: 18px;
  height: 18px;
  border-color: var(--el-color-primary, #409eff);
  border-style: solid;
  border-width: 2px;
  pointer-events: none;
  opacity: .4;
}

.rc-tl { top: 24px; left: 24px; border-right: none; border-bottom: none; }
.rc-tr { top: 24px; right: 24px; border-left: none; border-bottom: none; }
.rc-bl { bottom: 24px; left: 24px; border-right: none; border-top: none; }
.rc-br { bottom: 24px; right: 24px; border-left: none; border-top: none; }

.right-scan {
  position: absolute;
  left: 0; right: 0; top: 0;
  height: 60px;
  background: linear-gradient(180deg, transparent, rgba(64,158,255,.08), transparent);
  pointer-events: none;
  animation: scanY 6s ease-in-out infinite 1.2s;
}

.right-glow {
  position: absolute;
  right: -60px; bottom: -60px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64,158,255,.18), transparent 60%);
  pointer-events: none;
}

.stats-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.card-stats {
  display: none;
}

.stat-item {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255,255,255,.6), rgba(64,158,255,.04));
  border: 1px solid rgba(64,158,255,.12);
  transition: transform .25s, box-shadow .25s, border-color .25s;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 14%;
    bottom: 14%;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: linear-gradient(180deg, var(--el-color-primary, #409eff), var(--el-color-primary-light-4, #a0cfff));
    opacity: .85;
  }

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px rgba(64,158,255,.18);
    border-color: rgba(64,158,255,.3);
  }
}

.stat-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--el-color-primary, #409eff), var(--el-color-primary-light-3, #79bbff));
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(64,158,255,.3);

  svg { width: 20px; height: 20px; }
}

.stat-body {
  text-align: left;
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  margin: 0;
  font-size: 12px;
  color: #8a9bb4;
  letter-spacing: 1px;
}

.stat-value {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a3a6c;
  line-height: 1.2;
  word-break: break-all;
}

.time-value {
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  color: var(--el-color-primary, #409eff);
}

.status-value {
  color: #2bb673;
}

.stat-foot {
  margin: 2px 0 0;
  font-size: 10px;
  color: #a3b1c6;
  letter-spacing: .5px;
  text-transform: uppercase;
  opacity: .7;
}

@media (max-width: 1024px) {
  .stat-foot { display: block; }
}

.welcome-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  max-width: 1200px;
  margin: 0 auto;
  padding-top: 4px;
}

.welcome-foot .foot-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(64,158,255,.35), transparent);
  max-width: 320px;
  position: relative;
}

.welcome-foot .foot-dot {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
  animation: footDot 2s ease-in-out infinite;
}

.welcome-foot .foot-dot:not(.right) { left: 0; }
.welcome-foot .foot-dot.right { right: 0; }

.welcome-foot .foot-text {
  font-size: 11px;
  color: #8a9bb4;
  letter-spacing: 3px;
}

@media (max-width: 1200px) {
  .main-card {
    max-width: 900px;
  }
}

@media (max-width: 992px) {
  .stats-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .welcome {
    min-height: calc(100vh - 100px);
    padding: 32px 20px;
  }

  .left-pane {
    padding: 40px 24px 32px;
  }

  .right-pane {
    padding: 24px;
  }

  .hero-title {
    font-size: 24px;
    letter-spacing: 3px;
  }

  .hero-subtitle {
    font-size: 11px;
    letter-spacing: 2px;
  }

  .hero-line .line {
    width: 40px;
  }

  .stats-list {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .greet-row {
    flex-direction: column;
    text-align: center;
  }

  .greeting-text {
    text-align: center;
  }

  .welcome-foot .foot-line {
    max-width: 80px;
  }

  .welcome-foot .foot-text {
    font-size: 10px;
    letter-spacing: 2px;
  }
}
</style>