<script setup lang="ts">
import {WalletSummary} from '../finance'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../../api/auth'
import AccountPreferences from '../../components/AccountPreferences.vue'
import { useAuthStore } from '../../stores/auth'
const auth = useAuthStore()
const router = useRouter()
const profile = reactive({ realName: auth.user?.realName ?? '', phone: '' })
const password = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const profilePending = ref(false), passwordPending = ref(false)
const profileError = ref(''), passwordError = ref(''), saved = ref(false)

async function saveProfile() {
  if (profilePending.value) return
  if (!profile.realName.trim()) { profileError.value = '请填写姓名'; return }
  profilePending.value = true; profileError.value = ''; saved.value = false
  try { auth.setUser(await authApi.updateProfile(profile.realName.trim(), profile.phone.trim() || null)); saved.value = true; profile.phone = '' }
  catch (failure) { profileError.value = (failure as Error).message }
  finally { profilePending.value = false }
}
async function changePassword() {
  if (passwordPending.value) return
  passwordError.value = ''
  if (!password.oldPassword || password.newPassword.length < 12 || new TextEncoder().encode(password.newPassword).length > 72) {
    passwordError.value = '请填写原密码；新密码至少 12 个字符，最多 72 字节'; return
  }
  if (password.newPassword !== password.confirm) { passwordError.value = '两次输入的新密码不一致'; return }
  passwordPending.value = true
  try {
    await authApi.changePassword(password.oldPassword, password.newPassword)
    auth.clear()
    await router.replace({ path: '/login', query: { changed: '1' } })
  } catch (failure) { passwordError.value = (failure as Error).message }
  finally { passwordPending.value = false }
}
</script>

<template>
  <div><p class="eyebrow">账号设置</p><h1>照顾好您的账号</h1>
    <WalletSummary v-if="auth.user?.roles.includes('FAMILY')"/><div class="info-grid">
      <section class="content-card"><h2>个人信息</h2><p class="muted">当前联系电话：{{ auth.user?.phoneMasked || '尚未填写' }}</p>
        <form class="form-stack" @submit.prevent="saveProfile">
          <label for="realName">姓名</label><el-input id="realName" v-model="profile.realName" maxlength="80" :disabled="profilePending" />
          <label for="phone">新联系电话（留空保留原号码）</label><el-input id="phone" v-model="profile.phone" type="tel" autocomplete="tel" maxlength="30" :disabled="profilePending" />
          <el-alert v-if="profileError" :title="profileError" type="error" :closable="false" role="alert" />
          <el-alert v-if="saved" title="个人信息已保存" type="success" :closable="false" role="status" />
          <el-button native-type="submit" type="primary" :loading="profilePending">保存个人信息</el-button>
        </form>
      </section>
      <section class="content-card"><h2>修改密码</h2><p class="muted">修改成功后，所有设备需重新登录。</p>
        <form class="form-stack" @submit.prevent="changePassword">
          <label for="oldPassword">原密码</label><el-input id="oldPassword" v-model="password.oldPassword" type="password" show-password autocomplete="current-password" :disabled="passwordPending" />
          <label for="newPassword">新密码（至少 12 个字符）</label><el-input id="newPassword" v-model="password.newPassword" type="password" show-password autocomplete="new-password" :disabled="passwordPending" />
          <label for="confirmPassword">再次输入新密码</label><el-input id="confirmPassword" v-model="password.confirm" type="password" show-password autocomplete="new-password" :disabled="passwordPending" />
          <el-alert v-if="passwordError" :title="passwordError" type="error" :closable="false" role="alert" />
          <el-button native-type="submit" type="primary" :loading="passwordPending">修改密码并重新登录</el-button>
        </form>
      </section>
    </div>
    <AccountPreferences/>
  </div>
</template>
