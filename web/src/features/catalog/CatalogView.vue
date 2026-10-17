<script setup lang="ts">
import {computed,ref,reactive} from 'vue'
import RecordPage from '../../components/RecordPage.vue'
import type {Definition,Row} from '../../shared/records'
import {http} from '../../api/http'
const tab=ref('dishes'),kind=computed(()=>tab.value)
const definition=computed<Definition>(()=>{
 if(kind.value==='categories'||kind.value==='allergens')return{title:kind.value==='categories'?'菜品分类':'过敏原字典',endpoint:kind.value==='categories'?'/dish-categories':'/allergens',detail:'',columns:[['name','名称']],fields:[{key:'name',label:'名称'}]}
 return{title:'菜品与价格',endpoint:'/dishes',save:async(body,id)=>{if(Array.isArray(body.imageFileId))body.imageFileId=body.imageFileId[0]??null;return id?http.put(`/dishes/${id}`,body):http.post('/dishes',body)},columns:[['name','菜品'],['categoryName','分类'],['originalPrice','原价'],['elderPrice','补贴价'],['status','状态']],fields:[{key:'name',label:'菜品名称'},{key:'categoryId',label:'菜品分类',type:'select',source:'/dish-categories'},{key:'description',label:'菜品说明',type:'textarea'},{key:'imageFileId',label:'菜品图片',type:'files',purpose:'DISH_IMAGE'},{key:'ingredients',label:'主要食材（逗号分隔）',type:'tags'},...(['weightGrams','originalPrice','elderPrice','caloriesKcal','proteinGrams','fatGrams','carbGrams','sodiumMg'].map((key,i)=>({key,label:['份重 g','社会原价 元','老人补贴价 元','能量 kcal','蛋白质 g','脂肪 g','碳水 g','钠 mg'][i]!,type:'number'}))),{key:'allergenIds',label:'过敏原',type:'multi',source:'/allergens'},{key:'allergenDataComplete',label:'食材与过敏原资料已核对完整',type:'check'}]}
})

</script>
<template><div><div class="toolbar"><el-button v-for="(label,key) in {dishes:'菜品',categories:'分类',allergens:'过敏原'}" :key="key" @click="tab=String(key)">{{label}}</el-button></div><RecordPage :definition="definition"><template #actions="{row,action}"><el-button v-if="kind==='dishes'" link @click="action('PATCH',`/dishes/${row.id}/status`,{status:row.status==='ACTIVE'?'DISABLED':'ACTIVE'})">{{row.status==='ACTIVE'?'停用':'启用'}}</el-button></template></RecordPage></div></template>
