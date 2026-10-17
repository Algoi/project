<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { roles, roleHome } from '../router'
import { navigation } from '../navigation'
import {InboxIndicator} from '../features/messages'
import { http } from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const error = ref('')
const pending = ref(false)
const largeText = ref(sessionStorage.getItem('meal.largeText') === 'true')
const availableRoles = computed(() => roles.filter(role => auth.user?.roles.includes(role.code)))
const activeRole = computed(() => availableRoles.value.find(role => role.code === (route.meta.role ?? auth.activeRole)) ?? availableRoles.value[0])
async function logout() {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try { await auth.logout(); await router.replace('/login') }
  catch (failure) { error.value = (failure as Error).message }
  finally { pending.value = false }
}
function updateText(event:Event){largeText.value=!!(event as CustomEvent).detail}
onMounted(async()=>{window.addEventListener('meal:text-size',updateText);try{const prefs=await http.get<never,{largeText?:boolean}|null>('/auth/preferences');largeText.value=!!prefs?.largeText;sessionStorage.setItem('meal.largeText',String(largeText.value))}catch(e){error.value=(e as Error).message}})
onBeforeUnmount(()=>window.removeEventListener('meal:text-size',updateText))
async function changeText() { sessionStorage.setItem('meal.largeText', String(largeText.value));try{const prefs=await http.get<never,Record<string,unknown>|null>('/auth/preferences');await http.put('/auth/preferences',{...prefs,largeText:largeText.value})}catch(e){error.value=(e as Error).message} }
function switchRole(path: string) { void router.push(path) }
</script>

<template>
  <div class="app-shell" :class="{ 'large-text': largeText, 'family-shell': activeRole?.code === 'FAMILY' }">
    <header class="app-header">
      <router-link class="brand" to="/"><span class="brand-mark" aria-hidden="true">膳</span> 邻里膳</router-link>
      <div class="header-actions"><InboxIndicator/>
        <el-select v-if="availableRoles.length > 1" :model-value="activeRole?.path" aria-label="切换角色" @change="switchRole">
          <el-option v-for="role in availableRoles" :key="role.code" :value="role.path" :label="role.name" />
        </el-select>
        <span v-else class="role-chip">{{ activeRole?.name }}</span>
        <el-select v-if="auth.user?.sites.length" v-model="auth.currentSiteId" aria-label="当前服务网点">
          <el-option v-for="site in auth.user.sites" :key="site.id" :value="site.id" :label="site.name" />
        </el-select>
        <el-switch v-model="largeText" active-text="大字" aria-label="大字模式" @change="changeText" />
        <el-button :loading="pending" @click="logout">退出登录</el-button>
      </div>
    </header>
    <div class="app-body">
      <nav class="side-nav" aria-label="主要导航">
        <p class="nav-label">服务空间</p>
        <router-link :to="roleHome(activeRole ? [activeRole.code] : [])" exact-active-class="nav-active">我的首页 <span aria-hidden="true">↗</span></router-link>
        <router-link v-for="item in (navigation[activeRole?.code??'']??[]).filter(item=>!auth.user?.deniedPermissions?.includes('menu:'+item.path))" :key="item.path" :to="item.path" active-class="nav-active">{{item.label}}</router-link>
        <router-link to="/messages" active-class="nav-active">消息中心</router-link>
        <router-link to="/account" active-class="nav-active">账号设置 <span aria-hidden="true">↗</span></router-link>
        <div class="nav-footer">社区有温度<br />助餐有照顾</div>
      </nav>
      <main class="workspace">
        <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
        <router-view />
      </main>
    </div>
  </div>
</template>
