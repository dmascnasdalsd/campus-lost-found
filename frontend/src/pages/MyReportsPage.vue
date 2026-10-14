<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import {
  deleteReport,
  getReport,
  listMyReports,
  updateReport,
  updateReportStatus
} from '@/api/reports'
import type { Custody, ReportSummary, ReportType, UpdateReportPayload } from '@/api/types'
import { REPORT_CATEGORIES, REPORT_LOCATIONS } from '@/constants/vocabulary'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

/** 发布后可编辑 / 删除的时间窗。 */
const EDIT_WINDOW_MS = 30 * 60 * 1000

const reports = ref<ReportSummary[]>([])
const loading = ref(true)
const failed = ref(false)
const busy = ref(false)
const actionError = ref('')

/** 用于计算编辑窗口剩余时间；每 30 秒刷新一次。 */
const now = ref(Date.now())
let timer: number | undefined

const editingId = ref<number | null>(null)
const editType = ref<ReportType>('LOST')
const editForm = reactive({
  title: '',
  category: '',
  location: '',
  locationDetail: '',
  eventTime: '',
  description: '',
  custody: 'SELF' as Custody,
  handoverNo: ''
})

/** 正在填写移交单号的启事 id。 */
const handoverId = ref<number | null>(null)
const handoverNo = ref('')

onMounted(async () => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 30_000)
  await load()
})

onUnmounted(() => {
  if (timer !== undefined) {
    window.clearInterval(timer)
  }
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    reports.value = (await listMyReports()).items
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function errorOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

function createdAtMs(report: ReportSummary): number {
  const parsed = Date.parse(report.createdAt)
  return Number.isNaN(parsed) ? 0 : parsed
}

/** 是否仍在 30 分钟编辑窗口内。 */
function isEditable(report: ReportSummary): boolean {
  return createdAtMs(report) + EDIT_WINDOW_MS > now.value
}

function remainingMinutes(report: ReportSummary): number {
  const diff = createdAtMs(report) + EDIT_WINDOW_MS - now.value
  return diff <= 0 ? 0 : Math.ceil(diff / 60000)
}

function windowText(report: ReportSummary): string {
  return isEditable(report)
    ? `可编辑（剩余 ${remainingMinutes(report)} 分钟）`
    : '已超过 30 分钟编辑窗口'
}

/** 只有仍处于招领中（OPEN）的启事才提供操作。 */
function canAct(report: ReportSummary): boolean {
  return report.status === 'OPEN'
}

/** 招领且保管方式为「自留」时，才可标记已移交。 */
function canHandover(report: ReportSummary): boolean {
  return report.type === 'FOUND' && report.custody === 'SELF'
}

async function openEdit(report: ReportSummary): Promise<void> {
  actionError.value = ''
  handoverId.value = null
  try {
    const detail = await getReport(report.id)
    editingId.value = report.id
    editType.value = detail.type
    editForm.title = detail.title
    editForm.category = detail.category
    editForm.location = detail.location
    editForm.locationDetail = detail.locationDetail ?? ''
    editForm.eventTime = detail.eventTime ? detail.eventTime.slice(0, 16) : ''
    editForm.description = detail.description ?? ''
    editForm.custody = detail.custody ?? 'SELF'
    editForm.handoverNo = detail.handoverNo ?? ''
  } catch (error) {
    actionError.value = errorOf(error)
  }
}

function cancelEdit(): void {
  editingId.value = null
  actionError.value = ''
}

async function saveEdit(): Promise<void> {
  if (editingId.value === null) return
  busy.value = true
  actionError.value = ''
  try {
    const payload: UpdateReportPayload = {
      title: editForm.title.trim(),
      category: editForm.category,
      location: editForm.location,
      locationDetail: editForm.locationDetail.trim(),
      eventTime: editForm.eventTime,
      description: editForm.description.trim()
    }
    if (editType.value === 'FOUND') {
      payload.custody = editForm.custody
      payload.handoverNo = editForm.custody === 'AT_SECURITY' ? editForm.handoverNo.trim() : ''
    }
    await updateReport(editingId.value, payload)
    editingId.value = null
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  } finally {
    busy.value = false
  }
}

async function removeReport(report: ReportSummary): Promise<void> {
  if (!window.confirm(`确认删除启事「${report.title}」？删除后不可恢复。`)) {
    return
  }
  busy.value = true
  actionError.value = ''
  try {
    await deleteReport(report.id)
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  } finally {
    busy.value = false
  }
}

async function markReturned(report: ReportSummary): Promise<void> {
  if (!window.confirm(`确认把「${report.title}」标记为已归还？`)) {
    return
  }
  busy.value = true
  actionError.value = ''
  try {
    await updateReportStatus(report.id, 'RETURNED')
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  } finally {
    busy.value = false
  }
}

function openHandover(report: ReportSummary): void {
  actionError.value = ''
  editingId.value = null
  handoverId.value = report.id
  handoverNo.value = ''
}

async function confirmHandover(report: ReportSummary): Promise<void> {
  const no = handoverNo.value.trim()
  if (no === '') {
    actionError.value = '请填写移交单号'
    return
  }
  if (!window.confirm(`确认把「${report.title}」标记为已移交保卫处？`)) {
    return
  }
  busy.value = true
  actionError.value = ''
  try {
    await updateReportStatus(report.id, 'HANDED_OVER', no)
    handoverId.value = null
    handoverNo.value = ''
    await load()
  } catch (error) {
    actionError.value = errorOf(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="my-reports">
    <header class="my-reports__header">
      <h1 class="my-reports__title">我的发布</h1>
      <p class="my-reports__subtitle">管理自己的启事：发布后 30 分钟内可编辑或删除，随时可标记归还 / 移交。</p>
    </header>

    <p v-if="actionError" class="my-reports__error">{{ actionError }}</p>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState
      v-else-if="reports.length === 0"
      title="还没有发布"
      hint="发布一条启事，让大家帮你一起找。"
      action-text="发布启事"
      action-to="/reports/new"
    />
    <template v-else>
      <ul class="my-reports__list">
        <li v-for="report in reports" :key="report.id" class="my-report-card">
          <div class="my-report-card__head">
            <h2 class="my-report-card__title">
              <span class="badge" :class="report.type === 'LOST' ? 'badge--lost' : 'badge--found'">
                {{ report.type === 'LOST' ? '失物' : '招领' }}
              </span>
              {{ report.title }}
            </h2>
            <StatusBadge class="my-report-card__status" :status="report.status" />
          </div>
          <p class="my-report-card__meta">{{ report.location }} · {{ report.category }}</p>
          <p class="edit-window" :class="{ 'edit-window--expired': !isEditable(report) }">
            {{ windowText(report) }}
          </p>

          <div v-if="canAct(report)" class="my-report-card__actions">
            <button class="action-edit" type="button" :disabled="!isEditable(report) || busy" @click="openEdit(report)">
              编辑
            </button>
            <button
              class="action-delete"
              type="button"
              :disabled="!isEditable(report) || busy"
              @click="removeReport(report)"
            >
              删除
            </button>
            <button class="action-return" type="button" :disabled="busy" @click="markReturned(report)">
              标记已归还
            </button>
            <button
              v-if="canHandover(report)"
              class="action-handover"
              type="button"
              :disabled="busy"
              @click="openHandover(report)"
            >
              标记已移交
            </button>
          </div>

          <div v-if="handoverId === report.id" class="handover-form">
            <input v-model="handoverNo" class="handover-no" type="text" placeholder="请填写移交单号" />
            <button class="handover-confirm" type="button" :disabled="busy" @click="confirmHandover(report)">
              确认移交
            </button>
            <button class="handover-cancel" type="button" @click="handoverId = null">取消</button>
          </div>

          <form v-if="editingId === report.id" class="edit-form" @submit.prevent="saveEdit">
            <label class="field">
              <span class="field__label">物品名称</span>
              <input v-model="editForm.title" class="field__input" type="text" />
            </label>
            <label class="field">
              <span class="field__label">分类</span>
              <select v-model="editForm.category" class="field__input">
                <option v-for="item in REPORT_CATEGORIES" :key="item" :value="item">{{ item }}</option>
              </select>
            </label>
            <label class="field">
              <span class="field__label">地点</span>
              <select v-model="editForm.location" class="field__input">
                <option v-for="item in REPORT_LOCATIONS" :key="item" :value="item">{{ item }}</option>
              </select>
            </label>
            <label class="field">
              <span class="field__label">地点补充描述（可选）</span>
              <input v-model="editForm.locationDetail" class="field__input" type="text" />
            </label>
            <label class="field">
              <span class="field__label">{{ editType === 'LOST' ? '丢失时间' : '拾获时间' }}</span>
              <input v-model="editForm.eventTime" class="field__input" type="datetime-local" />
            </label>
            <label class="field">
              <span class="field__label">描述（可选）</span>
              <textarea v-model="editForm.description" class="field__input" rows="3"></textarea>
            </label>
            <template v-if="editType === 'FOUND'">
              <label class="field">
                <span class="field__label">保管方式</span>
                <select v-model="editForm.custody" class="field__input">
                  <option value="SELF">自留（站内约定交还）</option>
                  <option value="AT_SECURITY">已交保卫处</option>
                </select>
              </label>
              <label v-if="editForm.custody === 'AT_SECURITY'" class="field">
                <span class="field__label">移交单号</span>
                <input v-model="editForm.handoverNo" class="field__input" type="text" />
              </label>
            </template>
            <div class="edit-form__actions">
              <button class="edit-form__save" type="submit" :disabled="busy">保存修改</button>
              <button class="edit-form__cancel" type="button" @click="cancelEdit">取消</button>
            </div>
          </form>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.my-reports__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.my-reports__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}

.my-reports__error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
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

/* 窄屏：编辑表单控件占满一行，避免横向溢出。 */
@media (max-width: 768px) {
  .field__input,
  .handover-no {
    width: 100%;
  }
}

.my-reports__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.my-report-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.my-report-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.my-report-card__title {
  margin: 0;
  font-size: 16px;
}

.my-report-card__meta {
  margin: 6px 0 4px;
  color: #666;
  font-size: 13px;
}

.edit-window {
  margin: 0 0 10px;
  font-size: 12px;
  color: #2e7d32;
}

.edit-window--expired {
  color: #999;
}

.my-report-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.my-report-card__actions button {
  padding: 6px 12px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.my-report-card__actions button:disabled {
  opacity: 0.5;
  cursor: default;
}

.action-return,
.action-handover {
  border-color: #2e7d32;
  color: #2e7d32;
}

.handover-form,
.edit-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.handover-form {
  margin-top: 10px;
}

.handover-no {
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
}

.handover-confirm,
.edit-form__save {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.handover-cancel,
.edit-form__cancel {
  padding: 6px 12px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.edit-form {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e4e6eb;
}

.field {
  display: grid;
  gap: 4px;
}

.field__label {
  font-size: 12px;
  color: #444;
}

.field__input {
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
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
