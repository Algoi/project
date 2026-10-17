import {test,expect,type Page} from '@playwright/test'
test.setTimeout(120000)
async function login(page:Page,role:string){await page.goto('/login');await page.getByLabel('账号',{exact:true}).fill(role);await page.getByLabel('密码',{exact:true}).fill(role);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).toHaveURL(new RegExp(`/${role}$`))}
test('各角色列表不存在关键词均返回零条，清空查询恢复记录',async({page})=>{
 for(const [role,paths]of Object.entries({admin:['/admin/dishes','/admin/sites'],family:['/family/reviews','/family/tickets'],community:['/community/subsidies'],canteen:['/canteen/reviews']})){
  await login(page,role)
  for(const path of paths){await page.goto(path);await page.waitForLoadState('networkidle');const search=page.getByRole('textbox',{name:'搜索',exact:true});await search.fill('__NO_MATCH_7c4f18__');await page.getByRole('button',{name:'查询',exact:true}).click();await expect(page.getByText('共 0 条',{exact:true})).toBeVisible();await search.fill('');await page.getByRole('button',{name:'查询',exact:true}).click();await page.waitForLoadState('networkidle');await expect(page.locator('main .el-alert--error')).toHaveCount(0)}
 }
})
test('预测失败仍显示备餐清单，公告失败有重试而不伪装空数据',async({page})=>{
 await login(page,'canteen');await page.route(/\/sites\/\d+\/forecasts\?/,route=>route.fulfill({status:503,json:{code:'ALGORITHM_UNAVAILABLE',message:'预测故障测试'}}))
 await page.goto('/canteen/preparation');await expect(page.getByText(/预测暂不可用/).first()).toBeVisible();await expect(page.locator('main .el-table').first().locator('.el-table__row').first()).toBeVisible();await expect(page.locator('main .el-alert--error')).toHaveCount(0)
 await page.unrouteAll();await login(page,'family');await page.route(/\/api\/v1\/notices\?/,route=>route.fulfill({status:503,json:{code:'UNAVAILABLE',message:'公告故障测试'}}));await page.goto('/family');await expect(page.getByText('公告故障测试',{exact:true})).toBeVisible();await expect(page.getByText(/当前暂无公告/)).toHaveCount(0);await page.unrouteAll();await page.getByRole('button',{name:'重试加载公告',exact:true}).click();await expect(page.getByText('公告故障测试',{exact:true})).toHaveCount(0)
})
