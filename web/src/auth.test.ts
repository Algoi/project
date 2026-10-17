import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { createAppRouter } from './router'
import { authApi } from './api/auth'

vi.mock('./api/auth', () => ({
  authApi: { login: vi.fn(), me: vi.fn(), logout: vi.fn() },
}))

const profile = (roles = ['FAMILY']) => ({
  id: 3, username: 'family', realName: '家属', phoneMasked: '138****5678', roles, sites: [],
})

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.resetAllMocks()
})

describe('登录状态与路由权限', () => {
  it('清除会话后返回的旧恢复请求不能恢复用户身份', async () => {
    sessionStorage.setItem('meal.token', 'old-token')
    let resolveProfile: (value: ReturnType<typeof profile>) => void = () => {}
    vi.mocked(authApi.me).mockImplementation(() => new Promise(resolve => { resolveProfile = resolve }))
    const auth = useAuthStore()
    const restoring = auth.restore()
    auth.clear()
    resolveProfile(profile())
    await restoring
    expect(auth.user).toBeNull()
    expect(sessionStorage.getItem('meal.token')).toBeNull()
  })

  it('未登录访问受保护页面时跳转登录页', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/family')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(authApi.me).not.toHaveBeenCalled()
  })

  it('刷新后从服务端恢复权限，忽略浏览器伪造的角色', async () => {
    sessionStorage.setItem('meal.token', 'test-token')
    sessionStorage.setItem('meal.roles', '["ADMIN"]')
    vi.mocked(authApi.me).mockResolvedValue(profile())
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/403')
    expect(authApi.me).toHaveBeenCalledOnce()
  })

  it('被禁用的业务菜单不能通过直接输入网址访问',async()=>{sessionStorage.setItem('meal.token','test-token');vi.mocked(authApi.me).mockResolvedValue({...profile(),deniedPermissions:['menu:/family/orders']});const router=createAppRouter(createMemoryHistory());await router.push('/family/orders');expect(router.currentRoute.value.path).toBe('/403');await router.push('/family/elders');expect(router.currentRoute.value.path).toBe('/family/elders')})

  it('多角色用户可进入其每个角色首页', async () => {
    sessionStorage.setItem('meal.token', 'test-token')
    vi.mocked(authApi.me).mockResolvedValue(profile(['CANTEEN', 'COMMUNITY']))
    const router = createAppRouter(createMemoryHistory())
    await router.push('/canteen')
    expect(router.currentRoute.value.path).toBe('/canteen')
    await router.push('/community')
    expect(router.currentRoute.value.path).toBe('/community')
    await router.push('/account')
    expect(useAuthStore().activeRole).toBe('COMMUNITY')
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/community')
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/403')
  })

  it('过期会话清除令牌并回到登录页', async () => {
    sessionStorage.setItem('meal.token', 'expired')
    vi.mocked(authApi.me).mockRejectedValue({ status: 401, message: '登录已过期' })
    const router = createAppRouter(createMemoryHistory())
    await router.push('/family')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(sessionStorage.getItem('meal.token')).toBeNull()
  })

  it('服务暂不可用时保留会话并显示重试页面', async () => {
    sessionStorage.setItem('meal.token', 'valid-token')
    vi.mocked(authApi.me).mockRejectedValue({ status: 500, message: '服务暂不可用' })
    const router = createAppRouter(createMemoryHistory())
    await router.push('/family')
    expect(router.currentRoute.value.path).toBe('/session-unavailable')
    expect(sessionStorage.getItem('meal.token')).toBe('valid-token')
  })

  it('登录失败不存储令牌，退出成功清除本人信息与网点', async () => {
    const auth = useAuthStore()
    vi.mocked(authApi.login).mockRejectedValueOnce(new Error('账号或密码错误'))
    await expect(auth.login('family', 'wrong')).rejects.toThrow()
    expect(sessionStorage.getItem('meal.token')).toBeNull()
    vi.mocked(authApi.login).mockResolvedValue({ token: 'new-token', expiresAt: '2026-10-18T00:00:00Z', user: profile() })
    await auth.login('family', 'password')
    expect(auth.user?.username).toBe('family')
    await auth.logout()
    expect(auth.user).toBeNull()
    expect(sessionStorage.getItem('meal.token')).toBeNull()
    expect(auth.currentSiteId).toBeNull()
  })
})
