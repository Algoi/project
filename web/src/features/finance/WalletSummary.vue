<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {http} from '../../api/http'
defineProps<{hideLink?:boolean}>()
const balance=ref<string|null>(null),error=ref('')
async function load(){error.value='';try{balance.value=(await http.get<never,{balance:string}>('/wallet')).balance}catch(e){error.value=(e as Error).message}}
onMounted(()=>{void load();window.addEventListener('meal:wallet-updated',load)})
onBeforeUnmount(()=>window.removeEventListener('meal:wallet-updated',load))
</script>
<template><section class="content-card wallet-summary"><div><p class="muted">我的钱包余额</p><h2 data-testid="wallet-balance">{{balance==null?(error?'暂不可用':'正在读取…'):'¥'+balance}}</h2><p v-if="error" role="alert">{{error}} <el-button link @click="load">重试</el-button></p></div><router-link v-if="!hideLink" class="text-link" to="/family/wallet">充值与资金流水 →</router-link></section></template>
<style scoped>.wallet-summary{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}.wallet-summary p{margin:0 0 8px}.wallet-summary h2{margin:0}@media(max-width:700px){.wallet-summary{align-items:flex-start;flex-direction:column}}</style>
