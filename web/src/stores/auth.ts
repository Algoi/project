import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type UserProfile } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserProfile | null>(null)
  const currentSiteId = ref<number | null>(null)
  const activeRole = ref<string | null>(null)
  let restoring: Promise<void> | null = null
  let generation = 0

  function clear() {
    generation++
    restoring = null
    user.value = null
    currentSiteId.value = null
    activeRole.value = null
    sessionStorage.removeItem('meal.token')
  }

  function setUser(profile: UserProfile) {
    user.value = profile
    if (!activeRole.value || !profile.roles.includes(activeRole.value)) activeRole.value = profile.roles[0] ?? null
    if (!profile.sites.some(site => site.id === currentSiteId.value)) currentSiteId.value = profile.sites[0]?.id ?? null
  }

  async function login(username: string, password: string) {
    const requestGeneration = ++generation
    const result = await authApi.login(username, password)
    if (requestGeneration !== generation) throw new Error('登录状态已变更，请重试')
    sessionStorage.setItem('meal.token', result.token)
    setUser(result.user)
  }

  async function restore() {
    if (user.value || !sessionStorage.getItem('meal.token')) return
    if (restoring) return restoring
    const requestGeneration = generation
    const request = authApi.me().then(profile => {
      if (requestGeneration === generation) setUser(profile)
    }).catch(error => {
      if (error.status === 401 && requestGeneration === generation) clear()
      throw error
    }).finally(() => { if (restoring === request) restoring = null })
    restoring = request
    return restoring
  }

  async function logout() {
    const currentToken = sessionStorage.getItem('meal.token')
    await authApi.logout()
    if (sessionStorage.getItem('meal.token') === currentToken) clear()
  }

  return { user, currentSiteId, activeRole, clear, setUser, login, restore, logout }
})
