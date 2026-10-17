<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import {http} from '../api/http'
import type {Row} from '../shared/records'
const props=defineProps<{modelValue?:any;source:string;label:string;multiple?:boolean;id?:string}>()
const emit=defineEmits<{(e:'update:modelValue',value:any):void}>()
const items=ref<Row[]>([]),cache=ref<Row[]>([]),page=ref(1),total=ref(0),query=ref(''),pending=ref(false),error=ref('')
let generation=0
const value=(row:Row)=>row.id??row.code
const name=(row:Row)=>row.name??row.realName??row.username??row.code??String(row.id)
const selected=computed(()=>Array.isArray(props.modelValue)?props.modelValue:[props.modelValue])
const options=computed(()=>[...cache.value.filter(row=>selected.value.includes(value(row))&&!items.value.some(item=>value(item)===value(row))),...items.value])
async function load(reset=false){const request=++generation;if(reset){page.value=1;items.value=[]}pending.value=true;error.value='';try{const data=await http.get<never,any>(props.source.split('?')[0],{params:{...Object.fromEntries(new URLSearchParams(props.source.split('?')[1])),page:page.value,size:20,keyword:query.value}});if(request!==generation)return;let rows:Row[];if(Array.isArray(data)){const filtered=data.filter(row=>String(name(row)).toLowerCase().includes(query.value.toLowerCase()));total.value=filtered.length;rows=filtered.slice((page.value-1)*20,page.value*20)}else{total.value=data.total;rows=data.records}items.value=reset?rows:[...items.value,...rows];cache.value=[...new Map([...cache.value,...rows].map(row=>[value(row),row])).values()]}catch(e){if(request===generation)error.value=(e as Error).message}finally{if(request===generation)pending.value=false}}
function search(text:string){query.value=text;void load(true)}
watch(()=>props.source,async()=>{query.value='';cache.value=[];await load(true);const base=props.source.split('?')[0];if(['/users','/sites','/elders','/dishes'].includes(base!)){for(const id of selected.value.filter(id=>id!=null&&id!==''&&!cache.value.some(row=>value(row)===id))){try{const row=await http.get<never,Row>(`${base}/${id}`);cache.value.push(row)}catch{/* The current role may no longer access an old selection. */}}}},{immediate:true})
</script>
<template><div><el-select :id="id" :model-value="modelValue" :multiple="multiple" :aria-label="label" filterable remote :remote-method="search" :loading="pending" clearable @update:model-value="emit('update:modelValue',$event)"><el-option v-for="row in options" :key="value(row)" :value="value(row)" :label="name(row)"/><template #footer><el-button v-if="items.length<total" :loading="pending" @click.stop="page++;load()">加载更多</el-button><span v-else class="muted">共 {{total}} 项</span></template></el-select><p v-if="error" role="alert">{{error}} <el-button link @click="load(true)">重试</el-button></p></div></template>
