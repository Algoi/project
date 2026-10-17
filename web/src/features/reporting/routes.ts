import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/dashboard',component:()=>import('./ReportsView.vue'),meta:{role:'ADMIN'}},
{path:'family/nutrition',component:()=>import('./ReportsView.vue'),meta:{role:'FAMILY'}},
{path:'community/nutrition',component:()=>import('./ReportsView.vue'),meta:{role:'COMMUNITY'}},
]
