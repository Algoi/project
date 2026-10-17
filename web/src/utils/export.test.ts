import {it,expect,vi} from 'vitest'
import {csvText,exportRows} from './export'
import {http} from '../api/http'
vi.mock('../api/http',()=>({http:{get:vi.fn()}}))
it('CSV 转义逗号引号换行并阻断公式单元格',()=>{
 const csv=csvText(['姓名','内容'],[['张三','=HYPERLINK("x")'],['李四','多行\n含,逗号'],['甲','  @SUM(1)']])
 expect(csv).toContain("'=");expect(csv).toContain("'  @SUM");expect(csv).toContain('"多行\n含,逗号"');expect(csv).toContain('""x""');expect(csv.startsWith('\uFEFF')).toBe(true)
})
it('导出全部匹配分页，保留原筛选和服务端权限，不局限当前页',async()=>{
 vi.mocked(http.get).mockResolvedValueOnce({records:Array.from({length:100},(_,id)=>({id})),total:101}).mockResolvedValueOnce({records:[{id:100}],total:101})
 const rows=await exportRows('/orders?status=PAID',{keyword:'测试'})
 expect(rows).toHaveLength(101);expect(http.get).toHaveBeenLastCalledWith('/orders?status=PAID',{params:{keyword:'测试',page:2,size:100}})
})
