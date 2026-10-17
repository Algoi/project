import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[
{path:'admin/users',component:()=>import('./UsersView.vue'),meta:{role:'ADMIN'}},
{path:'account',component:()=>import('./AccountView.vue')},
{path:'family/profile',redirect:'/account',meta:{role:'FAMILY'}},
]
