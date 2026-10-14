<script setup lang="ts">
/**
 * 错误态：展示中文错误提示与「重试」按钮，点击后由父组件重新请求。
 */
withDefaults(
  defineProps<{
    /** 错误提示文案。 */
    message?: string
  }>(),
  { message: '加载失败，请稍后重试。' }
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="error-state">
    <p class="error-state__message">{{ message }}</p>
    <button class="error-state__retry" type="button" @click="$emit('retry')">重试</button>
  </div>
</template>

<style scoped>
.error-state {
  padding: 32px 16px;
  text-align: center;
  color: #c0392b;
  border: 1px solid #f0c9c4;
  border-radius: 8px;
  background: #fff8f7;
}

.error-state__message {
  margin: 0 0 12px;
  font-size: 14px;
}

.error-state__retry {
  padding: 6px 18px;
  border: none;
  border-radius: 6px;
  background: #c0392b;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>
