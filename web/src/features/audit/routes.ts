import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/audit',component:()=>import('./AuditView.vue'),meta:{role:'ADMIN'}},
]
