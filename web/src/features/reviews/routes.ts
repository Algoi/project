import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'canteen/reviews',component:()=>import('./ReviewsView.vue'),meta:{role:'CANTEEN'}},
{path:'family/reviews',component:()=>import('./ReviewsView.vue'),meta:{role:'FAMILY'}},
]
