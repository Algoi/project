import { test, expect, type Page } from '@playwright/test'
import { navigation } from '../src/navigation'

test.setTimeout(120000)
async function login(page: Page, role: string) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(role)
  await page.getByLabel('密码', { exact: true }).fill(role)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/${role}$`))
}
const headings: Record<string, string> = {
  '/admin/users': '用户与权限', '/admin/sites': '服务网点', '/admin/dishes': '菜品与价格',
  '/admin/audit': '操作审计', '/admin/recommendation-settings': '膳食推荐参数',
  '/admin/notices': '公告与重要通知', '/admin/dashboard': '运营数据看板', '/admin/tickets': '工单受理与处理',
  '/canteen/menus': '菜谱上报', '/canteen/forecasts': '就餐人数预测', '/canteen/preparation': '备餐出餐清单',
  '/canteen/orders': '订单备餐出餐', '/canteen/redemptions': '取餐核验与核销', '/canteen/delivery': '配送清单',
  '/canteen/reviews': '网点菜品评价', '/canteen/notices': '公告与重要通知',
  '/family/addresses':'常用配送地址', '/family/wallet':'我的钱包', '/family/elders': '我的老人', '/family/order/new': '为家人订一餐', '/family/orders': '我的订单',
  '/family/payments': '支付与消费账单', '/family/reviews': '我的菜品评价', '/family/tickets': '意见反馈与投诉',
  '/family/notices': '公告与重要通知', '/family/nutrition': '老人膳食报告',
  '/community/elders': '老人档案', '/community/subsidies': '补贴申请与审核', '/community/menus': '每日菜谱确认',
  '/community/proxy-orders': '协助老人订餐', '/community/orders': '网点订单', '/community/refunds': '退单退款审核',
  '/community/redemptions': '取餐核验与核销', '/community/delivery': '配送清单',
  '/community/tickets': '工单受理与处理', '/community/notices': '公告与重要通知', '/community/nutrition': '老人膳食报告',
}

for (const role of ['admin', 'canteen', 'family', 'community']) {
  test(`${role} 通过真实菜单点击切换全部业务页面，地址、标题与选中项同步`, async ({ page }) => {
    const failures: string[] = []
    page.on('pageerror', error => failures.push(error.message))
    page.on('response', response => {
      if (response.url().includes('/api/v1/') && response.status() >= 400)
        failures.push(`${response.status()} ${response.url()}`)
    })
    await login(page, role)
    for (const item of navigation[role.toUpperCase()]!) {
      await page.getByRole('navigation', { name: '主要导航' }).getByRole('link', { name: item.label, exact: true }).click()
      await expect(page).toHaveURL(new RegExp(`${item.path}$`))
      await expect(page.getByRole('navigation', { name: '主要导航' }).locator('a.nav-active')).toHaveAttribute('href', item.path)
      await expect(page.locator('main h1').first()).toHaveText(headings[item.path]!)
      await page.waitForLoadState('networkidle')
      await expect(page.locator('main .el-alert--error')).toHaveCount(0)
    }
    await page.getByRole('navigation', { name: '主要导航' }).getByRole('link', { name: /我的首页/ }).click()
    await expect(page.locator('main h1')).toContainText('您好')
    expect(failures).toEqual([])
  })
}
