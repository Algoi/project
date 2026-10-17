import { test, expect } from '@playwright/test'

for (const username of ['admin', 'canteen', 'family', 'community']) {
  const role = username
  test(`${role} 登录、刷新、角色隔离和退出`, async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('账号', { exact: true }).fill(username)
    await page.getByLabel('密码', { exact: true }).fill(username)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/${role}$`))
    await expect(page.getByRole('heading', { name: /您好/ })).toBeVisible()
    await expect(page.getByRole('definition').first()).toHaveText(username)
    await page.screenshot({ path: `test-results/login-${username}-${test.info().project.name}.png`, fullPage: true })
    await page.reload()
    await expect(page.getByRole('heading', { name: /您好/ })).toBeVisible()
    if (role !== 'admin') {
      await page.goto('/admin')
      await expect(page).toHaveURL(/\/403$/)
      await page.getByRole('button', { name: '返回首页' }).click()
    }
    await page.getByRole('link', { name: /账号设置/ }).click()
    await expect(page.getByRole('heading', { name: '照顾好您的账号' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.goto(`/${role}`)
    await expect(page).toHaveURL(/\/login$/)
  })
}

test('登录失败有明确反馈，匿名访问受保护路由被拦截', async ({ page }) => {
  await page.goto('/family')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('账号', { exact: true }).fill('family')
  await page.getByLabel('密码', { exact: true }).fill('Wrong-Password-2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('账号或密码错误')
  await expect(page).toHaveURL(/\/login$/)
})

test('家属可修改个人信息并使用大字模式', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('family')
  await page.getByLabel('密码', { exact: true }).fill('family')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/family$/)
  if(!await page.getByRole('switch',{name:'大字模式'}).isChecked())await page.getByText('大字', { exact: true }).click()
  await expect(page.getByRole('switch', { name: '大字模式' })).toBeChecked()
  await expect(page.locator('.app-shell')).toHaveClass(/large-text/)
  await page.getByRole('link', { name: /账号设置/ }).click()
  await page.getByLabel('姓名', { exact: true }).fill('家属用户')
  await page.getByRole('button', { name: '保存个人信息' }).click()
  await expect(page.getByRole('status')).toContainText('个人信息已保存')
  await page.screenshot({ path: `test-results/account-${test.info().project.name}.png`, fullPage: true })
})

test('修改密码后退出全部会话并要求重新登录', async ({ page }) => {
  const password = 'family'
  const nextPassword = 'Changed-Password-2026!'
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('family')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/family$/)
  await page.getByRole('link', { name: /账号设置/ }).click()
  try {
    await page.getByLabel('原密码', { exact: true }).fill(password)
    await page.getByLabel('新密码（至少 12 个字符）', { exact: true }).fill(nextPassword)
    await page.getByLabel('再次输入新密码', { exact: true }).fill(nextPassword)
    await page.getByRole('button', { name: '修改密码并重新登录' }).click()
    await expect(page).toHaveURL(/\/login\?changed=1$/)
    await expect(page.getByText('密码已修改，请使用新密码重新登录')).toBeVisible()
  } finally {
    // Restore the shared demo account for the other viewport and subsequent local use.
    const response = await page.request.post('/api/v1/auth/login', { data: { username: 'family', password: nextPassword } })
    if (response.ok()) {
      const result = await response.json()
      const restored = await page.request.put('/api/v1/auth/password', {
        headers: { Authorization: `Bearer ${result.data.token}` }, data: { oldPassword: nextPassword, newPassword: password },
      })
      expect(restored.ok()).toBeTruthy()
    }
  }
})

test('旧账号名称不能再登录', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('admin_demo')
  await page.getByLabel('密码', { exact: true }).fill('admin')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('账号或密码错误')
  await expect(page).toHaveURL(/\/login$/)
})
