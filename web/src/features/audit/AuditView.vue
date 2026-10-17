<script setup lang="ts">
import {computed,ref,reactive} from 'vue'
import RecordPage from '../../components/RecordPage.vue'
import type {Definition,Row} from '../../shared/records'
import {http} from '../../api/http'
const dateFrom=ref(''),dateTo=ref('')
const definition=computed<Definition>(()=>({title:'操作审计',endpoint:'/audit-logs?'+new URLSearchParams({dateFrom:dateFrom.value,dateTo:dateTo.value}).toString(),search:'action',columns:[['createdAt','时间'],['operatorName','操作人'],['action','行为'],['resourceType','对象'],['resourceId','编号'],['summary','摘要']]}))
</script>
<template><RecordPage :definition="definition"><template #intro><div class="toolbar"><label for="audit-from">开始日期</label><el-input id="audit-from" v-model="dateFrom" type="date"/><label for="audit-to">结束日期</label><el-input id="audit-to" v-model="dateTo" type="date"/><span>按行为代码搜索，例如 MENU_PUBLISH、REFUND_APPROVE</span></div></template></RecordPage></template>
