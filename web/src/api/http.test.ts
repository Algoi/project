import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { http } from './http'

const originalAdapter = http.defaults.adapter
beforeEach(() => sessionStorage.clear())
afterEach(() => { http.defaults.adapter = originalAdapter; vi.restoreAllMocks() })

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError('unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
    status: 401, statusText: 'Unauthorized', headers: {}, config,
    data: { code: 'UNAUTHENTICATED', message: '请重新登录', traceId: 'test-trace' },
  })
}

it('当前会话的 401 通知应用清除身份，并保留可定位的错误码', async () => {
  sessionStorage.setItem('meal.token', 'current-token')
  const dispatch = vi.spyOn(window, 'dispatchEvent')
  http.defaults.adapter = async config => { throw unauthorized(config) }
  await expect(http.get('/auth/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED', traceId: 'test-trace' })
  expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'meal:unauthenticated' }))
})

it('旧会话迟到的 401 不清除新登录的身份', async () => {
  sessionStorage.setItem('meal.token', 'old-token')
  const dispatch = vi.spyOn(window, 'dispatchEvent')
  http.defaults.adapter = async config => {
    sessionStorage.setItem('meal.token', 'new-token')
    throw unauthorized(config)
  }
  await expect(http.get('/auth/me')).rejects.toMatchObject({ status: 401 })
  expect(dispatch).not.toHaveBeenCalled()
  expect(sessionStorage.getItem('meal.token')).toBe('new-token')
})

it('输入错误登录密码时保留表单错误，不触发会话过期跳转', async () => {
  sessionStorage.setItem('meal.token', 'current-token')
  const dispatch = vi.spyOn(window, 'dispatchEvent')
  http.defaults.adapter = async config => { throw unauthorized(config) }
  await expect(http.post('/auth/login', { username: 'family', password: 'wrong' })).rejects.toMatchObject({ status: 401 })
  expect(dispatch).not.toHaveBeenCalled()
})
