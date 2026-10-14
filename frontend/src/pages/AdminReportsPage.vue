<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import {
  approveReport,
  listAdminReports,
  pinReport,
  rejectReport,
  takeDownReport,
  unpinReport
} from '@/api/admin'
import type { ReportDetail } from '@/api/types'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

/** 复核队列筛选：待复核（PENDING_REVIEW）或全部（空）。 */
type Filter = 'PENDING_REVIEW' | ''
/** 需要填写原因的治理动作。 */
type ReasonAction = 'reject' | 'take-down'

const filter = ref<Filter>('PENDING_REVIEW')
const reports = ref<ReportDetail[]>([])
const loading = ref(true)
const failed = ref(false)
const actionError = ref('')

// 原因弹窗状态：目标启事、动作类型、原因输入、行内校验错误与提交中标记。
const reasonTarget = ref<ReportDetail | null>(null)
const reasonAction = ref<ReasonAction>('reject')
const reasonText = ref('')
const reasonError = ref('')
const submitting = ref(false)

const reasonTitle = computed(() => (reasonAction.value === 'reject' ? '驳回启事' : '下架启事'))

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    reports.value = (await listAdminReports(filter.value || undefined)).items
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

function errorOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

/** 复核通过：确认后恢复为 OPEN 并重新加载。 */
async function onApprove(report: ReportDetail): Promise<void> {
  if (!window.confirm(`确认通过启事「${report.title}」？通过后恢复为招领中。`)) {
    return
  }
  actionError.value = ''
  try {
    await approveReport(report.id)
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  }
}

/** 置顶 / 取消置顶：按当前置顶状态切换并重新加载。 */
async function onTogglePin(report: ReportDetail): Promise<void> {
  actionError.value = ''
  try {
    if (report.pinned) {
      await unpinReport(report.id)
    } else {
      await pinReport(report.id)
    }
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  }
}

/** 打开原因弹窗（驳回 / 下架）。 */
function openReason(report: ReportDetail, action: ReasonAction): void {
  reasonTarget.value = report
  reasonAction.value = action
  reasonText.value = ''
  reasonError.value = ''
}

function closeReason(): void {
  reasonTarget.value = null
  reasonText.value = ''
  reasonError.value = ''
}

/** 提交原因：必填，成功后关闭弹窗并重新加载。 */
async function submitReason(): Promise<void> {
  if (!reasonTarget.value) {
    return
  }
  if (reasonText.value.trim() === '') {
    reasonError.value = reasonAction.value === 'reject' ? '请填写驳回原因' : '请填写下架原因'
    return
  }
  submitting.value = true
  reasonError.value = ''
  try {
    if (reasonAction.value === 'reject') {
      await rejectReport(reasonTarget.value.id, reasonText.value.trim())
    } else {
      await takeDownReport(reasonTarget.value.id, reasonText.value.trim())
    }
    closeReason()
    await load()
  } catch (error) {
    reasonError.value = errorOf(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="admin-reports">
    <header class="admin-reports__header">
      <h1 class="admin-reports__title">内容治理</h1>
      <p class="admin-reports__subtitle">复核被举报或被下架的启事，通过、驳回、下架或置顶。</p>
    </header>

    <div class="filters">
      <button
        class="filter filter-pending"
        :class="{ 'filter--active': filter === 'PENDING_REVIEW' }"
        type="button"
        @click="switchFilter('PENDING_REVIEW')"
      >
        待复核
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

    <p v-if="actionError" class="admin-reports__error">{{ actionError }}</p>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState v-else-if="reports.length === 0" title="暂无启事" hint="当前筛选条件下没有启事。" />
    <ul v-else class="admin-report-list">
      <li v-for="report in reports" :key="report.id" class="admin-report-card">
        <div class="admin-report-card__head">
          <h2 class="admin-report-card__title">
            <span class="badge" :class="report.type === 'LOST' ? 'badge--lost' : 'badge--found'">
              {{ report.type === 'LOST' ? '失物' : '招领' }}
            </span>
            <RouterLink class="admin-report-card__link" :to="`/reports/${report.id}`">
              {{ report.title }}
            </RouterLink>
            <span v-if="report.pinned" class="admin-report-card__pinned">已置顶</span>
          </h2>
          <StatusBadge :status="report.status" />
        </div>

        <p class="admin-report-card__meta">
          {{ report.location }} · {{ report.category }} · 发布于 {{ report.createdAt }}
        </p>
        <p v-if="report.description" class="admin-report-card__desc">{{ report.description }}</p>

        <ul v-if="report.images.length" class="admin-report-card__images">
          <li v-for="image in report.images" :key="image.id">
            <img class="admin-report-card__thumb" :src="image.path" :alt="image.originalName" />
          </li>
        </ul>

        <p v-if="report.rejectReason" class="admin-report-card__reason">
          驳回原因：{{ report.rejectReason }}
        </p>

        <div class="admin-report-card__actions">
          <template v-if="report.status === 'PENDING_REVIEW'">
            <button class="admin-card__approve" type="button" @click="onApprove(report)">通过</button>
            <button class="admin-card__reject" type="button" @click="openReason(report, 'reject')">
              驳回
            </button>
            <button class="admin-card__takedown" type="button" @click="openReason(report, 'take-down')">
              下架
            </button>
          </template>
          <template v-else-if="report.status === 'OPEN'">
            <button class="admin-card__takedown" type="button" @click="openReason(report, 'take-down')">
              下架
            </button>
            <button class="admin-card__pin" type="button" @click="onTogglePin(report)">
              {{ report.pinned ? '取消置顶' : '置顶' }}
            </button>
          </template>
        </div>
      </li>
    </ul>

    <div v-if="reasonTarget" class="reason-modal" role="dialog">
      <div class="reason-modal__box">
        <h2 class="reason-modal__title">{{ reasonTitle }}</h2>
        <textarea
          v-model="reasonText"
          class="reason-modal__reason"
          rows="3"
          placeholder="请填写原因"
        ></textarea>
        <p v-if="reasonError" class="reason-modal__error">{{ reasonError }}</p>
        <div class="reason-modal__actions">
          <button
            class="reason-modal__submit"
            type="button"
            :disabled="submitting"
            @click="submitReason"
          >
            {{ submitting ? '提交中…' : '确认' }}
          </button>
          <button class="reason-modal__cancel" type="button" @click="closeReason">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-reports__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.admin-reports__subtitle {
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

.admin-reports__error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
}

.admin-reports__empty {
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

.admin-report-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.admin-report-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.admin-report-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.admin-report-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 16px;
}

.admin-report-card__link {
  color: #1a1a1a;
  text-decoration: none;
}

.admin-report-card__link:hover {
  text-decoration: underline;
}

.admin-report-card__pinned {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #fff4e5;
  color: #b26a00;
}

.admin-report-card__meta {
  margin: 6px 0;
  color: #666;
  font-size: 13px;
}

.admin-report-card__desc {
  margin: 6px 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.admin-report-card__images {
  display: flex;
  gap: 8px;
  list-style: none;
  margin: 6px 0;
  padding: 0;
}

.admin-report-card__thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e4e6eb;
}

.admin-report-card__reason {
  margin: 6px 0;
  color: #c0392b;
  font-size: 13px;
}

.admin-report-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.admin-report-card__actions button {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.admin-card__approve {
  border: none;
  background: #2e7d32;
  color: #fff;
}

.admin-card__reject,
.admin-card__takedown {
  border: 1px solid #c0392b;
  background: #fff;
  color: #c0392b;
}

.admin-card__pin {
  border: 1px solid #1c5fb0;
  background: #fff;
  color: #1c5fb0;
}

.reason-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.reason-modal__box {
  width: 320px;
  padding: 16px;
  border-radius: 8px;
  background: #fff;
}

.reason-modal__title {
  margin: 0 0 10px;
  font-size: 16px;
}

.reason-modal__reason {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.reason-modal__error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 13px;
}

.reason-modal__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.reason-modal__submit {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.reason-modal__cancel {
  padding: 6px 14px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
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
</style>
