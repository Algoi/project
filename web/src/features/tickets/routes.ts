import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/tickets',component:()=>import('./TicketsView.vue'),meta:{role:'ADMIN'}},
{path:'family/tickets',component:()=>import('./TicketsView.vue'),meta:{role:'FAMILY'}},
{path:'community/tickets',component:()=>import('./TicketsView.vue'),meta:{role:'COMMUNITY'}},
]
