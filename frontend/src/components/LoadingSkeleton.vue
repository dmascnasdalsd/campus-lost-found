<script setup lang="ts">
/**
 * 加载态骨架屏：一组灰色占位块，用微光动画提示「内容正在加载」。
 * variant 控制占位布局：list 用于列表卡片，detail 用于详情大图。
 */
withDefaults(
  defineProps<{
    /** 文本占位条的行数。 */
    rows?: number
    /** 骨架形态。 */
    variant?: 'list' | 'detail'
  }>(),
  { rows: 2, variant: 'list' }
)
</script>

<template>
  <div class="loading-skeleton" :class="`loading-skeleton--${variant}`" aria-busy="true" aria-label="加载中">
    <div class="loading-skeleton__cover"></div>
    <div class="loading-skeleton__lines">
      <span v-for="n in rows" :key="n" class="loading-skeleton__line"></span>
    </div>
  </div>
</template>

<style scoped>
.loading-skeleton {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.loading-skeleton--detail {
  flex-direction: column;
  gap: 16px;
}

.loading-skeleton__cover {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 6px;
  background: #eef0f3;
  animation: loading-skeleton-shimmer 1.4s ease-in-out infinite;
}

.loading-skeleton--detail .loading-skeleton__cover {
  width: 100%;
  height: 180px;
}

.loading-skeleton__lines {
  display: grid;
  gap: 10px;
  flex: 1 1 auto;
  align-content: start;
}

.loading-skeleton__line {
  display: block;
  height: 14px;
  border-radius: 6px;
  background: #eef0f3;
  animation: loading-skeleton-shimmer 1.4s ease-in-out infinite;
}

.loading-skeleton__line:last-child {
  width: 60%;
}

@keyframes loading-skeleton-shimmer {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
</style>
