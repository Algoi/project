import{it,expect,vi,beforeEach,afterEach}from'vitest'
import{mount,flushPromises}from'@vue/test-utils'
import ElementPlus from'element-plus'
import OrderNew from'./OrderNew.vue'
import{http}from'../../api/http'
import{day}from'../../utils/dates'
vi.mock('../../api/http',()=>({http:{get:vi.fn(),post:vi.fn()}}))
vi.mock('vue-router',()=>({useRoute:()=>({path:'/family/order/new'}),useRouter:()=>({push:vi.fn()})}))
function deferred(){let resolve!:(value:any)=>void;const promise=new Promise<any>(r=>resolve=r);return{promise,resolve}}
const response=(id:number,name:string)=>({items:[{menuItemId:id,dishId:id,dishName:name,effectivePrice:'8.00',originalPrice:'12.00',ingredients:['米饭'],reasons:['通过规则核对'],score:'50',remainingQty:10}],excluded:[]})
beforeEach(()=>vi.resetAllMocks())
afterEach(()=>vi.unstubAllGlobals())
it('局域网 HTTP 没有 randomUUID 时仍可加载推荐，重试沿用防重键、切换日期生成新键',async()=>{
 const browserCrypto=globalThis.crypto
 vi.stubGlobal('crypto',{getRandomValues:browserCrypto.getRandomValues.bind(browserCrypto)})
 vi.mocked(http.get).mockImplementation((path:any)=>Promise.resolve(path.startsWith('/addresses?')?{records:[],total:0}:path.startsWith('/elders?')?{records:[{id:1,siteId:1,name:'测试老人',status:'ACTIVE',approvalStatus:'APPROVED'}]}:response(1,'可售菜品')))
 vi.mocked(http.post).mockRejectedValue(new Error('临时连接失败'))
 const wrapper=mount(OrderNew,{global:{plugins:[ElementPlus],stubs:{DishImage:true}}})
 try{
  await flushPromises();expect(wrapper.text()).toContain('可售菜品')
  await wrapper.get('#qty-1').setValue('1');await wrapper.get('button').trigger('click');await flushPromises()
  await wrapper.get('button').trigger('click');await flushPromises()
  const key=(index:number)=>(vi.mocked(http.post).mock.calls[index]![2] as any).headers['Idempotency-Key']
  expect(key(0)).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  expect(key(1)).toBe(key(0))
  await wrapper.get('#order-date').setValue(day(3));await flushPromises()
  await wrapper.get('#qty-1').setValue('1');await wrapper.get('button').trigger('click');await flushPromises()
  expect(key(2)).not.toBe(key(0))
 }finally{wrapper.unmount()}
})
it('切换日期时清空旧菜品并禁用提交，较早请求迟到不能覆盖新日期',async()=>{
 const first=deferred(),second=deferred();let recommendations=0
 vi.mocked(http.get).mockImplementation((path:any)=>path.startsWith('/addresses?')?Promise.resolve({records:[],total:0}):path.startsWith('/elders?')?Promise.resolve({records:[{id:1,siteId:1,name:'测试老人',status:'ACTIVE',approvalStatus:'APPROVED'}]}):++recommendations===1?first.promise:second.promise)
 const wrapper=mount(OrderNew,{global:{plugins:[ElementPlus],stubs:{DishImage:true}}})
 await flushPromises();await wrapper.get('#order-date').setValue(day(3));await flushPromises();expect(wrapper.get('button').attributes('disabled')).toBeDefined()
 second.resolve(response(2,'新日期菜品'));await flushPromises();expect(wrapper.text()).toContain('新日期菜品');first.resolve(response(1,'旧日期菜品'));await flushPromises();expect(wrapper.text()).not.toContain('旧日期菜品');wrapper.unmount()
})
it('已加载候选在新日期加载期间不可选择，恢复后只使用新菜品编号',async()=>{
 const next=deferred();let recommendations=0
 vi.mocked(http.get).mockImplementation((path:any)=>path.startsWith('/addresses?')?Promise.resolve({records:[],total:0}):path.startsWith('/elders?')?Promise.resolve({records:[{id:1,siteId:1,name:'测试老人',status:'ACTIVE',approvalStatus:'APPROVED'}]}):++recommendations===1?Promise.resolve(response(1,'原日期菜品')):next.promise)
 const wrapper=mount(OrderNew,{global:{plugins:[ElementPlus],stubs:{DishImage:true}}});await flushPromises();expect(wrapper.text()).toContain('原日期菜品');await wrapper.get('#qty-1').setValue('1');await wrapper.get('#order-date').setValue(day(4));await flushPromises();expect(wrapper.text()).not.toContain('原日期菜品');expect(wrapper.find('#qty-1').exists()).toBe(false)
 next.resolve(response(3,'新日期可售菜品'));await flushPromises();expect(wrapper.find('#qty-3').exists()).toBe(true);expect(wrapper.get('button').attributes('disabled')).toBeUndefined();wrapper.unmount()
})
