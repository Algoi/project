import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/dishes',component:()=>import('./CatalogView.vue'),meta:{role:'ADMIN'}},
]
