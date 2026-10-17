import {test,expect,type Page} from '@playwright/test'
test('管理员通过网页创建网点、分类与菜品并查询审计',async({page})=>{
 const unique=Date.now().toString()+test.info().project.name
 await page.goto('/login');await page.getByLabel('账号',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill('admin');await page.getByRole('button',{name:'登录',exact:true}).click()
 await page.getByRole('link',{name:'服务网点',exact:true}).click();await page.getByRole('button',{name:'新增',exact:true}).click()
 await page.getByLabel('网点名称',{exact:true}).fill('网页测试网点'+unique);await page.getByLabel('地址',{exact:true}).fill('自动测试服务中心');await page.getByRole('button',{name:'保存',exact:true}).click()
 await revealSite(page,'网页测试网点'+unique)
 await page.getByRole('link',{name:'菜品与价格',exact:true}).click();await page.getByRole('button',{name:'分类',exact:true}).click();await page.getByRole('button',{name:'新增',exact:true}).click();await page.getByLabel('名称',{exact:true}).fill('网页分类'+unique);await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('cell',{name:'网页分类'+unique,exact:true})).toBeVisible()
 await page.getByRole('button',{name:'菜品',exact:true}).click();await page.getByRole('button',{name:'新增',exact:true}).click();await page.getByLabel('菜品名称',{exact:true}).fill('网页测试菜'+unique);await page.getByLabel('社会原价 元',{exact:true}).fill('15.50');await page.getByLabel('老人补贴价 元',{exact:true}).fill('10.50');await page.getByLabel('主要食材（逗号分隔）',{exact:true}).fill('鸡肉，胡萝卜');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('cell',{name:'网页测试菜'+unique,exact:true})).toBeVisible()
 await page.getByRole('link',{name:'操作审计',exact:true}).click();await expect(page.getByRole('cell',{name:'DISH_SAVE',exact:true}).first()).toBeVisible();await page.screenshot({path:`test-results/admin-catalog-${test.info().project.name}.png`,fullPage:true})
})

test('拆分后的用户管理可重置新账号密码，网点日历可独立维护',async({page})=>{
 test.setTimeout(90000)
 const suffix=Date.now().toString()+test.info().project.name,username='boundary'+suffix,password='Boundary-Account-2026!',resetPassword='Boundary-Reset-2026!'
 const login=async(account:string,secret:string)=>{await page.goto('/login');await page.getByLabel('账号',{exact:true}).fill(account);await page.getByLabel('密码',{exact:true}).fill(secret);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).not.toHaveURL(/\/login$/)}
 await login('admin','admin');await page.goto('/admin/users');await page.getByRole('button',{name:'新增',exact:true}).click();await page.getByLabel('登录账号',{exact:true}).fill(username);await page.getByLabel('初始密码（编辑时留空）',{exact:true}).fill(password);await page.getByLabel('姓名',{exact:true}).fill('模块网页测试账号'+suffix);await page.getByRole('button',{name:'保存',exact:true}).click()
 const search=page.getByPlaceholder('输入关键词');await search.fill(username);await page.getByRole('button',{name:'查询',exact:true}).click();let row=page.getByRole('row').filter({hasText:username});await expect(row).toBeVisible();await row.getByRole('button',{name:'重置密码',exact:true}).click();await page.getByLabel('新密码',{exact:true}).fill(resetPassword);await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible()
 await login(username,resetPassword);await expect(page).toHaveURL(/\/family$/);await page.getByRole('button',{name:'退出登录',exact:true}).click();await login('admin','admin')
 await page.goto('/admin/sites');await page.getByRole('button',{name:'新增',exact:true}).click();const site='模块日历测试'+suffix;await page.getByLabel('网点名称',{exact:true}).fill(site);await page.getByRole('button',{name:'保存',exact:true}).click();await revealSite(page,site);row=page.getByRole('row').filter({hasText:site});await row.getByRole('button',{name:'停餐日历',exact:true}).click();await page.getByLabel('停餐日期',{exact:true}).fill('2027-01-01');await page.getByLabel('原因',{exact:true}).fill('独立日历测试');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();await row.getByRole('button',{name:'停餐日历',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('2027-01-01');await expect(page.getByRole('dialog')).toContainText('独立日历测试');await page.screenshot({path:`test-results/modules-admin-${test.info().project.name}.png`,fullPage:true})

})

// Site lists retain their stable ID order; repeated runs may put new fixtures on later pages.
async function revealSite(page:Page,name:string){
 await expect(page.getByRole('dialog')).not.toBeVisible()
 await page.waitForLoadState('networkidle')
 const cell=page.getByRole('cell',{name,exact:true})
 while(await cell.count()===0){
  const next=page.getByRole('button',{name:'下一页',exact:true})
  await expect(next).toBeEnabled()
  await next.click()
  await page.waitForLoadState('networkidle')
 }
 await expect(cell).toBeVisible()
}
