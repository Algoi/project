import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/sites',component:()=>import('./SitesView.vue'),meta:{role:'ADMIN'}},
]
