<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { roleHome } from '../../shared/roles'
import communityMeal from '../../assets/imagery/community-meal.png'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const pending = ref(false)
const error = ref('')
async function submit() {
  if (pending.value) return
  if (!form.username.trim() || !form.password) { error.value = '请填写账号和密码'; return }
  pending.value = true
  error.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    form.password = ''
    await router.replace(roleHome(auth.user!.roles))
  } catch (failure) { error.value = (failure as Error).message }
  finally { pending.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="brand"><span class="brand-mark" aria-hidden="true">膳</span> 邻里膳</div>
      <figure class="login-visual">
        <img :src="communityMeal" alt="社区餐厅里，工作人员为长者送上热饭" width="1536" height="1024" fetchpriority="high" />
        <figcaption>
          <p class="eyebrow">社区老年助餐服务</p>
          <h1>一餐热饭，<br />一份邻里关怀。</h1>
          <p class="intro-copy">让家人放心，让长者安心。<br />把每天的餐食，照顾得更周全。</p>
        </figcaption>
      </figure>
    </section>
    <section class="login-card" aria-labelledby="login-heading">
      <p class="eyebrow">欢迎回来</p><h2 id="login-heading">登录助餐服务</h2>
      <p class="muted">请使用社区分配的账号登录。</p>
      <el-alert v-if="route.query.changed" title="密码已修改，请使用新密码重新登录" type="success" :closable="false" />
      <form class="form-stack" @submit.prevent="submit">
        <label for="username">账号</label>
        <el-input id="username" v-model="form.username" autocomplete="username" maxlength="64" placeholder="请输入账号" size="large" :disabled="pending" />
        <label for="password">密码</label>
        <el-input id="password" v-model="form.password" type="password" show-password autocomplete="current-password" maxlength="72" placeholder="请输入密码" size="large" :disabled="pending" />
        <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" show-icon />
        <el-button class="submit-button" native-type="submit" type="primary" size="large" :loading="pending">{{ pending ? '正在登录' : '登录' }}</el-button>
      </form>
      <p class="login-help">忘记账号或密码？请联系社区管理员。</p>
    </section>
    <footer class="login-footer">邻里膳 · 社区老年助餐智能膳食管理系统</footer>
  </main>
</template>

<style scoped>
.login-page{grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:44px;max-width:1220px;padding-top:40px;padding-bottom:90px}
.login-intro .brand{margin-bottom:24px}.login-visual{margin:0;background:var(--brand-soft);border:1px solid var(--border-color);border-radius:22px;overflow:hidden}
.login-visual img{display:block;width:100%;height:300px;object-fit:cover;object-position:50% 38%}.login-visual figcaption{padding:28px 32px}.login-visual .eyebrow{margin-bottom:12px;color:var(--brand-primary)}
.login-intro h1{font-size:34px;line-height:1.55;letter-spacing:.02em;margin-bottom:12px}.intro-copy{font-size:16px;color:var(--text-muted);margin:0}
.login-card{border-color:var(--border-color);box-shadow:0 18px 50px #385b3b0a;padding:38px 34px;background:var(--surface-color)}
@media(max-width:1000px){.login-page{gap:28px;padding-left:24px;padding-right:24px}.login-visual img{height:270px}.login-visual figcaption{padding:24px}.login-intro h1{font-size:30px}.login-card{padding:30px 26px}}
@media(max-width:700px){.login-page{display:block;max-width:480px;padding:24px 20px 76px}.login-intro .brand{margin-bottom:18px}.login-visual img{height:160px;object-position:50% 36%}.login-visual figcaption{padding:18px 22px}.login-intro h1{font-size:24px;line-height:1.5;margin:0}.login-intro h1 br{display:none}.login-visual .intro-copy,.login-visual .eyebrow{display:none}.login-card{margin-top:20px;padding:26px 24px}.login-footer{padding:0 20px;line-height:1.6}}
</style>
