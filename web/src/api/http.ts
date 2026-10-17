import axios from 'axios'

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public traceId?: string) { super(message) }
}

export const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })
http.interceptors.request.use(config => {
  const token = sessionStorage.getItem('meal.token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
http.interceptors.response.use(
  response => response.config.responseType === 'blob' ? response.data : response.data.data,
  error => {
    const response = error.response
    const requestToken = error.config?.headers?.Authorization
    const currentToken = sessionStorage.getItem('meal.token')
    if (response?.status === 401 && error.config?.url !== '/auth/login' && currentToken && requestToken === `Bearer ${currentToken}`) {
      window.dispatchEvent(new Event('meal:unauthenticated'))
    }
    return Promise.reject(new ApiError(response?.status ?? 0, response?.data?.code ?? 'NETWORK_ERROR',
      response?.data?.message ?? '连接失败，请检查网络后重试', response?.data?.traceId))
  },
)
