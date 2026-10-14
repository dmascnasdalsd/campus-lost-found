<script setup lang="ts">
import type { ReportStatus } from '@/api/types'

defineProps<{ status: ReportStatus }>()

/** 状态中文文案：列表卡片与详情页共用。 */
const STATUS_LABELS: Record<ReportStatus, string> = {
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
  <span class="status-badge" :class="`status-badge--${status.toLowerCase()}`">
    {{ STATUS_LABELS[status] ?? status }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #eef1f5;
  color: #566272;
}

.status-badge--open {
  background: #e8f4ea;
  color: #2e7d32;
}

.status-badge--claiming,
.status-badge--pending_review {
  background: #fff4e5;
  color: #b26a00;
}

.status-badge--returned,
.status-badge--handed_over {
  background: #e7f0fd;
  color: #1c5fb0;
}

.status-badge--offline,
.status-badge--rejected {
  background: #f2f3f5;
  color: #999;
}
</style>
