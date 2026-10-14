<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

// token 变化（含刷新后从 localStorage 恢复）时刷新当前用户信息。
onMounted(() => {
  if (auth.isAuthenticated) {
    void auth.fetchMe()
  }
})

watch(
  () => auth.token,
  (token) => {
    if (token) {
      void auth.fetchMe()
    }
  }
)

async function onLogout(): Promise<void> {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <div class="app">
    <nav class="app__nav">
      <RouterLink class="app__brand" to="/">校园失物招领</RouterLink>
      <div class="app__actions">
        <template v-if="auth.isAuthenticated">
          <RouterLink class="app__link" to="/me/reports">我的发布</RouterLink>
          <span class="app__user">{{ auth.user?.nickname ?? '已登录' }}</span>
          <button class="app__link" type="button" @click="onLogout">退出登录</button>
        </template>
        <template v-else>
          <RouterLink class="app__link" to="/login">登录</RouterLink>
          <RouterLink class="app__link" to="/register">注册</RouterLink>
        </template>
      </div>
    </nav>
    <main class="app__main">
      <RouterView />
    </main>
  </div>
</template>
