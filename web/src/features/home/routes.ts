import type {RouteRecordRaw} from 'vue-router'
import {roles} from '../../shared/roles'
export const routes:RouteRecordRaw[]=[{path:'',component:()=>import('./HomeView.vue'),meta:{home:true}},...roles.map(role=>({path:role.path.slice(1),component:()=>import('./HomeView.vue'),meta:{role:role.code}}))]
