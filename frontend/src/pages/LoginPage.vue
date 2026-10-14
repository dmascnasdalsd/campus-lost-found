<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const studentNo = ref('')
const password = ref('')
const studentNoError = ref('')
const passwordError = ref('')
const formError = ref('')
const submitting = ref(false)

async function onSubmit(): Promise<void> {
  formError.value = ''
  studentNoError.value = ''
  passwordError.value = ''

  if (studentNo.value.trim() === '') {
    studentNoError.value = '请输入学号'
  }
  if (password.value === '') {
    passwordError.value = '请输入密码'
  }
  if (studentNoError.value !== '' || passwordError.value !== '') {
    return
  }

  submitting.value = true
  try {
    await auth.login({ studentNo: studentNo.value.trim(), password: password.value })
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' && redirect !== '' ? redirect : '/'
    await router.replace(target)
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth">
    <h1 class="auth__title">登录</h1>

    <form class="auth__form" novalidate @submit.prevent="onSubmit">
      <label class="field">
        <span class="field__label">学号</span>
        <input v-model="studentNo" class="field__input" type="text" autocomplete="username" />
        <span v-if="studentNoError" class="field__error">{{ studentNoError }}</span>
      </label>

      <label class="field">
        <span class="field__label">密码</span>
        <input v-model="password" class="field__input" type="password" autocomplete="current-password" />
        <span v-if="passwordError" class="field__error">{{ passwordError }}</span>
      </label>

      <p v-if="formError" class="auth__error">{{ formError }}</p>

      <button class="auth__submit" type="submit" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>

    <p class="auth__hint">
      还没有账号？<RouterLink to="/register">去注册</RouterLink>
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
