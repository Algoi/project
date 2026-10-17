import { http } from './http'

export interface Site { id: number; name: string; address: string | null; phone: string | null; businessStatus: string }
export interface UserProfile {
  deniedPermissions?: string[]
  id: number; username: string; realName: string | null; phoneMasked: string | null; roles: string[]; sites: Site[]
}
export interface LoginResult { token: string; expiresAt: string; user: UserProfile }

export const authApi = {
  login: (username: string, password: string) => http.post<never, LoginResult>('/auth/login', { username, password }),
  me: () => http.get<never, UserProfile>('/auth/me'),
  logout: () => http.post('/auth/logout'),
  updateProfile: (realName: string, phone: string | null) => http.put<never, UserProfile>('/auth/profile', { realName, phone }),
  changePassword: (oldPassword: string, newPassword: string) => http.put('/auth/password', { oldPassword, newPassword }),
}
