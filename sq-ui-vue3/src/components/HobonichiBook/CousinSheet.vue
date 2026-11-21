<template>
  <div class="cousin-sheet" :class="{ board: isBoard }">
    <template v-if="isBoard">
      <div class="board-face" :class="boardKind">
        <div v-if="boardKind === 'cover-front'" class="cover-brand">
          <span class="cover-title">{{ coverTitle }}</span>
          <span class="cover-sub">{{ coverSub }}</span>
        </div>
        <div v-else-if="boardKind === 'cover-inside' || boardKind === 'back-inside'" class="cover-inside" />
        <div v-else class="cover-back">
          <span class="year">{{ year }}</span>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="paper">
        <div class="fiber" aria-hidden="true" />
        <slot />
      </div>
    </template>
  </div>
</template>

<script setup>
defineProps({
  page: { type: Object, default: null },
  isRight: { type: Boolean, default: false },
  isBoard: { type: Boolean, default: false },
  boardKind: { type: String, default: '' },
  coverTitle: { type: String, default: '相册日记' },
  coverSub: { type: String, default: 'HOBONICHI STYLE' },
  year: { type: [String, Number], default: () => new Date().getFullYear() }
})
</script>

<style scoped lang="scss">
.cousin-sheet {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: #fbf9f1;
}

.board-face {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-front,
.cover-back {
  background:
    radial-gradient(ellipse at 28% 18%, rgba(255, 255, 255, 0.1), transparent 42%),
    linear-gradient(145deg, #1c2a4a 0%, #15233d 48%, #101a30 100%);
  color: #f4efe4;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.cover-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transform: translateY(-3%);
}

.cover-title {
  font-size: clamp(22px, 3vw, 34px);
  font-weight: 500;
  letter-spacing: 0.18em;
  font-family: 'Zen Kaku Gothic New', 'Noto Sans SC', sans-serif;
}

.cover-sub {
  font-size: 11px;
  letter-spacing: 0.28em;
  opacity: 0.55;
  font-weight: 300;
  font-family: 'Jost', sans-serif;
}

.cover-back .year {
  font-size: 26px;
  letter-spacing: 0.22em;
  opacity: 0.65;
  font-weight: 300;
  font-family: 'Jost', sans-serif;
}

.cover-inside {
  background: #f3eee3;
}

.paper {
  position: absolute;
  inset: 0;
  background: #fbf9f1;
}

.fiber {
  position: absolute;
  inset: 0;
  opacity: 0.035;
  background-image:
    radial-gradient(circle at 20% 30%, #8a7a5c 0 0.6px, transparent 1px),
    radial-gradient(circle at 70% 55%, #8a7a5c 0 0.5px, transparent 1px),
    radial-gradient(circle at 40% 80%, #8a7a5c 0 0.55px, transparent 1px);
  background-size: 18px 22px, 26px 20px, 22px 28px;
  pointer-events: none;
}
</style>
