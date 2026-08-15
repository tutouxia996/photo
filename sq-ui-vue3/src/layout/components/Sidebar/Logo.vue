<template>
  <div class="sidebar-logo-container" :class="{ 'collapse': collapse }">
    <span class="hud-corner hc-tl"></span>
    <span class="hud-corner hc-tr"></span>
    <span class="hud-corner hc-bl"></span>
    <span class="hud-corner hc-br"></span>
    <span class="scan-line"></span>
    <span class="scan-line scan-line-bottom"></span>
    <span class="data-stream"></span>
    <span class="binary-rain bin-1">10110</span>
    <span class="binary-rain bin-2">01001</span>
    <span class="edge-glow edge-l"></span>
    <span class="edge-glow edge-r"></span>
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link" to="/">
        <span class="dot-left"></span>
        <span class="dot-right"></span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <span class="sidebar-title">{{ title }}</span>
        <span class="status-led"></span>
      </router-link>
    </transition>
  </div>
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import variables from '@/assets/styles/variables.module.scss'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const title = import.meta.env.VITE_APP_TITLE
const settingsStore = useSettingsStore()
const sideTheme = computed(() => settingsStore.sideTheme)

const getLogoBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

const getLogoTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? '#ffffff' : variables.menuLightText
})
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables.module.scss';

.sidebarLogoFade-enter-active {
  transition: opacity .6s;
}

.sidebarLogoFade-enter-from,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 50px;
  background: v-bind(getLogoBackground);
  text-align: center;
  overflow: hidden;
  border-bottom: 1px solid rgba(64, 158, 255, .15);
  box-shadow: 0 4px 20px -5px rgba(64,158,255,.35);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      radial-gradient(160% 80% at 0% 50%, rgba(64,158,255,.18), transparent 65%),
      radial-gradient(100% 140% at 100% 50%, rgba(64,158,255,.1), transparent 60%),
      repeating-linear-gradient(135deg, transparent 0 6px, rgba(64,158,255,.05) 6px 7px),
      radial-gradient(rgba(64,158,255,.18) 1px, transparent 1px);
    background-size: auto, auto, auto, 14px 14px;
    background-position: 0 0, 0 0, 0 0, 0 0;
    opacity: 1;
    pointer-events: none;
  }

  &::after {
    content: none;
  }

  /* 四角 HUD 角标（双层） */
  .hud-corner {
    position: absolute;
    width: 10px;
    height: 10px;
    border-color: rgba(64,158,255,.85);
    border-style: solid;
    border-width: 0;
    pointer-events: none;
    z-index: 2;
  }
  .hud-corner::before {
    content: '';
    position: absolute;
    width: 4px;
    height: 4px;
    border-color: rgba(64,158,255,.5);
    border-style: solid;
    border-width: 0;
  }
  .hc-tl { top: 4px; left: 6px; border-top-width: 1px; border-left-width: 1px; }
  .hc-tl::before { top: 2px; left: 2px; border-top-width: 1px; border-left-width: 1px; }
  .hc-tr { top: 4px; right: 6px; border-top-width: 1px; border-right-width: 1px; }
  .hc-tr::before { top: 2px; right: 2px; border-top-width: 1px; border-right-width: 1px; }
  .hc-bl { bottom: 4px; left: 6px; border-bottom-width: 1px; border-left-width: 1px; }
  .hc-bl::before { bottom: 2px; left: 2px; border-bottom-width: 1px; border-left-width: 1px; }
  .hc-br { bottom: 4px; right: 6px; border-bottom-width: 1px; border-right-width: 1px; }
  .hc-br::before { bottom: 2px; right: 2px; border-bottom-width: 1px; border-right-width: 1px; }

  /* 顶部扫光带 */
  .scan-line {
    position: absolute;
    top: 0;
    left: -40%;
    width: 40%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(64,158,255,1), transparent);
    pointer-events: none;
    animation: scanX 4s ease-in-out infinite;
    z-index: 1;
  }

  .scan-line-bottom {
    top: auto;
    bottom: 0;
    background: linear-gradient(90deg, transparent, rgba(64,158,255,.7), transparent);
    animation: scanXReverse 5s ease-in-out infinite;
    animation-delay: 1.2s;
  }

  @keyframes scanX {
    0% { left: -40%; opacity: 0; }
    20% { opacity: 1; }
    80% { opacity: 1; }
    100% { left: 100%; opacity: 0; }
  }

  @keyframes scanXReverse {
    0% { left: 100%; opacity: 0; }
    20% { opacity: 1; }
    80% { opacity: 1; }
    100% { left: -40%; opacity: 0; }
  }

  /* 中部水平虚线数据流 */
  .data-stream {
    position: absolute;
    left: 12px;
    right: 12px;
    top: 50%;
    height: 1px;
    background-image: linear-gradient(90deg, rgba(64,158,255,.45) 50%, transparent 50%);
    background-size: 6px 1px;
    transform: translateY(-50%);
    opacity: .35;
    pointer-events: none;
    animation: streamX 3s linear infinite;
    z-index: 0;
  }

  @keyframes streamX {
    0% { background-position: 0 0; }
    100% { background-position: 60px 0; }
  }

  /* 角落二进制字符流 */
  .binary-rain {
    position: absolute;
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 8px;
    font-weight: 400;
    color: rgba(64,158,255,.4);
    letter-spacing: 1px;
    pointer-events: none;
    z-index: 1;
    text-shadow: 0 0 4px rgba(64,158,255,.6);
    animation: binFlicker 1.4s steps(2) infinite;
  }
  .bin-1 { top: 4px; left: 18px; }
  .bin-2 { bottom: 4px; right: 18px; animation-delay: .7s; }

  @keyframes binFlicker {
    0%, 100% { opacity: .5; }
    50% { opacity: .9; }
  }

  /* 左右边缘光带 */
  .edge-glow {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 12px;
    pointer-events: none;
    z-index: 0;
  }
  .edge-l {
    left: 0;
    background: linear-gradient(90deg, rgba(64,158,255,.25), transparent);
    animation: edgePulse 2.6s ease-in-out infinite;
  }
  .edge-r {
    right: 0;
    background: linear-gradient(-90deg, rgba(64,158,255,.18), transparent);
    animation: edgePulse 2.6s ease-in-out infinite;
    animation-delay: 1.3s;
  }

  @keyframes edgePulse {
    0%, 100% { opacity: .6; }
    50% { opacity: 1; }
  }

  .sidebar-logo-link {
    position: relative;
    z-index: 1;
    height: 100%;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    padding: 0 6px;
    box-sizing: border-box;
    text-decoration: none;
  }

  /* 标题前的双蓝竖标记（数据脉冲） */
  .title-mark {
    width: 5px;
    height: 14px;
    flex-shrink: 0;
    position: relative;
  }
  .title-mark::before,
  .title-mark::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    border-radius: 2px;
    background: linear-gradient(180deg, var(--el-color-primary-light-4, #a0cfff), var(--el-color-primary, #409eff));
    box-shadow: 0 0 8px rgba(64,158,255,.7);
  }
  .title-mark::before { left: 0; animation: markBlink 1.6s ease-in-out infinite; }
  .title-mark::after  { right: 0; animation: markBlink 1.6s ease-in-out infinite; animation-delay: .4s; opacity: .5; }

  @keyframes markBlink {
    0%, 100% { opacity: 1; transform: scaleY(1); }
    50% { opacity: .4; transform: scaleY(.7); }
  }

  /* 标题后的状态指示灯（双层扩散） */
  .status-led {
    position: relative;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #2bd47a;
    box-shadow: 0 0 8px rgba(43,212,122,.85);
    flex-shrink: 0;
    animation: ledPulse 1.8s ease-in-out infinite;
  }

  .status-led::before {
    content: '';
    position: absolute;
    inset: -3px;
    border-radius: 50%;
    border: 1px solid rgba(43,212,122,.45);
    animation: ledRing 1.8s ease-out infinite;
  }

  .status-led::after {
    content: '';
    position: absolute;
    inset: -6px;
    border-radius: 50%;
    border: 1px solid rgba(43,212,122,.25);
    animation: ledRing 1.8s ease-out infinite;
    animation-delay: .9s;
  }

  @keyframes ledPulse {
    0%, 100% { box-shadow: 0 0 6px rgba(43,212,122,.6); }
    50% { box-shadow: 0 0 14px rgba(43,212,122,1); }
  }

  @keyframes ledRing {
    0% { transform: scale(.8); opacity: 1; }
    100% { transform: scale(1.8); opacity: 0; }
  }

  .sidebar-title {
    position: relative;
    display: inline-flex;
    align-items: center;
    height: 100%;
    margin: 0;
    color: v-bind(getLogoTextColor);
    font-weight: 600;
    font-size: 16px;
    letter-spacing: 0;
    line-height: 1;
    padding: 0;
    font-family: 'PingFang SC', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif;
    white-space: nowrap;
    background: linear-gradient(90deg, v-bind(getLogoTextColor) 0%, var(--el-color-primary, #409eff) 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
    filter: drop-shadow(0 0 10px rgba(64,158,255,.3));
    animation: titleGlow 3s ease-in-out infinite;
    transition: letter-spacing .3s ease;
  }

  @keyframes titleGlow {
    0%, 100% {
      filter: drop-shadow(0 0 10px rgba(64,158,255,.25));
    }
    50% {
      filter: drop-shadow(0 0 22px rgba(64,158,255,.6));
    }
  }

  .sidebar-logo-link:hover .sidebar-title {
    letter-spacing: 1px;
  }

  .dot-left,
  .dot-right {
    position: relative;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--el-color-primary, #409eff);
    box-shadow: 0 0 12px rgba(64,158,255,.7);
    animation: dotPulse 2.2s ease-in-out infinite;
  }

  .dot-left {
    animation-delay: 0s;
  }

  .dot-right {
    margin-left: 7px;
    animation-delay: .45s;
  }

  @keyframes dotPulse {
    0%, 100% {
      transform: scale(1);
      opacity: .75;
    }
    50% {
      transform: scale(1.15);
      opacity: 1;
      box-shadow: 0 0 18px rgba(64,158,255,1);
    }
  }

  &.collapse {
    .sidebar-logo-link {
      padding: 0;
      gap: 0;
    }
  }
}
</style>
