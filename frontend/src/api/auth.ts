import { request } from './client'
import type { AuthResponse, User } from './types'

export interface RegisterPayload {
  studentNo: string
  nickname: string
  password: string
  department?: string
  email?: string
}

export interface LoginPayload {
  studentNo: string
  password: string
}

export function register(payload: RegisterPayload): Promise<User> {
  return request<User>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function me(): Promise<User> {
  return request<User>('/auth/me')
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' })
}
