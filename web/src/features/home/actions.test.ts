import {expect,it} from 'vitest'
import {homeActions} from './actions'
it('首页快捷入口只展示当前角色且未被禁用的菜单',()=>{
 const actions=homeActions('FAMILY',['menu:/family/order/new'])
 expect(actions.length).toBeGreaterThan(0)
 expect(actions.every(action=>action.path.startsWith('/family/'))).toBe(true)
 expect(actions.some(action=>action.path==='/family/order/new')).toBe(false)
 expect(homeActions('UNKNOWN',[])).toEqual([])
})
