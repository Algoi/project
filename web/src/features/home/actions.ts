export interface HomeAction {path:string;label:string;description:string;mark:string}
const actions:Record<string,HomeAction[]>={
 FAMILY:[
  {path:'/family/order/new',label:'为家人订餐',description:'为已绑定的家人挑选合适餐品',mark:'餐'},
  {path:'/family/orders',label:'我的订单',description:'查看订单、支付与取餐状态',mark:'单'},
  {path:'/family/elders',label:'老人绑定与补贴',description:'管理家属绑定，办理补贴申请',mark:'家'},
 ],
 CANTEEN:[
  {path:'/canteen/menus',label:'菜谱上报',description:'安排每日菜谱，核对餐品信息',mark:'餐'},
  {path:'/canteen/preparation',label:'备餐出餐清单',description:'查看已确认份数与备餐参考',mark:'备'},
  {path:'/canteen/orders',label:'订单出餐',description:'核对网点订单，更新出餐状态',mark:'单'},
 ],
 COMMUNITY:[
  {path:'/community/elders',label:'老人档案与绑定',description:'维护老人档案，核对家属关系',mark:'档'},
  {path:'/community/proxy-orders',label:'协助老人订餐',description:'按已登记饮食要求协助订餐',mark:'餐'},
  {path:'/community/subsidies',label:'补贴资格审核',description:'核对申请材料与补贴资格',mark:'审'},
 ],
 ADMIN:[
  {path:'/admin/users',label:'用户与权限',description:'维护账号、角色与操作权限',mark:'人'},
  {path:'/admin/sites',label:'服务网点',description:'管理服务网点与营业安排',mark:'点'},
  {path:'/admin/dashboard',label:'运营数据看板',description:'查看订单、就餐与运营统计',mark:'数'},
 ],
}
export function homeActions(role:string,denied:readonly string[]=[]){
 return (actions[role]??[]).filter(action=>!denied.includes(`menu:${action.path}`))
}
