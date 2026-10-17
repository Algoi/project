import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'canteen/menus',component:()=>import('./MenuView.vue'),meta:{role:'CANTEEN'}},
{path:'community/menus',component:()=>import('./MenuView.vue'),meta:{role:'COMMUNITY'}},
{path:'canteen/forecasts',component:()=>import('./MenuView.vue'),meta:{role:'CANTEEN'}},
{path:'canteen/preparation',component:()=>import('./MenuView.vue'),meta:{role:'CANTEEN'}},
]
