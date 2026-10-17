import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'family/order/new',component:()=>import('./OrderNew.vue'),meta:{role:'FAMILY'}},
{path:'community/proxy-orders',component:()=>import('./OrderNew.vue'),meta:{role:'COMMUNITY'}},
{path:'family/orders',component:()=>import('./OrdersView.vue'),meta:{role:'FAMILY'}},
{path:'community/orders',component:()=>import('./OrdersView.vue'),meta:{role:'COMMUNITY'}},
{path:'canteen/orders',component:()=>import('./OrdersView.vue'),meta:{role:'CANTEEN'}},
{path:'canteen/redemptions',component:()=>import('./OperationsView.vue'),meta:{role:'CANTEEN'}},
{path:'community/redemptions',component:()=>import('./OperationsView.vue'),meta:{role:'COMMUNITY'}},
{path:'canteen/delivery',component:()=>import('./OperationsView.vue'),meta:{role:'CANTEEN'}},
{path:'community/delivery',component:()=>import('./OperationsView.vue'),meta:{role:'COMMUNITY'}},
{path:'community/refunds',component:()=>import('./OperationsView.vue'),meta:{role:'COMMUNITY'}},
]
