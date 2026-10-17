import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/notices',component:()=>import('./NoticesView.vue'),meta:{role:'ADMIN'}},
{path:'canteen/notices',component:()=>import('./NoticesView.vue'),meta:{role:'CANTEEN'}},
{path:'family/notices',component:()=>import('./NoticesView.vue'),meta:{role:'FAMILY'}},
{path:'community/notices',component:()=>import('./NoticesView.vue'),meta:{role:'COMMUNITY'}},
]
