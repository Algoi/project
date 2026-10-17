import {it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import WalletView from './WalletView.vue'
import {http} from '../../api/http'
vi.mock('../../api/http',()=>({http:{get:vi.fn(),post:vi.fn()}}))
it('充值确认网络失败后沿用同一请求标识，成功后下一次充值使用新标识',async()=>{
 vi.mocked(http.get).mockImplementation((path:any)=>Promise.resolve(path==='/wallet'?{canTopup:true,balance:'0.00'}:{records:[],total:0}))
 let confirmations=0
 vi.mocked(http.post).mockImplementation((path:any)=>path==='/wallet/topups'?Promise.resolve({id:1}):++confirmations===1?Promise.reject(new Error('连接中断')):Promise.resolve({balance:'100.00'}))
 const w=mount(WalletView,{global:{plugins:[ElementPlus],stubs:{WalletSummary:true,RouterLink:true}}});await flushPromises()
 await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('连接中断')
 await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('演示充值成功')
 const requests=()=>vi.mocked(http.post).mock.calls.filter(call=>call[0]==='/wallet/topups').map(call=>(call[1] as any).requestId)
 expect(requests()[1]).toBe(requests()[0]);await w.get('form').trigger('submit');await flushPromises();expect(requests()[2]).not.toBe(requests()[0]);w.unmount()
})
