import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import MenuView from './MenuView.vue'
import { http } from '../../api/http'

vi.mock('../../api/http', () => ({ http: { get: vi.fn() } }))
vi.mock('../../stores/auth', () => ({ useAuthStore: () => ({ currentSiteId: 1 }) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/canteen/menus' }) }))

it('预测请求失败后菜品仍能加载供上报使用',async()=>{
 vi.mocked(http.get).mockImplementation((path:any)=>path.endsWith('/forecasts')?Promise.reject(new Error('预测暂不可用')):Promise.resolve(path.endsWith('/menus')?{items:[]}:path.startsWith('/dishes')?{records:[{id:1,name:'仍可选择的菜品',status:'ACTIVE'}]}:[]))
 const w=mount(MenuView,{global:{plugins:[ElementPlus],stubs:{TrendChart:true}}});await flushPromises()
 expect(http.get).toHaveBeenCalledWith('/dishes?size=100',{params:{page:1,size:100}})
 expect(w.text()).toContain('预测暂不可用');w.unmount()
})

it('预测尚未返回时，缺少营养覆盖值的菜谱也能安全显示并编辑原营养值', async () => {
  let resolveForecast!: (value: unknown) => void
  const forecast = new Promise(resolve => { resolveForecast = resolve })
  vi.mocked(http.get).mockImplementation((path: any) => {
    if (path.endsWith('/menus')) return Promise.resolve({ status: 'DRAFT', items: [{
      id: 1, dishId: 1, dishName: '时蔬豆腐', plannedQty: 20,
      ingredients: ['豆腐'], allergenIds: [], nutritionOverride: null,
      nutrition: { caloriesKcal: 120, proteinGrams: 8 },
    }] })
    if (path.endsWith('/forecasts')) return forecast
    if (path.startsWith('/dishes')) return Promise.resolve({ records: [] })
    return Promise.resolve([])
  })
  const errors: unknown[] = []
  const wrapper = mount(MenuView, { global: {
    plugins: [ElementPlus], stubs: { TrendChart: true },
    config: { errorHandler: error => { errors.push(error) } },
  } })
  try {
    await flushPromises()
    expect(errors).toEqual([])
    expect((wrapper.get('input[aria-label="能量 kcal"]').element as HTMLInputElement).value).toBe('120')
    expect((wrapper.get('input[aria-label="蛋白质 g"]').element as HTMLInputElement).value).toBe('8')
    await wrapper.get('input[aria-label="能量 kcal"]').setValue('135')
    resolveForecast({ points: [], history: [] })
    await flushPromises()
    expect((wrapper.get('input[aria-label="能量 kcal"]').element as HTMLInputElement).value).toBe('135')
    expect(errors).toEqual([])
  } finally {
    resolveForecast({ points: [], history: [] })
    await flushPromises()
    wrapper.unmount()
  }
})
