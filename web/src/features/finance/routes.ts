import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
 {path:'family/wallet',component:()=>import('./WalletView.vue'),meta:{role:'FAMILY'}},
 {path:'family/payments',component:()=>import('./BillingView.vue'),meta:{role:'FAMILY'}},
]
