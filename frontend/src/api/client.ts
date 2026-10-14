import type { ApiResponse } from './types'

const BASE_URL = '/api'

/**
 * token 存储键。v1 把 token 放在 localStorage 以便刷新页面后保持登录态
 * （相对 spec 里「仅内存」的务实简化）。
 */
const TOKEN_KEY = 'lostfound_token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 后端返回的业务错误：code 非 0 时抛出。 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {}
  // multipart（FormData）不手动设 Content-Type，交给浏览器带 boundary。
  if (!(init?.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getStoredToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    headers,
    ...init
  })

  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message)
  }
  return body.data
}
