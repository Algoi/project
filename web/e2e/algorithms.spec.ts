import { test, expect, type Page } from '@playwright/test'

test.setTimeout(120000)
async function login(page: Page, role: string) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(role)
  await page.getByLabel('密码', { exact: true }).fill(role)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/${role}$`))
}

test('预测页面的七天人数可以按历史样本独立复算', async ({ page }) => {
  await login(page, 'canteen')
  const response = page.waitForResponse(r => /\/sites\/\d+\/forecasts\?/.test(r.url()) && r.status() === 200)
  await page.getByRole('navigation').getByRole('link', { name: '就餐人数预测', exact: true }).click()
  const data = (await (await response).json()).data
  expect(['weekday_average_v1', 'recent_weighted_average_v1']).toContain(data.algorithm)
  expect(data.points).toHaveLength(7)
  const history = new Map<string, number>(data.history.map((item: any) => [item.date, Number(item.count)]))
  let checked = 0
  for (const point of data.points) {
    if (point.closed) { expect(point.count).toBe(0); continue }
    if (point.insufficientData) { expect(point.count).toBeNull(); continue }
    const counts: number[] = point.sampleDates.map((date: string) => {
      expect(history.has(date)).toBe(true)
      return history.get(date)!
    })
    expect(counts.length).toBeGreaterThanOrEqual(3)
    const weights = counts.map((_, index) => data.algorithm === 'recent_weighted_average_v1' ? counts.length - index : 1)
    const expected = Math.round(counts.reduce((sum, count, index) => sum + count * weights[index]!, 0) / weights.reduce((a, b) => a + b, 0))
    expect(point.count).toBe(expected)
    const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: point.date, exact: true }) })
    await expect(row.getByRole('cell', { name: String(expected), exact: true }).first()).toBeVisible()
    checked++
  }
  expect(checked, '演示网点应有可复算的历史就餐数据').toBeGreaterThan(0)
  console.log('预测核对', JSON.stringify({ algorithm: data.algorithm, checked, points: data.points }))
  await page.screenshot({ path: `test-results/algorithm-forecast-${test.info().project.name}.png`, fullPage: true })
})

test('切换演示老人可以核对推荐排序和花生过敏排除', async ({ page }) => {
  await login(page, 'family')
  const matches = (url: string) => /\/elders\/\d+\/recommendations\?/.test(url)
  const first = page.waitForResponse(r => matches(r.url()) && r.status() === 200)
  await page.getByRole('navigation').getByRole('link', { name: '为家人订餐', exact: true }).click()
  let data = (await (await first).json()).data
  const selector = page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '选择老人', exact: true }) })
  const summaries: unknown[] = []
  for (const name of ['李阿姨（演示）', '王大爷（演示）']) {
    if (!(await selector.locator('.el-select__selected-item').allTextContents()).includes(name)) {
      await selector.click()
      const next = page.waitForResponse(r => matches(r.url()) && r.status() === 200)
      await page.getByRole('option', { name, exact: true }).click()
      data = (await (await next).json()).data
    }
    await expect(page.getByRole('status')).toHaveCount(0)
    expect(data.algorithm).toBe('weighted_score_v1')
    const scores = data.items.map((item: any) => Number(item.score))
    expect(scores).toEqual([...scores].sort((a, b) => b - a))
    await expect(page.locator('article h2')).toHaveText(data.items.map((item: any) => item.dishName))
    const peanut = page.getByRole('article').filter({ has: page.getByRole('heading', { name: '花生鸡丁（演示）', exact: true }) })
    if (name.startsWith('李')) {
      expect(data.excluded.some((item: any) => item.dishName === '花生鸡丁（演示）' && item.reason.includes('过敏'))).toBe(true)
      await expect(peanut).toHaveCount(0)
      await page.getByText('查看未推荐的菜品及原因', { exact: true }).click()
      await expect(page.getByText(/花生鸡丁（演示）：.*过敏/)).toBeVisible()
      await page.screenshot({ path: `test-results/algorithm-recommendation-${test.info().project.name}.png`, fullPage: true })
    } else {
      await expect(peanut).toBeVisible()
    }
    summaries.push({ elder: name, algorithm: data.algorithm, items: data.items.map((item: any) => ({ name: item.dishName, score: item.score })), excluded: data.excluded })
  }
  console.log('推荐核对', JSON.stringify(summaries))
})
