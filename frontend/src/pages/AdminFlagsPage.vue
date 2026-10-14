<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { listAdminFlags, resolveFlag } from '@/api/admin'
import type { FlagHandleResult, FlagReason, FlagStatus, FlagTargetType, FlagView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

/** 举报列表筛选：待处理（PENDING）或全部（空）。 */
type Filter = 'PENDING' | ''

const REASON_LABELS: Record<FlagReason, string> = {
  FAKE: '虚假信息',
  AD: '广告',
  ABUSE: '辱骂',
  OTHER: '其他'
}

const STATUS_LABELS: Record<FlagStatus, string> = {
  PENDING: '待处理',
  RESOLVED: '已处置',
  DISMISSED: '已驳回'
}

const TARGET_LABELS: Record<FlagTargetType, string> = {
  REPORT: '启事',
  MESSAGE: '消息'
}

const RESULT_LABELS: Record<FlagHandleResult, string> = {
  DISMISS: '驳回举报',
  TAKE_DOWN: '下架内容',
  DISABLE_USER: '禁用用户'
}

const filter = ref<Filter>('PENDING')
const flags = ref<FlagView[]>([])
const loading = ref(true)
const failed = ref(false)
const actionError = ref('')

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    flags.value = (await listAdminFlags(filter.value || undefined)).items
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function switchFilter(next: Filter): void {
  if (filter.value === next) return
  filter.value = next
  actionError.value = ''
  void load()
}

/** 处置举报：驳回 / 下架内容 / 禁用用户，成功后重新加载。 */
async function onResolve(flag: FlagView, action: FlagHandleResult): Promise<void> {
  actionError.value = ''
  try {
    await resolveFlag(flag.id, action)
    await load()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '操作失败，请稍后重试'
  }
}

/** 被举报内容的表述：启事带标题链接，消息为正文摘要。 */
function contentText(flag: FlagView): string {
  return flag.targetSummary ?? '（内容已删除）'
}

/** 处置结果文案：未处置时为空。 */
function resultLabel(flag: FlagView): string {
  return flag.handleResult ? RESULT_LABELS[flag.handleResult] : ''
}
</script>

<template>
  <section class="admin-flags">
    <header class="admin-flags__header">
      <h1 class="admin-flags__title">举报处理</h1>
      <p class="admin-flags__subtitle">处理用户提交的举报：驳回举报、下架内容或禁用用户。</p>
    </header>

    <div class="filters">
      <button
        class="filter filter-pending"
        :class="{ 'filter--active': filter === 'PENDING' }"
        type="button"
        @click="switchFilter('PENDING')"
      >
        待处理
      </button>
      <button
        class="filter filter-all"
        :class="{ 'filter--active': filter === '' }"
        type="button"
        @click="switchFilter('')"
      >
        全部
      </button>
    </div>

    <p v-if="actionError" class="admin-flags__error">{{ actionError }}</p>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState v-else-if="flags.length === 0" title="暂无举报" hint="当前筛选条件下没有举报。" />
    <ul v-else class="flag-list">
      <li v-for="flag in flags" :key="flag.id" class="flag-card">
        <div class="flag-card__head">
          <h2 class="flag-card__title">
            <span class="flag-card__target">{{ TARGET_LABELS[flag.targetType] }}</span>
            <RouterLink
              v-if="flag.targetType === 'REPORT'"
              class="flag-card__link"
              :to="`/reports/${flag.targetId}`"
            >
              {{ contentText(flag) }}
            </RouterLink>
            <span v-else class="flag-card__summary">{{ contentText(flag) }}</span>
          </h2>
          <span class="flag-card__status" :class="`flag-card__status--${flag.status.toLowerCase()}`">
            {{ STATUS_LABELS[flag.status] }}
          </span>
        </div>

        <p class="flag-card__meta">
          理由：{{ REASON_LABELS[flag.reason] }}
          · 举报人：{{ flag.reporterNickname ?? '匿名用户' }}
          · {{ flag.createdAt }}
        </p>
        <p v-if="flag.detail" class="flag-card__detail">补充说明：{{ flag.detail }}</p>
        <p v-if="flag.handleResult" class="flag-card__result">处置结果：{{ resultLabel(flag) }}</p>

        <div v-if="flag.status === 'PENDING'" class="flag-card__actions">
          <button class="flag-card__dismiss" type="button" @click="onResolve(flag, 'DISMISS')">
            驳回举报
          </button>
          <button class="flag-card__takedown" type="button" @click="onResolve(flag, 'TAKE_DOWN')">
            下架内容
          </button>
          <button class="flag-card__disable" type="button" @click="onResolve(flag, 'DISABLE_USER')">
            禁用用户
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-flags__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.admin-flags__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}

.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.filter {
  padding: 6px 16px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.filter--active {
  border-color: #2e7d32;
  color: #2e7d32;
  font-weight: 600;
}

.admin-flags__error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
}

.admin-flags__empty {
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

.flag-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.flag-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.flag-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.flag-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 16px;
}

.flag-card__target {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #eef1f5;
  color: #566272;
}

.flag-card__link {
  color: #1a1a1a;
  text-decoration: none;
}

.flag-card__link:hover {
  text-decoration: underline;
}

.flag-card__status {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #eef1f5;
  color: #566272;
}

.flag-card__status--pending {
  background: #fff4e5;
  color: #b26a00;
}

.flag-card__status--resolved {
  background: #e7f0fd;
  color: #1c5fb0;
}

.flag-card__meta {
  margin: 6px 0;
  color: #666;
  font-size: 13px;
}

.flag-card__detail,
.flag-card__result {
  margin: 6px 0;
  font-size: 13px;
}

.flag-card__result {
  color: #2e7d32;
}

.flag-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.flag-card__actions button {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.flag-card__dismiss {
  border: 1px solid #566272;
  background: #fff;
  color: #566272;
}

.flag-card__takedown,
.flag-card__disable {
  border: 1px solid #c0392b;
  background: #fff;
  color: #c0392b;
}
</style>
