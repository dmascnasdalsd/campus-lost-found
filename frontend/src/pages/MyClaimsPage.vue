<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { listClaims, withdrawClaim } from '@/api/claims'
import type { ClaimRole } from '@/api/claims'
import type { ClaimStatus, ClaimView } from '@/api/types'

const tab = ref<ClaimRole>('applied')
const claims = ref<ClaimView[]>([])
const loading = ref(true)
const failed = ref(false)
const actionError = ref('')

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

        <p v-if="claim.status === 'REJECTED' && claim.rejectReason" class="claim-card__reject">
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
      </li>
    </ul>
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

.claim-card__reject {
  margin: 6px 0;
  color: #c0392b;
  font-size: 13px;
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
