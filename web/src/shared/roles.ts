export const roles = [
  { code: 'ADMIN', path: '/admin', name: '系统管理员' },
  { code: 'CANTEEN', path: '/canteen', name: '食堂工作人员' },
  { code: 'FAMILY', path: '/family', name: '老年人家属' },
  { code: 'COMMUNITY', path: '/community', name: '社区操作员' },
] as const

export function roleHome(allowed: string[]) {
  return roles.find(role => allowed.includes(role.code))?.path ?? '/403'
}

