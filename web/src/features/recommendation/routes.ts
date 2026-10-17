import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/recommendation-settings',component:()=>import('./RecommendationSettings.vue'),meta:{role:'ADMIN'}},
]
