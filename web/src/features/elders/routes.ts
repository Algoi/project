import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'family/elders',component:()=>import('./ElderView.vue'),meta:{role:'FAMILY'}},
{path:'community/elders',component:()=>import('./ElderView.vue'),meta:{role:'COMMUNITY'}},
{path:'community/subsidies',component:()=>import('./ElderView.vue'),meta:{role:'COMMUNITY'}},
]
