import {it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import HomeView from './HomeView.vue'
import {http} from '../../api/http'
vi.mock('../../api/http',()=>({http:{get:vi.fn()}}))
vi.mock('vue-router',()=>({useRoute:()=>({meta:{role:'FAMILY'}})}))
vi.mock('../../stores/auth',()=>({useAuthStore:()=>({user:{username:'family',sites:[]},activeRole:'FAMILY'})}))
it('公告加载失败有重试入口，不伪装成没有公告',async()=>{
 vi.mocked(http.get).mockRejectedValueOnce(new Error('公告服务不可用')).mockResolvedValue({records:[{id:1,title:'已恢复公告'}]})
 const w=mount(HomeView,{global:{plugins:[ElementPlus],stubs:{WorkbenchPanel:true,WalletSummary:true,RouterLink:{template:'<a><slot/></a>'}}}})
 await flushPromises();expect(w.text()).toContain('公告服务不可用');expect(w.text()).not.toContain('当前暂无公告')
 await w.findAll('button').find(b=>b.text()==='重试加载公告')!.trigger('click');await flushPromises();expect(w.text()).toContain('已恢复公告');w.unmount()
})
