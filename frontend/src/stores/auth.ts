import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { clearStoredToken, getStoredToken, setStoredToken } from '@/api/client'
import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { User } from '@/api/types'

/**
 * 登录态：token 与当前用户。
 * v1 把 token 持久化到 localStorage（见 client.ts 说明），刷新页面后仍可保持登录。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getStoredToken())
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => token.value !== null)

  async function login(payload: LoginPayload): Promise<void> {
    const result = await authApi.login(payload)
    token.value = result.token
    setStoredToken(result.token)
    user.value = result.user
  }

  async function register(payload: RegisterPayload): Promise<User> {
    return authApi.register(payload)
  }

  async function fetchMe(): Promise<void> {
    if (token.value === null) {
      return
    }
    user.value = await authApi.me()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      user.value = null
      clearStoredToken()
    }
  }

  return { token, user, isAuthenticated, login, register, fetchMe, logout }
})
