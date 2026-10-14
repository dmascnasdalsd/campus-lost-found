<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAuditLogs } from '@/api/admin'
import type { AuditLogView, ReportActionType } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

const ACTION_LABELS: Record<ReportActionType, string> = {
  APPROVE: '通过',
  REJECT: '驳回',
  TAKE_DOWN: '下架',
  PIN: '置顶',
  UNPIN: '取消置顶'
}

const PAGE_SIZE = 20

const logs = ref<AuditLogView[]>([])
const loading = ref(true)
const failed = ref(false)
const page = ref(0)
const total = ref(0)

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const result = await listAuditLogs(page.value, PAGE_SIZE)
    logs.value = result.items
    total.value = result.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function hasPrev(): boolean {
  return page.value > 0
}

function hasNext(): boolean {
  return (page.value + 1) * PAGE_SIZE < total.value
}

function goPrev(): void {
  if (!hasPrev()) return
  page.value -= 1
  void load()
}

function goNext(): void {
  if (!hasNext()) return
  page.value += 1
  void load()
}
</script>

<template>
  <section class="audit-logs">
    <header class="audit-logs__header">
      <h1 class="audit-logs__title">审计日志</h1>
      <p class="audit-logs__subtitle">记录所有内容治理动作：操作人、动作、目标启事、原因与时间。</p>
    </header>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState v-else-if="logs.length === 0" title="暂无审计记录" hint="还没有内容治理动作记录。" />
    <template v-else>
      <ul class="audit-log-list">
        <li v-for="log in logs" :key="log.id" class="audit-log">
          <div class="audit-log__head">
            <span class="audit-log__action" :class="`audit-log__action--${log.action.toLowerCase()}`">
              {{ ACTION_LABELS[log.action] ?? log.action }}
            </span>
            <span class="audit-log__operator">操作人：{{ log.operatorNickname ?? `#${log.operatorId}` }}</span>
            <span class="audit-log__time">{{ log.createdAt }}</span>
          </div>
          <p class="audit-log__target">
            目标启事：{{ log.reportTitle ?? `#${log.reportId}` }}
          </p>
          <p v-if="log.reason" class="audit-log__reason">原因：{{ log.reason }}</p>
        </li>
      </ul>

      <div class="audit-pager">
        <button class="audit-prev" type="button" :disabled="!hasPrev()" @click="goPrev">上一页</button>
        <span class="audit-pager__info">第 {{ page + 1 }} 页 · 共 {{ total }} 条</span>
        <button class="audit-next" type="button" :disabled="!hasNext()" @click="goNext">下一页</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.audit-logs__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.audit-logs__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}

.audit-logs__empty {
  padding: 32px 16px;
  text-align: center;
  color: #666;
  border: 1px dashed #d9dbe0;
  border-radius: 8px;
  background: #fff;
}

.state {
  color: #666;
}

.state--error {
  color: #c0392b;
}

.skeleton-list {
  display: grid;
  gap: 12px;
}

.audit-log-list {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: grid;
  gap: 12px;
}

.audit-log {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.audit-log__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.audit-log__action {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #eef1f5;
  color: #566272;
}

.audit-log__action--approve {
  background: #e8f4ea;
  color: #2e7d32;
}

.audit-log__action--reject,
.audit-log__action--take_down {
  background: #fdecea;
  color: #c0392b;
}

.audit-log__action--pin,
.audit-log__action--unpin {
  background: #fff4e5;
  color: #b26a00;
}

.audit-log__operator {
  font-size: 13px;
  color: #444;
}

.audit-log__time {
  margin-left: auto;
  font-size: 12px;
  color: #999;
}

.audit-log__target {
  margin: 6px 0 0;
  font-size: 13px;
  color: #1a1a1a;
}

.audit-log__reason {
  margin: 4px 0 0;
  font-size: 13px;
  color: #c0392b;
}

.audit-pager {
  display: flex;
  align-items: center;
  gap: 12px;
}

.audit-prev,
.audit-next {
  padding: 6px 12px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.audit-prev:disabled,
.audit-next:disabled {
  opacity: 0.5;
  cursor: default;
}

.audit-pager__info {
  font-size: 13px;
  color: #666;
}
</style>
