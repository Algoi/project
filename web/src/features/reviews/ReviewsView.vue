<script setup lang="ts">
import {computed,ref,reactive} from 'vue'
import RecordPage from '../../components/RecordPage.vue'
import type {Definition,Row} from '../../shared/records'
import {http} from '../../api/http'
import {useRoute,useRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import PrivateUpload from '../../components/PrivateUpload.vue'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),admin=computed(()=>route.path.startsWith('/admin')),family=computed(()=>route.path.startsWith('/family')),page=ref<InstanceType<typeof RecordPage>>(),detail=ref<Row|null>(null),error=ref(''),pending=ref(false)
const rating=ref('')
const definition=computed<Definition>(()=>({title:family.value?'我的菜品评价':'网点菜品评价',endpoint:(family.value?'/reviews':`/sites/${auth.currentSiteId??0}/reviews`)+(rating.value?`?maxRating=${rating.value}`:''),columns:[['dishName','菜品'],['mealDate','用餐日期'],['rating','评分'],['content','评价内容'],['createdAt','评价时间']]}))

</script>
<template><section><RecordPage :definition="definition"><template #tools><el-select v-model="rating" aria-label="评价筛选"><el-option label="全部评价" value=""/><el-option label="差评（两星及以下）" value="2"/></el-select><el-button v-if="family" @click="router.push('/family/orders')">从已完成订单评价</el-button></template><template #actions="{row}"><el-button link type="primary" @click="detail=row">查看详情</el-button></template></RecordPage><el-dialog :model-value="!!detail" title="菜品评价" width="min(700px,94vw)" @close="detail=null"><template v-if="detail"><p>{{detail.dishName}} · {{detail.rating}} 星</p><p style="white-space:pre-wrap;overflow-wrap:anywhere">{{detail.content}}</p><PrivateUpload v-if="detail.imageFileIds?.length" :model-value="detail.imageFileIds" purpose="REVIEW" readonly/></template></el-dialog></section></template>
