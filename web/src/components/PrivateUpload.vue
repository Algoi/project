<script setup lang="ts">
import {ref} from 'vue'
import {http} from '../api/http'
const props=defineProps<{modelValue?:number[]|number|string;purpose:string;readonly?:boolean}>(),emit=defineEmits<{(e:'update:modelValue',value:number[]):void}>(),pending=ref(false),error=ref('')
function ids(){return Array.isArray(props.modelValue)?props.modelValue:props.modelValue?[Number(props.modelValue)]:[]}
async function upload(event:Event){const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;pending.value=true;error.value='';try{const body=new FormData();body.append('file',file);const result=await http.post<never,{id:number}>(`/files/upload?purpose=${props.purpose}`,body);emit('update:modelValue',[...ids(),result.id])}catch(e){error.value=(e as Error).message}finally{pending.value=false}}
async function download(id:number){try{const blob=await http.get<never,Blob>(`/files/${id}`,{responseType:'blob'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`附件-${id}`;a.click();URL.revokeObjectURL(url)}catch(e){error.value=(e as Error).message}}
</script>
<template><div><input v-if="!readonly" type="file" :disabled="pending" accept="image/png,image/jpeg,image/webp,application/pdf" aria-label="上传附件" @change="upload"/><p v-if="pending">正在上传…</p><p v-if="error" role="alert">{{error}}</p><div v-for="id in ids()" :key="id" class="toolbar"><el-button link @click="download(id)">查看附件 {{id}}</el-button><el-button v-if="!readonly" link type="danger" @click="emit('update:modelValue',ids().filter(v=>v!==id))">移除</el-button></div></div></template>
