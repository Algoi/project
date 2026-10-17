import {it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import RecordPage from './RecordPage.vue'
import {http} from '../api/http'
vi.mock('../api/http',()=>({http:{get:vi.fn()}}))
it('关联对象可分页加载到首屏之外，不受 100 条限制',async()=>{
 vi.mocked(http.get).mockImplementation((path:any,config:any)=>Promise.resolve(path==='/paged'?{records:[],total:0}:{records:config?.params?.page===2?[{id:101,name:'第101位老人'}]:Array.from({length:100},(_,i)=>({id:i+1,name:'老人'+(i+1)})),total:101}))
 const w=mount(RecordPage,{props:{definition:{title:'申请',endpoint:'/paged',columns:[['name','名称']],fields:[{key:'elderId',label:'老人',type:'select',source:'/elders?size=100'}]}},global:{plugins:[ElementPlus]}})
 await flushPromises();await w.findAll('button').find(b=>b.text()==='新增')!.trigger('click');await flushPromises()
 await w.findAll('.el-select').at(-1)!.trigger('click');await flushPromises()
 const more=[...document.body.querySelectorAll('button')].find(b=>b.textContent?.trim()==='加载更多');expect(more).toBeDefined();(more as HTMLButtonElement).click();await flushPromises();expect(document.body.textContent).toContain('第101位老人');w.unmount()
})
it('数组字典按列筛选并正确分页，查询后总数对应结果',async()=>{
 vi.mocked(http.get).mockResolvedValue(Array.from({length:25},(_,id)=>({id,name:`分类${id}`})))
 const w=mount(RecordPage,{props:{definition:{title:'分类',endpoint:'/dictionary',columns:[['name','名称']]}},global:{plugins:[ElementPlus]}})
 await flushPromises();expect(w.findAll('.el-table__body-wrapper .el-table__row')).toHaveLength(20)
 await w.findAll('button').find(b=>b.text()==='下一页')!.trigger('click');await flushPromises();expect(w.findAll('.el-table__body-wrapper .el-table__row')).toHaveLength(5)
 await w.get('input[aria-label="搜索"]').setValue('不存在');await w.findAll('button').find(b=>b.text()==='查询')!.trigger('click');await flushPromises();expect(w.text()).toContain('共 0 条');w.unmount()
})
it('迟到的旧查询不能覆盖较新的筛选结果',async()=>{
 let resolve!:(v:any)=>void;vi.mocked(http.get).mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockResolvedValue({records:[{id:2,name:'新结果'}],total:1})
 const w=mount(RecordPage,{props:{definition:{title:'列表',endpoint:'/paged',columns:[['name','名称']]}},global:{plugins:[ElementPlus]}})
 await w.get('input[aria-label="搜索"]').setValue('新');await w.findAll('button').find(b=>b.text()==='查询')!.trigger('click');await flushPromises();resolve({records:[{id:1,name:'旧结果'}],total:1});await flushPromises();expect(w.text()).toContain('新结果');expect(w.text()).not.toContain('旧结果');w.unmount()
})
