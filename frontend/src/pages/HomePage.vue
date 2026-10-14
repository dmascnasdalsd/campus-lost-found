<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listReports } from '@/api/reports'
import type { ReportSummary } from '@/api/types'
import ReportCard from '@/components/ReportCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const reports = ref<ReportSummary[]>([])
const loading = ref(true)
const failed = ref(false)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const page = await listReports({ size: 20 })
    reports.value = page.items
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="home">
    <header class="home__header">
      <h1 class="home__title">校园失物招领</h1>
      <p class="home__subtitle">丢了东西、捡到东西，都先来这里看一眼。</p>
    </header>

    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="failed" class="state state--error">加载失败，请稍后重试。</p>
    <EmptyState v-else-if="reports.length === 0" />
    <ul v-else class="report-list">
      <li v-for="report in reports" :key="report.id">
        <ReportCard :report="report" />
      </li>
    </ul>
  </section>
</template>

<style scoped>
.home__header {
  margin-bottom: 24px;
}

.home__title {
  margin: 0 0 8px;
  font-size: 24px;
}

.home__subtitle {
  margin: 0;
  color: #666;
}

.state {
  color: #666;
}

.state--error {
  color: #c0392b;
}

.report-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
</style>
