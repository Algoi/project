import {test,expect,type Page,type Locator} from '@playwright/test'
test.setTimeout(120000)
async function loaded(image:Locator){await image.scrollIntoViewIfNeeded();await expect.poll(()=>image.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth>0)).toBe(true)}
async function login(page:Page,role:string){await page.goto('/login');await page.getByLabel('账号',{exact:true}).fill(role);await page.getByLabel('密码',{exact:true}).fill(role);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).toHaveURL(new RegExp(`/${role}$`))}
async function fits(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}

test('浅绿色登录页和四角色首页均展示本地场景图，快捷入口按角色显示',async({page})=>{
 const failures:string[]=[];page.on('pageerror',error=>failures.push(error.message))
 await page.goto('/login');await loaded(page.getByRole('img',{name:'社区餐厅里，工作人员为长者送上热饭',exact:true}));await fits(page)
 const palette=await page.locator('.login-visual').evaluate(node=>getComputedStyle(node).backgroundColor)
 expect(palette).toBe('rgb(229, 239, 223)')
 await page.screenshot({path:`test-results/visual-login-${test.info().project.name}.png`,fullPage:true})
 for(const role of ['admin','canteen','family','community']){
  await login(page,role);await loaded(page.getByRole('img',{name:'社区工作人员为长者送上热饭',exact:true}));await fits(page)
  const links=await page.locator('.home-actions a').evaluateAll(nodes=>nodes.map(node=>node.getAttribute('href')))
  expect(links).toHaveLength(3);expect(links.every(link=>link?.startsWith(`/${role}/`))).toBe(true)
  if(role==='family')await page.screenshot({path:`test-results/visual-home-${test.info().project.name}.png`,fullPage:true})
 }
 expect(failures).toEqual([])
})

test('订餐菜品缺少实拍时显示可加载的示意图，桌面手机均可选择份数',async({page})=>{
 await login(page,'family')
 // Exercise missing-photo rendering without changing any stored dish or uploaded picture.
 await page.route(/\/api\/v1\/elders\/\d+\/recommendations(?:\?|$)/,async route=>{
  const response=await route.fetch()
  if(response.status()!==200){await route.fulfill({response});return}
  const body=await response.json();for(const item of body.data.items)item.imageFileId=null
  await route.fulfill({response,json:body})
 })
 await page.goto('/family/order/new')
 const images=page.locator('article .dish-visual img');await expect(images.first()).toBeVisible()
 for(const image of await images.all()){await loaded(image);expect(await image.getAttribute('alt')).toContain('示意')}
 await expect(page.locator('article .dish-visual figcaption').first()).toContainText('示意')
 const chicken=page.getByRole('article').filter({has:page.getByRole('heading',{name:'清蒸鸡肉（演示）',exact:true})})
 await chicken.getByLabel('选择份数',{exact:true}).fill('1');await expect(page.getByRole('button',{name:'提交订单',exact:true})).toBeEnabled();await fits(page)
 await page.screenshot({path:`test-results/visual-meals-${test.info().project.name}.png`,fullPage:true})
})
