<script setup lang="ts">
import { RouterLink } from 'vue-router'

/**
 * 空态：默认展示「还没有启事」引导；可通过 title / hint / actionText / actionTo 定制，
 * 提供 actionTo 时渲染一个跳转链接（RouterLink）。也可用默认插槽补充自定义内容。
 */
withDefaults(
  defineProps<{
    /** 标题文案。 */
    title?: string
    /** 引导提示文案。 */
    hint?: string
    /** 跳转链接的文字。 */
    actionText?: string
    /** 跳转目标路径，提供后渲染跳转链接。 */
    actionTo?: string
  }>(),
  {
    title: '还没有启事',
    hint: '没有找到自己丢的东西？发出第一条启事，让大家帮你一起找。',
    actionText: '',
    actionTo: ''
  }
)
</script>

<template>
  <div class="empty-state">
    <p class="empty-state__title">{{ title }}</p>
    <p class="empty-state__hint">{{ hint }}</p>
    <RouterLink v-if="actionTo" class="empty-state__action" :to="actionTo">
      {{ actionText || '去看看' }}
    </RouterLink>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  padding: 48px 16px;
  text-align: center;
  border: 1px dashed #d9dbe0;
  border-radius: 8px;
  background: #fff;
}

.empty-state__title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.empty-state__hint {
  margin: 0;
  color: #666;
  font-size: 13px;
}

.empty-state__action {
  display: inline-block;
  margin-top: 16px;
  padding: 8px 20px;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}
</style>
