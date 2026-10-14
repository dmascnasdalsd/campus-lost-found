<script setup lang="ts">
import type { ReportSummary } from '@/api/types'

defineProps<{ report: ReportSummary }>()

const statusLabels: Record<string, string> = {
  OPEN: '招领中',
  PENDING_REVIEW: '待复核',
  CLAIMING: '认领中',
  RETURNED: '已归还',
  HANDED_OVER: '已移交',
  OFFLINE: '已下架',
  REJECTED: '已驳回'
}
</script>

<template>
  <article class="report-card">
    <img
      v-if="report.thumbnailPath"
      class="report-card__thumb"
      :src="report.thumbnailPath"
      :alt="report.title"
    />
    <div v-else class="report-card__thumb report-card__thumb--empty">暂无图片</div>

    <div class="report-card__body">
      <h2 class="report-card__title">
        <span class="badge" :class="report.type === 'LOST' ? 'badge--lost' : 'badge--found'">
          {{ report.type === 'LOST' ? '失物' : '招领' }}
        </span>
        {{ report.title }}
      </h2>
      <p class="report-card__meta">{{ report.location }} · {{ report.category }}</p>
      <p class="report-card__meta">
        状态：{{ statusLabels[report.status] ?? report.status }}
        <span v-if="report.pinned" class="badge badge--pinned">置顶</span>
      </p>
    </div>
  </article>
</template>

<style scoped>
.report-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.report-card__thumb {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f2f3f5;
}

.report-card__thumb--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
}

.report-card__title {
  margin: 0 0 8px;
  font-size: 16px;
}

.report-card__meta {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}

.badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
}

.badge--lost {
  background: #fdecea;
  color: #c0392b;
}

.badge--found {
  background: #e8f4ea;
  color: #2e7d32;
}

.badge--pinned {
  background: #fff4e5;
  color: #b26a00;
}
</style>
