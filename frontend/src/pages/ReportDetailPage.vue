<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getReport } from '@/api/reports'
import type { ReportDetail } from '@/api/types'
import StatusBadge from '@/components/StatusBadge.vue'

const route = useRoute()
const report = ref<ReportDetail | null>(null)
const loading = ref(true)
const failed = ref(false)

const activeImage = ref(0)

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
</style>
