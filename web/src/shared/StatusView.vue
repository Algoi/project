<script setup lang="ts">
import { useRouter } from 'vue-router'
defineProps<{ kind: string }>()
const router = useRouter()
</script>
<template>
  <main class="status-page"><section class="content-card">
    <p class="eyebrow">邻里膳</p>
    <template v-if="kind === 'forbidden'"><h1>暂时无法访问此页面</h1><p>您的账号没有此页面的访问权限。</p></template>
    <template v-else-if="kind === 'unavailable'"><h1>服务暂时连接不上</h1><p>您的会话已保留，请稍后重试。</p></template>
    <template v-else><h1>没有找到这个页面</h1><p>请检查地址，或返回首页继续使用。</p></template>
    <el-button type="primary" @click="router.push('/')">{{ kind === 'unavailable' ? '重试连接' : '返回首页' }}</el-button>
    <el-button @click="router.push('/login')">返回登录</el-button>
  </section></main>
</template>
