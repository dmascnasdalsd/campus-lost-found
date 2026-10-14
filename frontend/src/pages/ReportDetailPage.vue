<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { getReport } from '@/api/reports'
import { createClaim } from '@/api/claims'
import { createReportFlag } from '@/api/flags'
import type { FlagReason, ReportDetail } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const report = ref<ReportDetail | null>(null)
const loading = ref(true)
const failed = ref(false)

const activeImage = ref(0)

/** 已结束的启事不再接受新的认领申请。 */
const CLOSED_STATUSES: ReportDetail['status'][] = ['RETURNED', 'HANDED_OVER', 'OFFLINE']

/** 当前用户是否为该启事的发布者（前端据此隐藏申请入口）。 */
const isPublisher = computed(() => {
  const userId = auth.user?.id
  const publisherId = report.value?.publisher?.id
  return userId != null && publisherId != null && userId === publisherId
})

/** 启事是否仍在可申请状态。 */
const canClaim = computed(
  () => report.value !== null && !CLOSED_STATUSES.includes(report.value.status)
)

const claimOpen = ref(false)
const claimForm = reactive({ featureDesc: '', expectTime: '', expectPlace: '' })
const claimErrors = ref<Record<string, string>>({})
const claimSubmitting = ref(false)
const claimError = ref('')
const claimSuccess = ref('')

/** 举报理由选项：与后端 FlagReason 枚举一致。 */
const REPORT_REASONS: { value: FlagReason; label: string }[] = [
  { value: 'FAKE', label: '虚假信息' },
  { value: 'AD', label: '广告' },
  { value: 'ABUSE', label: '辱骂' },
  { value: 'OTHER', label: '其他' }
]

const flagOpen = ref(false)
const flagReason = ref<FlagReason>('FAKE')
const flagDetail = ref('')
const flagSubmitting = ref(false)
const flagError = ref('')
const flagSuccess = ref('')

/** 打开举报理由弹窗。 */
function openFlag(): void {
  flagOpen.value = true
  flagReason.value = 'FAKE'
  flagDetail.value = ''
  flagError.value = ''
}

function closeFlag(): void {
  flagOpen.value = false
  flagReason.value = 'FAKE'
  flagDetail.value = ''
  flagError.value = ''
}

/** 提交举报：成功后关闭弹窗并提示。 */
async function submitFlag(): Promise<void> {
  flagSubmitting.value = true
  flagError.value = ''
  try {
    await createReportFlag(String(route.params.id), {
      reason: flagReason.value,
      detail: flagDetail.value.trim() === '' ? undefined : flagDetail.value.trim()
    })
    closeFlag()
    flagSuccess.value = '举报已提交'
  } catch (error) {
    flagError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试'
  } finally {
    flagSubmitting.value = false
  }
}

const contactTypeLabels: Record<string, string> = {
  phone: '手机号',
  wechat: '微信号',
  email: '邮箱'
}

const contactTypeLabel = computed(() => {
  const type = report.value?.publisher?.contactType
  if (!type) return '联系方式'
  return contactTypeLabels[type] ?? type
})

function selectImage(index: number): void {
  activeImage.value = index
}

function prevImage(): void {
  const count = report.value?.images.length ?? 0
  if (count === 0) return
  activeImage.value = (activeImage.value - 1 + count) % count
}

function nextImage(): void {
  const count = report.value?.images.length ?? 0
  if (count === 0) return
  activeImage.value = (activeImage.value + 1) % count
}

function toggleClaimForm(): void {
  claimOpen.value = !claimOpen.value
  claimError.value = ''
}

/** 未登录时引导去登录，并带上回跳路径回到本页。 */
function goLogin(): void {
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function validateClaim(): boolean {
  const next: Record<string, string> = {}
  if (claimForm.featureDesc.trim() === '') {
    next.featureDesc = '请填写独有特征说明'
  }
  claimErrors.value = next
  return Object.keys(next).length === 0
}

async function submitClaim(): Promise<void> {
  claimError.value = ''
  claimSuccess.value = ''
  if (!validateClaim()) {
    return
  }

  claimSubmitting.value = true
  try {
    await createClaim(String(route.params.id), {
      featureDesc: claimForm.featureDesc.trim(),
      expectTime: claimForm.expectTime === '' ? undefined : claimForm.expectTime,
      expectPlace: claimForm.expectPlace.trim() === '' ? undefined : claimForm.expectPlace.trim()
    })
    claimOpen.value = false
    claimForm.featureDesc = ''
    claimForm.expectTime = ''
    claimForm.expectPlace = ''
    claimSuccess.value = '认领申请已提交，等待发布者处理'
  } catch (error) {
    claimError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试'
  } finally {
    claimSubmitting.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    report.value = await getReport(String(route.params.id))
    activeImage.value = 0
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="report-detail">
    <LoadingSkeleton v-if="loading" variant="detail" :rows="3" />
    <ErrorState
      v-else-if="failed"
      message="启事不存在或加载失败，请稍后重试。"
      @retry="load"
    />

    <article v-else-if="report" class="report-detail__body">
      <header class="report-detail__header">
        <h1 class="report-detail__title">
          <span class="badge" :class="report.type === 'LOST' ? 'badge--lost' : 'badge--found'">
            {{ report.type === 'LOST' ? '失物' : '招领' }}
          </span>
          {{ report.title }}
        </h1>
        <p class="report-detail__meta">
          {{ report.location }}
          <template v-if="report.locationDetail">（{{ report.locationDetail }}）</template>
          · {{ report.category }}
        </p>
        <p class="report-detail__meta">
          <StatusBadge :status="report.status" />
          <span v-if="report.pinned" class="badge badge--pinned">置顶</span>
          <span class="report-detail__views">浏览量：{{ report.viewCount }}</span>
        </p>
      </header>

      <div v-if="report.images.length > 0" class="gallery">
        <div class="gallery__main">
          <button
            v-if="report.images.length > 1"
            class="gallery__nav"
            type="button"
            @click="prevImage"
          >
            上一张
          </button>
          <img
            class="gallery__img"
            :src="report.images[activeImage]?.path"
            :alt="report.images[activeImage]?.originalName ?? report.title"
          />
          <button
            v-if="report.images.length > 1"
            class="gallery__nav"
            type="button"
            @click="nextImage"
          >
            下一张
          </button>
        </div>
        <ul v-if="report.images.length > 1" class="gallery__thumbs">
          <li v-for="(image, index) in report.images" :key="image.id">
            <button
              class="gallery__thumb"
              :class="{ 'gallery__thumb--active': index === activeImage }"
              type="button"
              @click="selectImage(index)"
            >
              <img :src="image.path" :alt="image.originalName" />
            </button>
          </li>
        </ul>
      </div>

      <dl class="report-detail__facts">
        <dt>{{ report.type === 'LOST' ? '丢失时间' : '拾获时间' }}</dt>
        <dd>{{ report.eventTime ?? '未填写' }}</dd>
        <template v-if="report.type === 'FOUND'">
          <dt>保管方式</dt>
          <dd>{{ report.custody === 'AT_SECURITY' ? '已交保卫处' : '自留' }}</dd>
          <template v-if="report.handoverNo">
            <dt>移交单号</dt>
            <dd>{{ report.handoverNo }}</dd>
          </template>
        </template>
      </dl>

      <p v-if="report.description" class="report-detail__description">{{ report.description }}</p>

      <section class="publisher">
        <h2 class="publisher__title">发布者</h2>
        <p class="publisher__name">{{ report.publisher?.nickname ?? '匿名用户' }}</p>
        <template v-if="report.publisher">
          <p v-if="report.publisher.contactPublic && report.publisher.contactValue" class="publisher__contact">
            {{ contactTypeLabel }}：{{ report.publisher.contactValue }}
          </p>
          <p v-else class="publisher__contact publisher__contact--hint">通过站内会话联系</p>
        </template>
      </section>

      <section class="flag">
        <h2 class="flag__title">举报</h2>
        <p v-if="flagSuccess" class="flag__success">{{ flagSuccess }}</p>
        <button v-if="auth.isAuthenticated" class="flag__open" type="button" @click="openFlag">
          举报该启事
        </button>
        <p v-else class="flag__hint">
          <button class="flag__login" type="button" @click="goLogin">登录后举报</button>
        </p>
      </section>

      <section class="claim">
        <h2 class="claim__title">申请认领</h2>

        <p v-if="!canClaim" class="claim__hint">该启事已结束，无法发起认领申请。</p>
        <p v-else-if="!auth.isAuthenticated" class="claim__hint">
          <button class="claim__login" type="button" @click="goLogin">登录后申请认领</button>
        </p>
        <p v-else-if="isPublisher" class="claim__hint">这是你自己发布的启事，无需申请认领。</p>

        <template v-else>
          <p v-if="claimSuccess" class="claim__success">{{ claimSuccess }}</p>
          <button v-if="!claimOpen" class="claim__open" type="button" @click="toggleClaimForm">
            申请认领
          </button>

          <form v-if="claimOpen" class="claim-form" novalidate @submit.prevent="submitClaim">
            <label class="field">
              <span class="field__label">独有特征说明</span>
              <textarea
                v-model="claimForm.featureDesc"
                class="claim-form__feature"
                rows="3"
                placeholder="请描述只有你才知道的物品细节，便于发布者核对"
              ></textarea>
              <span v-if="claimErrors.featureDesc" class="field__error">{{ claimErrors.featureDesc }}</span>
            </label>
            <label class="field">
              <span class="field__label">期望交还时间（可选）</span>
              <input v-model="claimForm.expectTime" class="claim-form__time" type="datetime-local" />
            </label>
            <label class="field">
              <span class="field__label">期望交还地点（可选）</span>
              <input v-model="claimForm.expectPlace" class="claim-form__place" type="text" />
            </label>

            <p v-if="claimError" class="claim-form__error">{{ claimError }}</p>

            <div class="claim-form__actions">
              <button class="claim-form__submit" type="submit" :disabled="claimSubmitting">
                {{ claimSubmitting ? '提交中…' : '提交申请' }}
              </button>
              <button class="claim-form__cancel" type="button" @click="toggleClaimForm">取消</button>
            </div>
          </form>
        </template>
      </section>

      <div v-if="flagOpen" class="flag-modal" role="dialog">
        <div class="flag-modal__box">
          <h2 class="flag-modal__title">举报启事</h2>
          <fieldset class="flag-modal__reasons">
            <legend class="flag-modal__legend">举报理由</legend>
            <label
              v-for="option in REPORT_REASONS"
              :key="option.value"
              class="flag-modal__option"
            >
              <input
                v-model="flagReason"
                type="radio"
                name="flag-reason"
                :value="option.value"
                :class="`flag-modal__reason-${option.value}`"
              />
              {{ option.label }}
            </label>
          </fieldset>
          <textarea
            v-model="flagDetail"
            class="flag-modal__detail"
            rows="2"
            placeholder="补充说明（可选）"
          ></textarea>
          <p v-if="flagError" class="flag-modal__error">{{ flagError }}</p>
          <div class="flag-modal__actions">
            <button
              class="flag-modal__submit"
              type="button"
              :disabled="flagSubmitting"
              @click="submitFlag"
            >
              {{ flagSubmitting ? '提交中…' : '提交举报' }}
            </button>
            <button class="flag-modal__cancel" type="button" @click="closeFlag">取消</button>
          </div>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.state {
  color: #666;
}

.state--error {
  color: #c0392b;
}

/* 窄屏：详情大图按容器自适应，事实列表改单列不横向溢出。 */
@media (max-width: 768px) {
  .gallery__img {
    width: 100%;
    max-width: 100%;
    height: auto;
    max-height: none;
  }

  .report-detail__facts {
    grid-template-columns: 1fr;
  }

  .report-detail__facts dt {
    margin-top: 6px;
  }
}

.report-detail__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.report-detail__meta {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}

.report-detail__views {
  margin-left: 8px;
  color: #888;
}

.gallery {
  margin: 16px 0;
}

.gallery__main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gallery__img {
  flex: 1 1 auto;
  max-width: 520px;
  max-height: 420px;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid #e4e6eb;
  background: #f7f8fa;
}

.gallery__nav {
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.gallery__thumbs {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.gallery__thumb {
  padding: 0;
  border: 2px solid transparent;
  border-radius: 6px;
  background: none;
  cursor: pointer;
}

.gallery__thumb--active {
  border-color: #2e7d32;
}

.gallery__thumb img {
  display: block;
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 4px;
}

.report-detail__facts {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 4px 12px;
  margin: 16px 0;
  font-size: 14px;
}

.report-detail__facts dt {
  color: #888;
}

.report-detail__facts dd {
  margin: 0;
}

.report-detail__description {
  white-space: pre-wrap;
  line-height: 1.6;
}

.publisher {
  margin-top: 20px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.publisher__title {
  margin: 0 0 8px;
  font-size: 15px;
}

.publisher__name {
  margin: 0 0 4px;
  font-size: 14px;
}

.publisher__contact {
  margin: 0;
  font-size: 13px;
  color: #2e7d32;
}

.publisher__contact--hint {
  color: #888;
}

.claim {
  margin-top: 20px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.claim__title {
  margin: 0 0 8px;
  font-size: 15px;
}

.claim__hint {
  margin: 0;
  color: #888;
  font-size: 13px;
}

.claim__login,
.claim__open {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.claim__success {
  margin: 0 0 8px;
  color: #2e7d32;
  font-size: 13px;
}

.claim-form {
  display: grid;
  gap: 10px;
  margin-top: 8px;
}

.field {
  display: grid;
  gap: 4px;
}

.field__label {
  font-size: 12px;
  color: #444;
}

.field__error {
  color: #c0392b;
  font-size: 12px;
}

.claim-form__feature,
.claim-form__time,
.claim-form__place {
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.claim-form__error {
  margin: 0;
  color: #c0392b;
  font-size: 13px;
}

.claim-form__actions {
  display: flex;
  gap: 8px;
}

.claim-form__submit {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.claim-form__cancel {
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

.badge--pinned {
  background: #fff4e5;
  color: #b26a00;
  margin-left: 8px;
}

.flag {
  margin-top: 20px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.flag__title {
  margin: 0 0 8px;
  font-size: 15px;
}

.flag__success {
  margin: 0 0 8px;
  color: #2e7d32;
  font-size: 13px;
}

.flag__open,
.flag__login {
  padding: 6px 14px;
  border: 1px solid #c0392b;
  border-radius: 6px;
  background: #fff;
  color: #c0392b;
  font-size: 13px;
  cursor: pointer;
}

.flag__hint {
  margin: 0;
}

.flag-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.flag-modal__box {
  width: 340px;
  padding: 16px;
  border-radius: 8px;
  background: #fff;
}

.flag-modal__title {
  margin: 0 0 10px;
  font-size: 16px;
}

.flag-modal__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 0 12px;
  padding: 8px 10px;
  border: 1px solid #e4e6eb;
  border-radius: 6px;
}

.flag-modal__legend {
  padding: 0 4px;
  color: #444;
  font-size: 12px;
}

.flag-modal__option {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.flag-modal__detail {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.flag-modal__error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 13px;
}

.flag-modal__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.flag-modal__submit {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #c0392b;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.flag-modal__cancel {
  padding: 6px 14px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>
