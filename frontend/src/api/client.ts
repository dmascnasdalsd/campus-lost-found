import type { ApiResponse } from './types'

const BASE_URL = '/api'

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
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init
  })

  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message)
  }
  return body.data
}
