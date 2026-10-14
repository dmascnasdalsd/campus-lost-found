<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { LocationQueryRaw } from 'vue-router'
import { listReports } from '@/api/reports'
import type { ReportSort, ReportTimeRange } from '@/api/reports'
import type { ReportSummary, ReportType } from '@/api/types'
import { REPORT_CATEGORIES, REPORT_LOCATIONS } from '@/constants/vocabulary'
import ReportCard from '@/components/ReportCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const PAGE_SIZE = 10
const TIME_RANGES: ReportTimeRange[] = ['today', '3d', '7d', '30d', 'custom']

const route = useRoute()
const router = useRouter()

// 各筛选控件均以字符串 / 布尔保存，真正调用 API 时再做一次收窄。
const filters = reactive({
  type: '',
  category: '',
  location: '',
  timeRange: '',
  startDate: '',
  endDate: '',
  keyword: '',
  sort: 'latest',
  includeClosed: false,
  page: 0
})

const reports = ref<ReportSummary[]>([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)

const totalPages = computed(() => Math.max(Math.ceil(total.value / PAGE_SIZE), 1))

/** 从 URL query 初始化筛选状态：刷新与分享后可保持。 */
function syncFromQuery(): void {
  const query = route.query
  const text = (key: string): string => (typeof query[key] === 'string' ? (query[key] as string) : '')
  const type = text('type')
  filters.type = type === 'LOST' || type === 'FOUND' ? type : ''
  filters.category = text('category')
  filters.location = text('location')
  const timeRange = text('timeRange')
  filters.timeRange = TIME_RANGES.includes(timeRange as ReportTimeRange) ? timeRange : ''
  filters.startDate = text('startDate')
  filters.endDate = text('endDate')
  filters.keyword = text('keyword')
  filters.sort = text('sort') === 'eventTime' ? 'eventTime' : 'latest'
  filters.includeClosed = text('includeClosed') === 'true'
  const page = Number(text('page'))
  filters.page = Number.isInteger(page) && page > 0 ? page : 0
}

/** 由当前筛选状态构造 URL query，只写入非默认值，保持链接整洁。 */
function buildQuery(): LocationQueryRaw {
  const query: Record<string, string> = {}
  if (filters.type) query.type = filters.type
  if (filters.category) query.category = filters.category
  if (filters.location) query.location = filters.location
  if (filters.timeRange) query.timeRange = filters.timeRange
  if (filters.timeRange === 'custom') {
    if (filters.startDate) query.startDate = filters.startDate
    if (filters.endDate) query.endDate = filters.endDate
  }
  const keyword = filters.keyword.trim()
  if (keyword) query.keyword = keyword
  if (filters.sort === 'eventTime') query.sort = 'eventTime'
  if (filters.includeClosed) query.includeClosed = 'true'
  if (filters.page > 0) query.page = String(filters.page)
  return query
}

/** 将筛选条件同步到 URL query（replace，避免污染浏览器历史）。 */
function applyFilters(): void {
  void router.replace({ query: buildQuery() })
}

function setPage(page: number): void {
  const next = Math.min(Math.max(page, 0), totalPages.value - 1)
  if (next === filters.page) return
  filters.page = next
  applyFilters()
}

function toIso(date: string, endOfDay: boolean): string | undefined {
  if (date === '') return undefined
  return `${date}T${endOfDay ? '23:59:59' : '00:00:00'}`
}

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const page = await listReports({
      type: filters.type === 'LOST' || filters.type === 'FOUND' ? (filters.type as ReportType) : undefined,
      category: filters.category || undefined,
      location: filters.location || undefined,
      timeRange: TIME_RANGES.includes(filters.timeRange as ReportTimeRange)
        ? (filters.timeRange as ReportTimeRange)
        : undefined,
      startTime: filters.timeRange === 'custom' ? toIso(filters.startDate, false) : undefined,
      endTime: filters.timeRange === 'custom' ? toIso(filters.endDate, true) : undefined,
      keyword: filters.keyword.trim() || undefined,
      sort: (filters.sort === 'eventTime' ? 'eventTime' : 'latest') as ReportSort,
      page: filters.page,
      size: PAGE_SIZE,
      includeClosed: filters.includeClosed
    })
    reports.value = page.items
    total.value = page.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

// URL query 是筛选条件的唯一真相：进入页面与 query 变化（含浏览器前进/后退）时都重新加载。
watch(
  () => route.query,
  () => {
    syncFromQuery()
    void load()
  },
  { immediate: true, deep: true }
)
</script>

<template>
  <section class="home">
    <header class="home__header">
      <h1 class="home__title">校园失物招领</h1>
      <p class="home__subtitle">丢了东西、捡到东西，都先来这里看一眼。</p>
    </header>

    <form class="filters" @submit.prevent="applyFilters">
      <label class="filters__field">
        <span class="filters__label">类型</span>
        <select v-model="filters.type" class="filter-type" @change="applyFilters">
          <option value="">全部</option>
          <option value="LOST">失物</option>
          <option value="FOUND">招领</option>
        </select>
      </label>

      <label class="filters__field">
        <span class="filters__label">分类</span>
        <select v-model="filters.category" class="filter-category" @change="applyFilters">
          <option value="">全部分类</option>
          <option v-for="item in REPORT_CATEGORIES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>

      <label class="filters__field">
        <span class="filters__label">地点</span>
        <select v-model="filters.location" class="filter-location" @change="applyFilters">
          <option value="">全部地点</option>
          <option v-for="item in REPORT_LOCATIONS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>

      <label class="filters__field">
        <span class="filters__label">时间范围</span>
        <select v-model="filters.timeRange" class="filter-timerange" @change="applyFilters">
          <option value="">全部时间</option>
          <option value="today">今天</option>
          <option value="3d">最近 3 天</option>
          <option value="7d">最近 7 天</option>
          <option value="30d">最近 30 天</option>
          <option value="custom">自定义</option>
        </select>
      </label>

      <template v-if="filters.timeRange === 'custom'">
        <label class="filters__field">
          <span class="filters__label">起始日期</span>
          <input v-model="filters.startDate" class="filter-start" type="date" @change="applyFilters" />
        </label>
        <label class="filters__field">
          <span class="filters__label">结束日期</span>
          <input v-model="filters.endDate" class="filter-end" type="date" @change="applyFilters" />
        </label>
      </template>

      <label class="filters__field filters__field--grow">
        <span class="filters__label">关键词</span>
        <input
          v-model="filters.keyword"
          class="filter-keyword"
          type="search"
          placeholder="搜索物品名称或描述，如 AirPods"
          @change="applyFilters"
        />
      </label>

      <label class="filters__field">
        <span class="filters__label">排序</span>
        <select v-model="filters.sort" class="filter-sort" @change="applyFilters">
          <option value="latest">最新发布</option>
          <option value="eventTime">最近事件时间</option>
        </select>
      </label>

      <label class="filters__checkbox">
        <input v-model="filters.includeClosed" class="filter-include-closed" type="checkbox" @change="applyFilters" />
        <span>含已结束</span>
      </label>

      <button class="filters__submit" type="submit">搜索</button>
    </form>

    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="failed" class="state state--error">加载失败，请稍后重试。</p>
    <EmptyState v-else-if="reports.length === 0" />
    <template v-else>
      <ul class="report-list">
        <li v-for="report in reports" :key="report.id">
          <ReportCard :report="report" />
        </li>
      </ul>
      <nav class="pager">
        <button
          class="pager__prev"
          type="button"
          :disabled="filters.page === 0"
          @click="setPage(filters.page - 1)"
        >
          上一页
        </button>
        <span class="pager__info">第 {{ filters.page + 1 }} / {{ totalPages }} 页</span>
        <button
          class="pager__next"
          type="button"
          :disabled="filters.page >= totalPages - 1"
          @click="setPage(filters.page + 1)"
        >
          下一页
        </button>
      </nav>
    </template>
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

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.filters__field {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #666;
}

.filters__field--grow {
  flex: 1 1 200px;
}

.filters__label {
  font-size: 12px;
}

.filters select,
.filters input[type='date'],
.filters input[type='search'] {
  padding: 6px 8px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.filters__checkbox {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #444;
}

.filters__submit {
  padding: 7px 16px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
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

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  font-size: 13px;
  color: #666;
}

.pager button {
  padding: 6px 12px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
