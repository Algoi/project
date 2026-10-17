import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../../router'
import LoginView from './LoginView.vue'
import { authApi } from '../../api/auth'

vi.mock('../../api/auth', () => ({ authApi: { login: vi.fn(), me: vi.fn() } }))
beforeEach(() => { sessionStorage.clear(); vi.resetAllMocks() })

it('登录请求中禁止重复提交，失败后显示错误并恢复按钮', async () => {
  let rejectLogin: (reason: Error) => void = () => {}
  vi.mocked(authApi.login).mockImplementation(() => new Promise((_, reject) => { rejectLogin = reject }))
  const wrapper = mount(LoginView, { global: { plugins: [createPinia(), createAppRouter(createMemoryHistory()), ElementPlus] } })
  await flushPromises()
  await wrapper.get('#username').setValue('family')
  await wrapper.get('#password').setValue('Wrong-Password-2026!')
  await wrapper.get('form').trigger('submit')
  await wrapper.get('form').trigger('submit')
  expect(authApi.login).toHaveBeenCalledOnce()
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  rejectLogin(new Error('账号或密码错误'))
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('账号或密码错误')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  wrapper.unmount()
})
