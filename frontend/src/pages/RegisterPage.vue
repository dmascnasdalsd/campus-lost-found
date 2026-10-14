<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const studentNo = ref('')
const nickname = ref('')
const password = ref('')
const department = ref('')
const email = ref('')
const studentNoError = ref('')
const nicknameError = ref('')
const passwordError = ref('')
const formError = ref('')
const submitting = ref(false)

async function onSubmit(): Promise<void> {
  formError.value = ''
  studentNoError.value = ''
  nicknameError.value = ''
  passwordError.value = ''

  if (studentNo.value.trim() === '') {
    studentNoError.value = '请输入学号'
  }
  if (nickname.value.trim() === '') {
    nicknameError.value = '请输入昵称'
  }
  if (password.value.length < 8) {
    passwordError.value = '密码至少 8 位'
  }
  if (studentNoError.value !== '' || nicknameError.value !== '' || passwordError.value !== '') {
    return
  }

  submitting.value = true
  try {
    await auth.register({
      studentNo: studentNo.value.trim(),
      nickname: nickname.value.trim(),
      password: password.value,
      department: department.value.trim() === '' ? undefined : department.value.trim(),
      email: email.value.trim() === '' ? undefined : email.value.trim()
    })
    await router.replace('/login')
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth">
    <h1 class="auth__title">注册</h1>

    <form class="auth__form" novalidate @submit.prevent="onSubmit">
      <label class="field">
        <span class="field__label">学号</span>
        <input v-model="studentNo" class="field__input" type="text" autocomplete="username" />
        <span v-if="studentNoError" class="field__error">{{ studentNoError }}</span>
      </label>

      <label class="field">
        <span class="field__label">昵称</span>
        <input v-model="nickname" class="field__input" type="text" />
        <span v-if="nicknameError" class="field__error">{{ nicknameError }}</span>
      </label>

      <label class="field">
        <span class="field__label">密码</span>
        <input v-model="password" class="field__input" type="password" autocomplete="new-password" />
        <span v-if="passwordError" class="field__error">{{ passwordError }}</span>
      </label>

      <label class="field">
        <span class="field__label">院系（可选）</span>
        <input v-model="department" class="field__input" type="text" />
      </label>

      <label class="field">
        <span class="field__label">邮箱（可选）</span>
        <input v-model="email" class="field__input" type="email" />
      </label>

      <p v-if="formError" class="auth__error">{{ formError }}</p>

      <button class="auth__submit" type="submit" :disabled="submitting">
        {{ submitting ? '注册中…' : '注册' }}
      </button>
    </form>

    <p class="auth__hint">
      已有账号？<RouterLink to="/login">去登录</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth__title {
  margin: 0 0 16px;
  font-size: 22px;
}

.auth__form {
  display: grid;
  gap: 12px;
}

.field {
  display: grid;
  gap: 4px;
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
}

.field__error {
  color: #c0392b;
  font-size: 12px;
}

.auth__error {
  margin: 0;
  color: #c0392b;
  font-size: 13px;
}

.auth__submit {
  padding: 10px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.auth__submit:disabled {
  opacity: 0.6;
  cursor: default;
}

.auth__hint {
  margin-top: 16px;
  color: #666;
  font-size: 13px;
}
</style>
