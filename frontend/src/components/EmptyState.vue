<template>
  <div class="empty-state">
    <p class="empty-state__title">还没有启事</p>
    <p class="empty-state__hint">没有找到自己丢的东西？发出第一条启事，让大家帮你一起找。</p>
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
</style>
