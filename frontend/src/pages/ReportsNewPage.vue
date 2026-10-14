<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { createReport, uploadImages } from '@/api/reports'
import type { Custody, ReportType } from '@/api/types'
import { REPORT_CATEGORIES, REPORT_LOCATIONS } from '@/constants/vocabulary'

interface SelectedImage {
  file: File
  url: string
}

const MAX_IMAGES = 3

const router = useRouter()

const type = ref<ReportType>('LOST')
const title = ref('')
const category = ref('')
const location = ref('')
const locationDetail = ref('')
const eventTime = ref('')
const description = ref('')
const custody = ref<Custody>('SELF')
const handoverNo = ref('')
const images = ref<SelectedImage[]>([])

const errors = ref<Record<string, string>>({})
const formError = ref('')
const submitting = ref(false)

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  for (const file of picked) {
    if (images.value.length >= MAX_IMAGES) {
      errors.value = { ...errors.value, images: `最多上传 ${MAX_IMAGES} 张图片` }
      break
    }
    images.value.push({ file, url: URL.createObjectURL(file) })
  }
  if (images.value.length > 0) {
    delete errors.value.images
  }
  input.value = ''
}

function moveUp(index: number): void {
  if (index <= 0) return
  const list = images.value
  ;[list[index - 1], list[index]] = [list[index], list[index - 1]]
}

function moveDown(index: number): void {
  const list = images.value
  if (index >= list.length - 1) return
  ;[list[index + 1], list[index]] = [list[index], list[index + 1]]
}

function removeImage(index: number): void {
  const [removed] = images.value.splice(index, 1)
  if (removed) {
    URL.revokeObjectURL(removed.url)
  }
}

function validate(): boolean {
  const next: Record<string, string> = {}
  if (title.value.trim() === '') {
    next.title = '请填写物品名称'
  }
  if (category.value === '') {
    next.category = '请选择分类'
  }
  if (location.value === '') {
    next.location = '请选择地点'
  }
  if (eventTime.value === '') {
    next.eventTime = '请选择事件时间'
  }
  if (type.value === 'FOUND' && custody.value === 'AT_SECURITY' && handoverNo.value.trim() === '') {
    next.handoverNo = '移交保卫处时必须填写移交单号'
  }
  if (images.value.length === 0) {
    next.images = '请至少选择 1 张图片'
  } else if (images.value.length > MAX_IMAGES) {
    next.images = `最多上传 ${MAX_IMAGES} 张图片`
  }
  errors.value = next
  return Object.keys(next).length === 0
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!validate()) {
    return
  }

  submitting.value = true
  try {
    const detail = await createReport({
      type: type.value,
      title: title.value.trim(),
      category: category.value,
      location: location.value,
      locationDetail: locationDetail.value.trim() === '' ? undefined : locationDetail.value.trim(),
      eventTime: eventTime.value,
      description: description.value.trim() === '' ? undefined : description.value.trim(),
      custody: type.value === 'FOUND' ? custody.value : undefined,
      handoverNo:
        type.value === 'FOUND' && custody.value === 'AT_SECURITY' ? handoverNo.value.trim() : undefined
    })
    if (images.value.length > 0) {
      await uploadImages(detail.id, images.value.map((item) => item.file))
    }
    await router.push(`/reports/${detail.id}`)
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '发布失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="report-new">
    <h1 class="report-new__title">发布启事</h1>

    <form class="report-form" novalidate @submit.prevent="onSubmit">
      <fieldset class="field">
        <legend class="field__label">启事类型</legend>
        <div class="radio-row">
          <label class="radio">
            <input v-model="type" type="radio" value="LOST" />
            <span>失物（我丢了东西）</span>
          </label>
          <label class="radio">
            <input v-model="type" type="radio" value="FOUND" />
            <span>招领（我捡到东西）</span>
          </label>
        </div>
      </fieldset>

      <label class="field">
        <span class="field__label">物品名称</span>
        <input v-model="title" class="field__input" type="text" placeholder="如：黑色校园卡、AirPods 耳机" />
        <span v-if="errors.title" class="field__error">{{ errors.title }}</span>
      </label>

      <label class="field">
        <span class="field__label">分类</span>
        <select v-model="category" class="field__input">
          <option value="" disabled>请选择分类</option>
          <option v-for="item in REPORT_CATEGORIES" :key="item" :value="item">{{ item }}</option>
        </select>
        <span v-if="errors.category" class="field__error">{{ errors.category }}</span>
      </label>

      <label class="field">
        <span class="field__label">地点</span>
        <select v-model="location" class="field__input">
          <option value="" disabled>请选择地点</option>
          <option v-for="item in REPORT_LOCATIONS" :key="item" :value="item">{{ item }}</option>
        </select>
        <span v-if="errors.location" class="field__error">{{ errors.location }}</span>
      </label>

      <label class="field">
        <span class="field__label">地点补充描述（可选）</span>
        <input v-model="locationDetail" class="field__input" type="text" placeholder="如：图书馆三楼自习区" />
      </label>

      <label class="field">
        <span class="field__label">{{ type === 'LOST' ? '丢失时间' : '拾获时间' }}</span>
        <input v-model="eventTime" class="field__input" type="datetime-local" />
        <span v-if="errors.eventTime" class="field__error">{{ errors.eventTime }}</span>
      </label>

      <label class="field">
        <span class="field__label">描述（可选）</span>
        <textarea
          v-model="description"
          class="field__input"
          rows="4"
          placeholder="补充物品特征，便于他人辨认"
        ></textarea>
      </label>

      <fieldset v-if="type === 'FOUND'" class="field">
        <legend class="field__label">保管方式</legend>
        <div class="radio-row">
          <label class="radio">
            <input v-model="custody" type="radio" value="SELF" />
            <span>自留（站内约定交还）</span>
          </label>
          <label class="radio">
            <input v-model="custody" type="radio" value="AT_SECURITY" />
            <span>已交保卫处</span>
          </label>
        </div>
      </fieldset>

      <label v-if="type === 'FOUND' && custody === 'AT_SECURITY'" class="field">
        <span class="field__label">移交单号</span>
        <input v-model="handoverNo" class="field__input" type="text" />
        <span v-if="errors.handoverNo" class="field__error">{{ errors.handoverNo }}</span>
      </label>

      <div class="field">
        <span class="field__label">图片（1～{{ MAX_IMAGES }} 张）</span>
        <input type="file" accept="image/*" multiple @change="onFileChange" />
        <span v-if="errors.images" class="field__error">{{ errors.images }}</span>

        <ul v-if="images.length > 0" class="preview-list">
          <li v-for="(item, index) in images" :key="item.url" class="preview-item">
            <img class="preview-item__img" :src="item.url" :alt="item.file.name" />
            <div class="preview-item__actions">
              <button type="button" :disabled="index === 0" @click="moveUp(index)">上移</button>
              <button type="button" :disabled="index === images.length - 1" @click="moveDown(index)">
                下移
              </button>
              <button type="button" @click="removeImage(index)">删除</button>
            </div>
          </li>
        </ul>
      </div>

      <p v-if="formError" class="report-form__error">{{ formError }}</p>

      <button class="report-form__submit" type="submit" :disabled="submitting">
        {{ submitting ? '发布中…' : '发布启事' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.report-new__title {
  margin: 0 0 16px;
  font-size: 22px;
}

.report-form {
  display: grid;
  gap: 14px;
  max-width: 560px;
}

.field {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  border: none;
}

.field__label {
  font-size: 13px;
  color: #444;
}

.field__input {
  padding: 8px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
}

.field__error {
  color: #c0392b;
  font-size: 12px;
}

.radio-row {
  display: flex;
  gap: 16px;
}

.radio {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.preview-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.preview-item {
  display: grid;
  gap: 4px;
  width: 120px;
}

.preview-item__img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e4e6eb;
}

.preview-item__actions {
  display: flex;
  gap: 4px;
  font-size: 12px;
}

.report-form__error {
  margin: 0;
  color: #c0392b;
  font-size: 13px;
}

.report-form__submit {
  padding: 10px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.report-form__submit:disabled {
  opacity: 0.6;
  cursor: default;
}

/* 窄屏：每个字段占满一行，输入控件宽度 100%，避免横向溢出。 */
@media (max-width: 768px) {
  .report-form {
    max-width: 100%;
  }

  .field__input {
    width: 100%;
  }

  .radio-row {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
