import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElAlert, ElButton, ElCheckbox, ElCollapse, ElCollapseItem, ElDialog, ElEmpty, ElInput, ElOption, ElRadio, ElRadioGroup, ElRate, ElSelect, ElSwitch, ElTable, ElTableColumn, ElLoading } from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import { createAppRouter } from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
app.use(createPinia())
const router = createAppRouter()
app.use(router)
for(const [name,component]of Object.entries({ElAlert,ElButton,ElCheckbox,ElCollapse,ElCollapseItem,ElDialog,ElEmpty,ElInput,ElOption,ElRadio,ElRadioGroup,ElRate,ElSelect,ElSwitch,ElTable,ElTableColumn}))app.component(name,component)
app.use(ElLoading)
window.addEventListener('meal:unauthenticated', () => {
  useAuthStore().clear()
  if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})
app.mount('#app')
