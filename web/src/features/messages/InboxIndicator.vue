<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,watch} from 'vue';import {useRoute} from 'vue-router';import {http} from '../../api/http'
const count=ref<number|null>(null),route=useRoute();let timer:ReturnType<typeof setInterval>
async function load(){try{count.value=(await http.get<never,{count:number}>('/messages/unread-count')).count}catch{count.value=null}}
onMounted(()=>{void load();timer=setInterval(load,60000);window.addEventListener('meal:messages-updated',load)})
onBeforeUnmount(()=>{clearInterval(timer);window.removeEventListener('meal:messages-updated',load)});watch(()=>route.fullPath,load)
</script><template><router-link to="/messages" class="text-link">消息<span v-if="count!==null">（{{count}} 未读）</span></router-link></template>