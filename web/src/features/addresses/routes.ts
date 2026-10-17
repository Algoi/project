import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[{path:'family/addresses',component:()=>import('./AddressesView.vue'),meta:{role:'FAMILY'}}]
