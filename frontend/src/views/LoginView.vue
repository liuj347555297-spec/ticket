<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { authApi } from '@/api/auth'
import { useSessionStore } from '@/stores/session'
const route = useRoute(), router = useRouter(), session = useSessionStore()
const loginName = ref(''), password = ref(''), submitting = ref(false), error = ref('')
function safeRedirect(): string {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/login') ? value : '/'
}
async function submit(): Promise<void> {
  if (submitting.value) return
  error.value = ''
  if (!loginName.value.trim() || password.value.length < 1) { error.value = '请输入账号和密码。'; return }
  submitting.value = true
  try {
    const current = await authApi.login(loginName.value.trim(), password.value)
    session.applyCurrentUser(current, 'api')
    password.value = ''
    await router.replace(safeRedirect())
  } catch (cause) {
    password.value = ''
    error.value = cause instanceof ApiError && cause.status === 429 ? '登录尝试过多，请稍后再试。' : '账号或密码错误，或账号暂不可用。'
  } finally { submitting.value = false }
}
onMounted(() => { if (session.currentUser) void router.replace('/') })
</script>
<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="login-title">
      <header><span class="login-mark">S</span><div><b>ServiceHub</b><small>集团信息化服务平台</small></div></header>
      <div class="login-copy"><small>LOCAL ACCOUNT</small><h1 id="login-title">账号登录</h1><p>普通用户可发起服务与查看知识；运维人员按负责系统受理和处理工单。</p></div>
      <form @submit.prevent="submit">
        <label><span>账号</span><input v-model="loginName" name="username" autocomplete="username" maxlength="128" autofocus /></label>
        <label><span>密码</span><input v-model="password" name="password" type="password" autocomplete="current-password" maxlength="128" /></label>
        <p v-if="error" role="alert">{{ error }}</p>
        <button type="submit" :disabled="submitting">{{ submitting ? '正在验证…' : '登录' }}</button>
      </form>
      <footer>账号、角色和负责系统均由平台管理员配置；本页不会保存密码。</footer>
    </section>
  </main>
</template>
<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(circle at 16% 12%,#e9f4ff 0,transparent 38%),linear-gradient(135deg,#f6f9fd,#e8f0f8)}.login-card{width:min(420px,100%);padding:30px;border:1px solid #d9e5f1;border-radius:12px;background:#fff;box-shadow:0 20px 60px rgb(25 66 108/12%)}header{display:flex;align-items:center;gap:12px}.login-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:8px;color:#fff;background:#2671bd;font-size:20px;font-weight:700}header div{display:grid;gap:2px}header b{color:#284966;font-size:17px}header small,.login-copy p,footer{color:#71859a;font-size:12px;line-height:1.65}.login-copy{margin:34px 0 22px}.login-copy>small{color:#3478b9;font-size:10px;font-weight:700;letter-spacing:.12em}.login-copy h1{margin:7px 0;font-size:25px;color:#264d72}.login-copy p{margin:0}form{display:grid;gap:16px}label{display:grid;gap:7px;color:#48647e;font-size:12px}input{min-height:42px;padding:8px 11px;border:1px solid #ccd9e6;border-radius:6px;font:inherit;outline:0}input:focus{border-color:#3984c8;box-shadow:0 0 0 3px rgb(50 128 198/12%)}form p{margin:0;padding:9px 10px;border-radius:5px;color:#a13c35;background:#fff1ef;font-size:12px}form button{min-height:43px;border:0;border-radius:6px;color:#fff;background:#226eb7;font-weight:700;cursor:pointer}form button:disabled{opacity:.6;cursor:wait}footer{margin-top:24px;padding-top:18px;border-top:1px solid #edf1f5}
</style>
