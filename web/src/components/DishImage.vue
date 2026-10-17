<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {http} from '../api/http'
import {dishIllustration} from '../shared/visuals'
const props=defineProps<{fileId?:number|null;name:string}>()
const url=ref(''),illustration=computed(()=>dishIllustration(props.name))
let generation=0
function release(){if(url.value)URL.revokeObjectURL(url.value);url.value=''}
watch(()=>props.fileId,async id=>{
 const request=++generation
 release()
 if(!id)return
 try{
  const blob=await http.get<never,Blob>(`/files/${id}`,{responseType:'blob'})
  if(request===generation)url.value=URL.createObjectURL(blob)
 }catch{/* Keep a clearly labeled illustration when a private image is unavailable. */}
},{immediate:true})
onBeforeUnmount(()=>{generation++;release()})
</script>
<template>
 <figure class="dish-visual">
  <img :src="url||illustration.src" :alt="url?name:illustration.alt" width="1448" height="1086" loading="lazy" decoding="async" @error="release"/>
  <figcaption v-if="!url">{{illustration.caption}}</figcaption>
 </figure>
</template>
<style scoped>
.dish-visual{margin:0 0 18px}
.dish-visual img{display:block;width:100%;height:220px;object-fit:cover;border-radius:12px;background:var(--brand-soft)}
.dish-visual figcaption{margin-top:8px;color:var(--text-muted);font-size:14px;line-height:1.6}
@media(max-width:700px){.dish-visual img{height:200px}}
</style>
