import type {RouteRecordRaw} from 'vue-router'
export const routes:RouteRecordRaw[]=[{path:'messages',component:()=>import('./MessagesView.vue')}]
