import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import DishImage from './DishImage.vue'
import {http} from '../api/http'
vi.mock('../api/http',()=>({http:{get:vi.fn()}}))
const NativeURL=URL
beforeEach(()=>{
 vi.resetAllMocks()
 vi.stubGlobal('URL',class extends NativeURL{
  static createObjectURL=vi.fn(()=> 'blob:uploaded')
  static revokeObjectURL=vi.fn()
 })
})
afterEach(()=>vi.unstubAllGlobals())
function deferred(){let resolve!:(blob:Blob)=>void;const promise=new Promise<Blob>(r=>resolve=r);return{promise,resolve}}
it('未上传图片的菜品显示带说明的本地示意图，不请求私有附件',()=>{
 const wrapper=mount(DishImage,{props:{name:'时蔬豆腐（演示）'}})
 expect(wrapper.find('img').exists()).toBe(true)
 expect(wrapper.text()).toContain('示意')
 expect(wrapper.get('img').attributes('alt')).toContain('时蔬豆腐')
 expect(http.get).not.toHaveBeenCalled();wrapper.unmount()
})
it('较早的私有图片请求迟到不能覆盖新菜品图片',async()=>{
 const first=deferred(),second=deferred(),older=new Blob(['first']),newer=new Blob(['second'])
 vi.mocked(http.get).mockImplementationOnce(()=>first.promise as any).mockImplementationOnce(()=>second.promise as any)
 vi.mocked(URL.createObjectURL).mockImplementation(blob=>blob===newer?'blob:new':'blob:old')
 const wrapper=mount(DishImage,{props:{name:'测试餐品',fileId:1}})
 await wrapper.setProps({fileId:2});second.resolve(newer);await flushPromises()
 expect(wrapper.get('img').attributes('src')).toBe('blob:new')
 first.resolve(older);await flushPromises();expect(wrapper.get('img').attributes('src')).toBe('blob:new')
 wrapper.unmount();expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:new')
})
it('私有图片失败或浏览器解码失败时回退到示意图',async()=>{
 vi.mocked(http.get).mockRejectedValueOnce(new Error('附件不可访问')).mockResolvedValueOnce(new Blob() as never)
 const wrapper=mount(DishImage,{props:{name:'杂粮饭',fileId:1}});await flushPromises()
 expect(wrapper.text()).toContain('示意')
 await wrapper.setProps({fileId:2});await flushPromises();expect(wrapper.text()).not.toContain('示意')
 await wrapper.get('img').trigger('error');expect(wrapper.text()).toContain('示意');expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:uploaded');wrapper.unmount()
})
it('组件销毁后返回的附件不再创建临时图片地址',async()=>{
 const pending=deferred();vi.mocked(http.get).mockImplementationOnce(()=>pending.promise as any)
 const wrapper=mount(DishImage,{props:{name:'测试餐品',fileId:1}});wrapper.unmount();pending.resolve(new Blob());await flushPromises()
 expect(URL.createObjectURL).not.toHaveBeenCalled()
})
