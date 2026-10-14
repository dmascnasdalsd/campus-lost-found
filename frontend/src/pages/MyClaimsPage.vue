<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { listClaims, withdrawClaim, acceptClaim, rejectClaim } from '@/api/claims'
import type { ClaimRole } from '@/api/claims'
import type { ClaimStatus, ClaimView } from '@/api/types'

const tab = ref<ClaimRole>('applied')
const claims = ref<ClaimView[]>([])
const loading = ref(true)
const failed = ref(false)
const actionError = ref('')

// 驳回弹窗状态：目标申请、原因输入、行内校验错误与提交中标记。
const rejectingClaim = ref<ClaimView | null>(null)
const rejectReason = ref('')
const rejectError = ref('')
const rejecting = ref(false)

const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已接受',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CLOSED: '已关闭'
}

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    claims.value = await listClaims(tab.value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function switchTab(next: ClaimRole): void {
  if (tab.value === next) return
  tab.value = next
  actionError.value = ''
  void load()
}

async function onWithdraw(claim: ClaimView): Promise<void> {
  if (!window.confirm('确认撤回这条认领申请？撤回后可重新申请。')) {
    return
  }
  actionError.value = ''
  try {
    await withdrawClaim(claim.id)
    await load()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '撤回失败，请稍后重试'
  }
}

/** 接受认领申请：确认后调用接受接口并重新加载（同启事其他待处理申请会被关闭）。 */
async function onAccept(claim: ClaimView): Promise<void> {
  if (!window.confirm('确认接受这条认领申请？接受后同一启事的其他待处理申请会被自动关闭。')) {
    return
  }
  actionError.value = ''
  try {
    await acceptClaim(claim.id)
    await load()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '接受失败，请稍后重试'
  }
}

/** 打开驳回弹窗，重置原因与错误。 */
function openReject(claim: ClaimView): void {
  rejectingClaim.value = claim
  rejectReason.value = ''
  rejectError.value = ''
}

/** 关闭驳回弹窗。 */
function closeReject(): void {
  rejectingClaim.value = null
  rejectReason.value = ''
  rejectError.value = ''
}

/** 提交驳回：原因必填，成功后关闭弹窗并重新加载。 */
async function submitReject(): Promise<void> {
  if (!rejectingClaim.value) {
    return
  }
  if (rejectReason.value.trim() === '') {
    rejectError.value = '请填写驳回原因'
    return
  }
  rejecting.value = true
  rejectError.value = ''
  try {
    await rejectClaim(rejectingClaim.value.id, { rejectReason: rejectReason.value.trim() })
    closeReject()
    await load()
  } catch (error) {
    rejectError.value = error instanceof ApiError ? error.message : '驳回失败，请稍后重试'
  } finally {
    rejecting.value = false
  }
}
</script>

<template>
  <section class="my-claims">
    <header class="my-claims__header">
      <h1 class="my-claims__title">我的认领</h1>
      <p class="my-claims__subtitle">查看自己发起与被他人发起的认领申请。</p>
    </header>

    <div class="tabs">
      <button
        class="tab tab-applied"
        :class="{ 'tab--active': tab === 'applied' }"
        type="button"
        @click="switchTab('applied')"
      >
        我发起的
      </button>
      <button
        class="tab tab-received"
        :class="{ 'tab--active': tab === 'received' }"
        type="button"
        @click="switchTab('received')"
      >
        我收到的
      </button>
    </div>

    <p v-if="actionError" class="my-claims__error">{{ actionError }}</p>

    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="failed" class="state state--error">加载失败，请稍后重试。</p>
    <p v-else-if="claims.length === 0" class="my-claims__empty">暂无认领申请</p>
    <ul v-else class="claim-list">
      <li v-for="claim in claims" :key="claim.id" class="claim-card">
        <div class="claim-card__head">
          <RouterLink
            v-if="claim.report"
            class="claim-card__title"
            :to="`/reports/${claim.report.id}`"
          >
            {{ claim.report.title }}
          </RouterLink>
          <span v-else class="claim-card__title claim-card__title--missing">关联启事已删除</span>
          <span class="claim-badge" :class="`claim-badge--${claim.status.toLowerCase()}`">
            {{ CLAIM_STATUS_LABELS[claim.status] }}
          </span>
        </div>

        <p class="claim-card__meta">
          <span
            v-if="claim.report"
            class="badge"
            :class="claim.report.type === 'LOST' ? 'badge--lost' : 'badge--found'"
          >
            {{ claim.report.type === 'LOST' ? '失物' : '招领' }}
          </span>
          <span v-if="tab === 'received' && claim.applicantNickname" class="claim-card__applicant">
            申请人：{{ claim.applicantNickname }}
          </span>
        </p>

        <p class="claim-card__feature">{{ claim.featureDesc }}</p>

        <p class="claim-card__details">
          <span v-if="claim.expectTime">期望时间：{{ claim.expectTime }}</span>
          <span v-if="claim.expectPlace">期望地点：{{ claim.expectPlace }}</span>
        </p>

        <p v-if="claim.status === 'REJECTED' && claim.rejectReason" class="claim-card__reject-reason">
          驳回原因：{{ claim.rejectReason }}
        </p>

        <button
          v-if="tab === 'applied' && claim.status === 'PENDING'"
          class="claim-card__withdraw"
          type="button"
          @click="onWithdraw(claim)"
        >
          撤回
        </button>

        <div
          v-if="tab === 'received' && claim.status === 'PENDING'"
          class="claim-card__actions"
        >
          <button class="claim-card__accept" type="button" @click="onAccept(claim)">接受</button>
          <button class="claim-card__reject" type="button" @click="openReject(claim)">拒绝</button>
        </div>
      </li>
    </ul>

    <div v-if="rejectingClaim" class="reject-modal" role="dialog">
      <div class="reject-modal__box">
        <h2 class="reject-modal__title">驳回认领申请</h2>
        <textarea
          v-model="rejectReason"
          class="reject-modal__reason"
          rows="3"
          placeholder="请填写驳回原因（申请人可见）"
        ></textarea>
        <p v-if="rejectError" class="reject-modal__error">{{ rejectError }}</p>
        <div class="reject-modal__actions">
          <button
            class="reject-modal__submit"
            type="button"
            :disabled="rejecting"
            @click="submitReject"
          >
            {{ rejecting ? '提交中…' : '确认驳回' }}
          </button>
          <button class="reject-modal__cancel" type="button" @click="closeReject">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.my-claims__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.my-claims__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tab {
  padding: 6px 16px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.tab--active {
  border-color: #2e7d32;
  color: #2e7d32;
  font-weight: 600;
}

.my-claims__error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
}

.my-claims__empty {
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

.claim-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.claim-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.claim-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.claim-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  text-decoration: none;
}

.claim-card__title:hover {
  text-decoration: underline;
}

.claim-card__title--missing {
  color: #999;
  font-weight: 400;
}

.claim-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #eef1f5;
  color: #566272;
}

.claim-badge--pending {
  background: #fff4e5;
  color: #b26a00;
}

.claim-badge--accepted {
  background: #e8f4ea;
  color: #2e7d32;
}

.claim-badge--rejected {
  background: #fdecea;
  color: #c0392b;
}

.claim-badge--withdrawn,
.claim-badge--closed {
  background: #f2f3f5;
  color: #999;
}

.claim-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
  color: #666;
  font-size: 13px;
}

.claim-card__feature {
  margin: 6px 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.claim-card__details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 6px 0;
  color: #666;
  font-size: 13px;
}

.claim-card__reject-reason {
  margin: 6px 0;
  color: #c0392b;
  font-size: 13px;
}

.claim-card__actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.claim-card__accept,
.claim-card__reject {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.claim-card__accept {
  border: none;
  background: #2e7d32;
  color: #fff;
}

.claim-card__reject {
  border: 1px solid #c0392b;
  background: #fff;
  color: #c0392b;
}

.reject-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.reject-modal__box {
  width: 320px;
  padding: 16px;
  border-radius: 8px;
  background: #fff;
}

.reject-modal__title {
  margin: 0 0 10px;
  font-size: 16px;
}

.reject-modal__reason {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.reject-modal__error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 13px;
}

.reject-modal__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.reject-modal__submit {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.reject-modal__cancel {
  padding: 6px 14px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.claim-card__withdraw {
  margin-top: 6px;
  padding: 6px 12px;
  border: 1px solid #c0392b;
  border-radius: 6px;
  background: #fff;
  color: #c0392b;
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
