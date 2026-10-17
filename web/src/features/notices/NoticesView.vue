<script setup lang="ts">
import {computed,ref,reactive} from 'vue'
import RecordPage from '../../components/RecordPage.vue'
import type {Definition,Row} from '../../shared/records'
import {http} from '../../api/http'
import {useRoute,useRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import PrivateUpload from '../../components/PrivateUpload.vue'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),admin=computed(()=>route.path.startsWith('/admin')),family=computed(()=>route.path.startsWith('/family')),page=ref<InstanceType<typeof RecordPage>>(),detail=ref<Row|null>(null),error=ref(''),pending=ref(false)
const definition=computed<Definition>(()=>({title:'公告与重要通知',endpoint:'/notices',columns:[['title','标题'],['type','类别'],['publishAt','发布时间'],['status','状态']],...(admin.value?{fields:[{key:'title',label:'公告标题'},{key:'type',label:'公告类别',type:'select',initial:'GENERAL',options:[{value:'GENERAL',label:'一般公告'},{value:'CLOSURE',label:'停餐通知'},{value:'ACTIVITY',label:'活动通知'},{value:'IMPORTANT',label:'重要公告'}]},{key:'content',label:'公告正文',type:'textarea'},{key:'expireAt',label:'失效时间（可选）',type:'datetime-local'},{key:'receiveRoles',label:'接收角色（不选为全部）',type:'multi',options:[{value:'ADMIN',label:'管理员'},{value:'CANTEEN',label:'食堂工作人员'},{value:'FAMILY',label:'老年人家属'},{value:'COMMUNITY',label:'社区操作员'}]},{key:'siteIds',label:'接收网点（不选为全部）',type:'multi',source:'/sites?size=100'}]}:{})}))
async function open(row:Row){try{error.value='';detail.value=await http.get(`/notices/${row.id}`)}catch(e){error.value=(e as Error).message}}
</script>
<template><section><el-alert v-if="error" :title="error" type="error" :closable="false"/><RecordPage :definition="definition"><template #actions="{row,action}"><el-button link type="primary" @click="open(row)">查看详情</el-button><el-button v-if="admin" link @click="action('POST',`/notices/${row.id}/${row.status==='PUBLISHED'?'revoke':'publish'}`,{})">{{row.status==='PUBLISHED'?'撤回':'发布'}}</el-button></template></RecordPage><el-dialog :model-value="!!detail" title="公告详情" width="min(700px,94vw)" @close="detail=null"><template v-if="detail"><h2>{{detail.title}}</h2><p style="white-space:pre-wrap;overflow-wrap:anywhere">{{detail.content}}</p></template></el-dialog></section></template>
