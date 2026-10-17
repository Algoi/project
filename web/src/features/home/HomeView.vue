<script setup lang="ts">
import WorkbenchPanel from './WorkbenchPanel.vue'
import {WalletSummary} from '../finance'
import {computed,ref,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {roles} from '../../shared/roles'
import {http} from '../../api/http'
import communityMeal from '../../assets/imagery/community-meal.png'
import {homeActions} from './actions'
const notices=ref<Record<string,any>[]>([]),noticeError=ref(''),noticePending=ref(false)
async function loadNotices(){noticePending.value=true;noticeError.value='';try{notices.value=(await http.get<never,any>('/notices?size=3')).records}catch(e){noticeError.value=(e as Error).message}finally{noticePending.value=false}}
onMounted(loadNotices)
const auth=useAuthStore(),route=useRoute()
const role=computed(()=>String(route.meta.role??auth.activeRole??''))
const roleName=computed(()=>roles.find(item=>item.code===role.value)?.name)
const site=computed(()=>auth.user?.sites.find(item=>item.id===auth.currentSiteId))
const actions=computed(()=>homeActions(role.value,auth.user?.deniedPermissions))
</script>
<template>
 <div class="home-page">
  <p class="eyebrow">{{roleName}} · 我的首页</p>
  <section class="home-hero">
   <div class="home-intro">
    <span class="home-kicker">邻里有温度 · 助餐有照顾</span>
    <h1>您好，{{auth.user?.realName||auth.user?.username}}</h1>
    <h2>把每一餐，照顾好。</h2>
    <p>一份热饭，一声问候。<br/>让家人的每一天，多一份安心。</p>
    <router-link v-if="actions[0]" class="home-primary" :to="actions[0].path" :aria-label="`快捷进入${actions[0].label}`">{{actions[0].label}} <span aria-hidden="true">→</span></router-link>
   </div>
   <figure class="home-photo"><img :src="communityMeal" alt="社区工作人员为长者送上热饭" width="1536" height="1024" fetchpriority="high"/></figure>
  </section>
  <WalletSummary v-if="role==='FAMILY'"/><WorkbenchPanel :role="role"/><section v-if="actions.length" class="home-services" aria-labelledby="home-services-title">
   <div class="home-section-heading"><h2 id="home-services-title">常用服务</h2><p>从这里开始，办好每一件小事。</p></div>
   <div class="home-actions">
    <router-link v-for="action in actions" :key="action.path" :to="action.path" class="home-action">
     <span class="home-action-mark" aria-hidden="true">{{action.mark}}</span>
     <div><h3>{{action.label}}</h3><p>{{action.description}}</p></div>
     <span class="home-action-arrow" aria-hidden="true">↗</span>
    </router-link>
   </div>
  </section>
  <section class="home-notices content-card" aria-labelledby="home-notices-title">
   <div class="home-section-heading"><h2 id="home-notices-title">社区公告</h2><router-link class="text-link" :to="`/${role.toLowerCase()}/notices`">查看全部 →</router-link></div>
   <p v-if="noticePending" role="status">正在加载公告…</p><div v-else-if="noticeError"><el-alert :title="noticeError" type="error" :closable="false"/><el-button @click="loadNotices">重试加载公告</el-button></div>
   <ul v-else-if="notices.length"><li v-for="notice in notices" :key="notice.id"><router-link :to="`/${role.toLowerCase()}/notices`">{{notice.title}} <span aria-hidden="true">→</span></router-link></li></ul>
   <p v-else class="muted home-empty-notice">当前暂无公告，新的服务通知会在这里展示。</p>
  </section>
  <div class="info-grid home-details">
   <section class="content-card"><p class="eyebrow">个人信息</p><h2>您的账号</h2>
    <dl class="details"><dt>登录账号</dt><dd>{{auth.user?.username}}</dd><dt>联系电话</dt><dd>{{auth.user?.phoneMasked||'尚未填写'}}</dd><dt>当前身份</dt><dd>{{roleName}}</dd></dl>
    <router-link class="text-link" to="/account">管理个人信息与密码 →</router-link>
   </section>
   <section v-if="site" class="content-card"><p class="eyebrow">当前服务网点</p><h2>{{site.name}}</h2>
    <dl class="details"><dt>服务地址</dt><dd>{{site.address||'尚未填写'}}</dd><dt>服务电话</dt><dd>{{site.phone||'尚未填写'}}</dd><dt>营业状态</dt><dd>{{site.businessStatus==='OPEN'?'营业中':'暂停营业'}}</dd></dl>
   </section>
   <section v-else class="content-card"><p class="eyebrow">联系与帮助</p><h2>我们在您身边</h2><p class="muted">账号信息需要调整，或使用中遇到问题，请联系所属社区工作人员。</p></section>
  </div>
 </div>
</template>
<style scoped>
.home-hero{display:grid;grid-template-columns:1fr 1fr;background:var(--brand-soft);border:1px solid var(--border-color);border-radius:22px;overflow:hidden;margin-bottom:32px}
.home-intro{padding:34px 36px}.home-kicker{font-size:13px;color:var(--brand-primary);letter-spacing:.08em}
.home-intro h1{font-size:28px;margin:18px 0 10px;overflow-wrap:anywhere}.home-intro h2{font-size:24px;margin:0;font-weight:500}.home-intro p{color:var(--text-muted);margin:16px 0 22px}
.home-primary{display:inline-flex;align-items:center;justify-content:space-between;gap:28px;background:var(--brand-primary);color:white;padding:12px 20px;border-radius:9px;font-size:16px;font-weight:600}.home-primary:hover{background:var(--brand-deep)}
.home-photo{margin:0;position:relative;min-height:330px}.home-photo img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;object-position:50% 45%}
.home-section-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px}.home-section-heading h2{font-size:21px;margin:0}.home-section-heading p{font-size:14px;color:var(--text-muted);margin:0}
.home-services{margin-bottom:30px}.home-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.home-action{display:flex;gap:14px;align-items:flex-start;background:var(--surface-color);border:1px solid var(--border-color);border-radius:14px;padding:22px 18px;transition:background .15s,border-color .15s}
.home-action:hover{background:var(--brand-pale);border-color:var(--brand-mid)}.home-action-mark{flex-shrink:0;display:grid;place-items:center;width:40px;height:40px;background:var(--brand-soft);border-radius:11px;color:var(--brand-primary);font-size:19px}.home-action h3{margin:1px 0 8px;font-size:17px;font-weight:600}.home-action p{font-size:14px;color:var(--text-muted);line-height:1.65;margin:0}.home-action-arrow{margin-left:auto;color:var(--brand-primary)}
.home-notices{margin-bottom:24px}.home-notices ul{list-style:none;margin:0;padding:0}.home-notices li+li{border-top:1px solid var(--border-color)}.home-notices li a{display:flex;justify-content:space-between;gap:18px;padding:13px 0;line-height:1.7;overflow-wrap:anywhere}.home-empty-notice{margin-bottom:0}
@media(max-width:1100px){.home-intro{padding:28px}.home-action{padding:18px 14px;gap:10px}.home-action-mark{display:none}}
@media(max-width:700px){.home-hero{grid-template-columns:1fr;margin-bottom:26px}.home-intro{padding:26px}.home-intro h1{font-size:25px}.home-intro h2{font-size:22px}.home-intro p{margin:12px 0 18px}.home-photo{min-height:210px}.home-actions{grid-template-columns:1fr;gap:12px}.home-action{padding:18px}.home-action-mark{display:grid}.home-section-heading{align-items:flex-start;flex-wrap:wrap;gap:8px}.home-section-heading p{font-size:14px}.home-details{margin-top:20px}}
</style>
