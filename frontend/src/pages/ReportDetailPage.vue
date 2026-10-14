<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getReport } from '@/api/reports'
import type { ReportDetail } from '@/api/types'

const route = useRoute()
const report = ref<ReportDetail | null>(null)
const loading = ref(true)
const failed = ref(false)

const statusLabels: Record<string, string> = {
  OPEN: '招领中',
  PENDING_REVIEW: '待复核',
  CLAIMING: '认领中',
  RETURNED: '已归还',
  HANDED_OVER: '已移交',
  OFFLINE: '已下架',
  REJECTED: '已驳回'
}

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    report.value = await getReport(String(route.params.id))
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
    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="failed" class="state state--error">启事不存在或加载失败。</p>

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
          状态：{{ statusLabels[report.status] ?? report.status }}
          <span v-if="report.pinned" class="badge badge--pinned">置顶</span>
        </p>
      </header>

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
        <dt>发布者</dt>
        <dd>{{ report.publisher?.nickname ?? '匿名用户' }}</dd>
      </dl>

      <p v-if="report.description" class="report-detail__description">{{ report.description }}</p>

      <ul v-if="report.images.length > 0" class="image-list">
        <li v-for="image in report.images" :key="image.id">
          <img class="image-list__img" :src="image.path" :alt="image.originalName" />
        </li>
      </ul>
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

.report-detail__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.report-detail__meta {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
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

.image-list {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-list__img {
  width: 180px;
  height: 180px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e4e6eb;
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
