<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { exportReportsCsv, getAdminStats } from '@/api/admin'
import type { AdminStatsView, NameCountView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

/** 统计窗口：周（最近 7 天）或月（最近 30 天）。 */
type Range = 'week' | 'month'

const range = ref<Range>('week')
const stats = ref<AdminStatsView | null>(null)
const loading = ref(true)
const failed = ref(false)
const exporting = ref(false)

const hasData = computed(() => (stats.value?.totalReports ?? 0) > 0)

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    stats.value = await getAdminStats(range.value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function switchRange(next: Range): void {
  if (range.value === next) return
  range.value = next
  void load()
}

/** 导出 CSV：取回文件流后经临时链接触发下载。 */
async function onExport(): Promise<void> {
  exporting.value = true
  try {
    const blob = await exportReportsCsv()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'lostfound-export.csv'
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

/** 某分布列表中的最大计数，用于计算条形相对宽度。 */
function maxCount(items: NameCountView[]): number {
  return items.reduce((max, item) => Math.max(max, item.count), 0)
}

/** 条形宽度：相对最大计数的百分比。 */
function barWidth(count: number, max: number): string {
  return max > 0 ? `${Math.round((count / max) * 100)}%` : '0%'
}
</script>

<template>
  <section class="admin-stats">
    <header class="admin-stats__header">
      <h1 class="admin-stats__title">统计看板</h1>
      <p class="admin-stats__subtitle">每日新增与归还趋势、地点 / 分类分布与高发时段。</p>
    </header>

    <div class="filters">
      <button
        class="filter range-week"
        :class="{ 'filter--active': range === 'week' }"
        type="button"
        @click="switchRange('week')"
      >
        本周
      </button>
      <button
        class="filter range-month"
        :class="{ 'filter--active': range === 'month' }"
        type="button"
        @click="switchRange('month')"
      >
        本月
      </button>
      <button class="stats-export" type="button" :disabled="exporting" @click="onExport">
        {{ exporting ? '导出中…' : '导出 CSV' }}
      </button>
    </div>

    <LoadingSkeleton v-if="loading" variant="detail" :rows="3" />
    <ErrorState v-else-if="failed" @retry="load" />
    <template v-else-if="stats">
      <EmptyState v-if="!hasData" title="暂无统计数据" hint="当前时间范围内还没有启事数据。" />
      <template v-else>
        <div class="stat-cards">
          <div class="stat-card stat-card--total">
            <span class="stat-card__label">新增启事</span>
            <span class="stat-card__value stats-total">{{ stats.totalReports }}</span>
          </div>
          <div class="stat-card stat-card--returned">
            <span class="stat-card__label">归还成功</span>
            <span class="stat-card__value stats-returned">{{ stats.returnedReports }}</span>
          </div>
          <div class="stat-card stat-card--rate">
            <span class="stat-card__label">归还率</span>
            <span class="stat-card__value stats-rate">{{ stats.returnRate }}%</span>
          </div>
        </div>

        <section class="stats-section">
          <h2 class="stats-section__title">每日新增与归还</h2>
          <ul class="daily-list">
            <li v-for="day in stats.daily" :key="day.date" class="daily-item">
              <span class="daily-item__date">{{ day.date }}</span>
              <span class="daily-item__created">新增 {{ day.created }}</span>
              <span class="daily-item__returned">归还 {{ day.returned }}</span>
            </li>
          </ul>
        </section>

        <section class="stats-section">
          <h2 class="stats-section__title">按地点分布</h2>
          <ul class="distribution">
            <li v-for="item in stats.byLocation" :key="item.name" class="distribution__item">
              <span class="distribution__name">{{ item.name }}</span>
              <span class="distribution__bar">
                <span
                  class="distribution__bar-fill"
                  :style="{ width: barWidth(item.count, maxCount(stats.byLocation)) }"
                ></span>
              </span>
              <span class="distribution__count">{{ item.count }}</span>
            </li>
          </ul>
        </section>

        <section class="stats-section">
          <h2 class="stats-section__title">按分类分布</h2>
          <ul class="distribution">
            <li v-for="item in stats.byCategory" :key="item.name" class="distribution__item">
              <span class="distribution__name">{{ item.name }}</span>
              <span class="distribution__bar">
                <span
                  class="distribution__bar-fill"
                  :style="{ width: barWidth(item.count, maxCount(stats.byCategory)) }"
                ></span>
              </span>
              <span class="distribution__count">{{ item.count }}</span>
            </li>
          </ul>
        </section>

        <section class="stats-section">
          <h2 class="stats-section__title">高发时段</h2>
          <ul class="distribution">
            <li v-for="item in stats.byHour" :key="item.name" class="distribution__item">
              <span class="distribution__name">{{ item.name }}</span>
              <span class="distribution__bar">
                <span
                  class="distribution__bar-fill"
                  :style="{ width: barWidth(item.count, maxCount(stats.byHour)) }"
                ></span>
              </span>
              <span class="distribution__count">{{ item.count }}</span>
            </li>
          </ul>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.admin-stats__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.admin-stats__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}

.filters {
  display: flex;
  align-items: center;
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

.stats-export {
  margin-left: auto;
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.stats-export:disabled {
  opacity: 0.6;
  cursor: default;
}

.admin-stats__empty {
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.stat-card__label {
  font-size: 13px;
  color: #666;
}

.stat-card__value {
  font-size: 26px;
  font-weight: 600;
  color: #1a1a1a;
}

.stat-card--returned .stat-card__value {
  color: #2e7d32;
}

.stats-section {
  margin-bottom: 20px;
}

.stats-section__title {
  margin: 0 0 10px;
  font-size: 16px;
}

.daily-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.daily-item {
  display: flex;
  gap: 16px;
  padding: 6px 10px;
  border: 1px solid #e4e6eb;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
}

.daily-item__date {
  color: #444;
  min-width: 96px;
}

.daily-item__created {
  color: #1c5fb0;
}

.daily-item__returned {
  color: #2e7d32;
}

.distribution {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.distribution__item {
  display: grid;
  grid-template-columns: 96px 1fr 48px;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.distribution__name {
  color: #444;
}

.distribution__bar {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: #eef1f5;
  overflow: hidden;
}

.distribution__bar-fill {
  display: block;
  height: 100%;
  border-radius: 6px;
  background: #2e7d32;
}

.distribution__count {
  text-align: right;
  color: #1a1a1a;
}

/* 窄屏：卡片与分布改单列，避免横向溢出。 */
@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }

  .distribution__item {
    grid-template-columns: 1fr;
  }

  .daily-item {
    flex-wrap: wrap;
    gap: 8px;
  }

  .daily-item__date {
    min-width: 0;
  }
}
</style>
