<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { navigation } from '@/router'
import { authApi } from '@/api/auth'
import type { PlatformRole } from '@/api/identity'
import { useSessionStore } from '@/stores/session'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const notifications = useNotificationStore()
const navigationOpen = ref(false)
const navigationDrawer = ref<HTMLElement | null>(null)
const navigationToggle = ref<HTMLButtonElement | null>(null)
const loggingOut = ref(false)
const title = computed(() => String(route.meta.label ?? 'ServiceHub'))
const userInitial = computed(() => session.currentUser?.displayName?.slice(0, 1) ?? '访')
const isDevelopmentPreview = computed(() => session.source === 'development-preview')
const roleRequirements: Partial<Record<string, PlatformRole[]>> = {
  'approval-tasks': ['APPROVER', 'SERVICE_MANAGER', 'PLATFORM_ADMIN'],
  'service-config': ['SERVICE_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'],
  'iam-projection': ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'],
  'backoffice-access': ['PLATFORM_ADMIN'],
  'account-management': ['PLATFORM_ADMIN'],
  'approval-policies': ['SERVICE_MANAGER', 'PLATFORM_ADMIN'],
  reports: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'],
  operations: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN'],
}
const currentRoles = computed(() => new Set(session.authorization?.roles ?? []))
function canPresent(name: string): boolean {
  const required = roleRequirements[name]
  return !required || required.some((role) => currentRoles.value.has(role))
}
const visibleNavigation = computed(() => navigation.filter((item) => canPresent(item.name)))
const workbenchNames = ['dashboard', 'create-ticket', 'tickets', 'approval-tasks', 'notifications', 'knowledge']
const inFlowWorkspace = computed(()=>['tickets','ticket-detail','ticket-drafts','create-ticket','approval-tasks'].includes(String(route.name)))
const flowNavigation = [
  {label:'我的待办',path:'/tickets?queue=MY_TODO',icon:'◷'},
  {label:'逾期待办',path:'/tickets?queue=OVERDUE',icon:'!'},
  {label:'当日需完成',path:'/tickets?queue=TODAY_DUE',icon:'▣'},
  {label:'我的已办',path:'/tickets?queue=MY_DONE',icon:'✓'},
  {label:'我发起的',path:'/tickets?queue=MY_REQUESTED',icon:'↗'},
  {label:'草稿箱',path:'/ticket-drafts',icon:'▤'},
  {label:'所有工单',path:'/tickets?queue=ALL',icon:'▦'},
  {label:'我的待阅',path:'/tickets?queue=TO_READ',icon:'◇'},
]
function flowActive(path:string){return path==='/ticket-drafts'?route.name==='ticket-drafts':route.name==='tickets'&&(route.query.queue??'MY_TODO')===new URLSearchParams(path.split('?')[1]).get('queue')}
const workbenchNavigation = computed(() => visibleNavigation.value.filter((item) => workbenchNames.includes(item.name)&&(!inFlowWorkspace.value||!['tickets','approval-tasks'].includes(item.name))))
const governanceNavigation = computed(() => visibleNavigation.value.filter((item) => !workbenchNames.includes(item.name)))
const topNavigationCandidates = [
  { label: '工作台', path: '/', names: ['dashboard'] },
  { label: '我的流程', path: '/tickets', names: ['tickets', 'ticket-detail', 'ticket-drafts', 'create-ticket', 'approval-tasks'] },
  { label: '消息中心', path: '/notifications', names: ['notifications'] },
  { label: '基础配置', path: '/service-config', names: ['service-config', 'design-studio', 'iam-projection', 'backoffice-access', 'account-management', 'operations'], roles: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'] as PlatformRole[] },
  { label: '知识管理', path: '/knowledge', names: ['knowledge', 'knowledge-article'] },
  { label: '报表', path: '/reports', names: ['reports', 'sla-rules', 'notification-routing-preview'], roles: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'] as PlatformRole[] },
]
const topNavigation = computed(() => topNavigationCandidates.filter((item) => !item.roles || item.roles.some((role) => currentRoles.value.has(role))))
function isTopActive(names: string[]): boolean { return names.includes(String(route.name)) }
async function logout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try { await authApi.logout() } catch { /* Session is cleared locally even if it already expired. */ }
  session.clearSession(); notifications.clear(); loggingOut.value = false
  await router.replace('/login')
}

async function openNavigation(): Promise<void> {
  navigationOpen.value = true
  await nextTick()
  navigationDrawer.value?.querySelector<HTMLElement>('a, button')?.focus()
}
async function closeNavigation(restoreFocus = false): Promise<void> {
  navigationOpen.value = false
  if (restoreFocus) {
    await nextTick()
    navigationToggle.value?.focus()
  }
}
function handleGlobalKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && navigationOpen.value) {
    event.preventDefault()
    void closeNavigation(true)
  }
}
function trapDrawerFocus(event: KeyboardEvent): void {
  if (event.key !== 'Tab' || !navigationOpen.value || window.matchMedia('(min-width: 681px)').matches) return
  const focusable = Array.from(navigationDrawer.value?.querySelectorAll<HTMLElement>('a, button:not([disabled])') ?? [])
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

watch(() => route.fullPath, () => { navigationOpen.value = false })
watch(() => JSON.stringify([route.name, session.authorization]), () => {
  if (session.currentUser && route.name !== 'login' && !canPresent(String(route.name))) void router.replace('/')
}, { flush: 'post' })

onMounted(async () => {
  window.addEventListener('keydown', handleGlobalKeydown)
  try {
    await session.loadCurrentUser()
  } catch {
    // The application remains usable for public/development shell rendering. No client-side
    // authentication decision or privilege fallback is made when the identity endpoint fails.
  }
  if (!session.currentUser && route.name !== 'login') { const requested=`${window.location.pathname}${window.location.search}${window.location.hash}`; await router.replace({ name:'login', query:{ redirect:requested.startsWith('/')&&!requested.startsWith('//')?requested:'/' } }); return }
  if (session.currentUser && route.name === 'login') { await router.replace('/'); return }
  try {
    await notifications.loadUnreadCount()
  } catch {
    // Notification availability must not influence IAM session rendering or authorization.
  }
})
onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalKeydown))
</script>

<template>
  <RouterView v-if="route.name === 'login'" />
  <div v-else class="app-shell">
    <header class="topbar topbar--itsupport">
      <button ref="navigationToggle" class="mobile-nav-toggle" type="button" aria-label="打开功能导航" aria-controls="mobile-navigation" :aria-expanded="navigationOpen" @click="openNavigation">☰</button>
      <RouterLink class="brand" to="/" aria-label="ServiceHub 服务台首页">
        <span class="brand-mark">S</span><span><b>ServiceHub</b><small>信息化服务平台</small></span>
      </RouterLink>
      <nav class="top-navigation" aria-label="一级导航">
        <RouterLink v-for="item in topNavigation" :key="item.label" :to="item.path" :class="{ 'is-active': isTopActive(item.names) }">{{ item.label }}</RouterLink>
      </nav>
      <label class="global-search"><span>⌕</span><input type="search" placeholder="请输入菜单名称" /><kbd>⌘ K</kbd></label>
      <RouterLink class="icon-button notification-trigger" to="/notifications" aria-label="查看消息中心">
        <span aria-hidden="true">♢</span><span v-if="notifications.unreadCount" class="notification-badge">{{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}</span>
      </RouterLink>
      <div class="user-entry"><span class="avatar">{{ userInitial }}</span><span><b>{{ session.currentUser?.displayName ?? '未登录' }}</b><small>平台账号</small></span></div><button class="logout-button" type="button" :disabled="loggingOut" @click="logout">退出</button>
    </header>

    <button v-if="navigationOpen" class="mobile-nav-backdrop" type="button" aria-label="关闭功能导航" @click="closeNavigation(true)"></button>
    <aside id="mobile-navigation" ref="navigationDrawer" class="sidebar" :class="{ 'is-mobile-open': navigationOpen }" aria-label="功能导航" @keydown="trapDrawerFocus">
      <div class="sidebar-mobile-header"><b>功能导航</b><button type="button" aria-label="关闭功能导航" @click="closeNavigation(true)">×</button></div>
      <div class="sidebar-section">
        <p class="navigation-title">{{inFlowWorkspace?'我的流程':'服务工作台'}}</p>
      <nav class="navigation navigation--workspace" aria-label="服务工作台">
        <RouterLink
          v-for="item in workbenchNavigation"
          :key="item.name"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item--primary': item.primary }"
        >
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <nav v-if="inFlowWorkspace" class="navigation manual-flow-navigation" aria-label="我的流程分类"><RouterLink v-for="item in flowNavigation" :key="item.path" :to="item.path" class="nav-item" :class="{'manual-flow-active':flowActive(item.path)}"><span class="nav-icon" aria-hidden="true">{{item.icon}}</span>{{item.label}}</RouterLink></nav>
      </div>
      <div v-if="governanceNavigation.length" class="sidebar-section">
        <p class="navigation-title">基础配置</p>
      <nav class="navigation navigation--governance" aria-label="平台治理">
        <RouterLink v-for="item in governanceNavigation" :key="item.name" :to="item.path" class="nav-item">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      </div>
      <p class="navigation-authority-note">菜单按当前账号角色优化呈现；访问权限始终由服务端逐次校验。</p>
      <div class="sidebar-footer"><span class="sidebar-footer__dot"></span>内网服务台<br /><small>本地账号会话鉴权</small></div>
    </aside>

    <main class="main-content">
      <div class="workspace-crumb"><span>⌂</span><span>服务台</span><i>›</i><b>{{ title }}</b><em>内网</em></div>
      <p v-if="isDevelopmentPreview" class="session-preview-notice">开发预览身份：仅用于界面展示，权限始终由服务端会话校验。</p>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>

<style scoped>
.manual-flow-navigation .nav-item.router-link-active{background:transparent;color:inherit}.manual-flow-navigation .nav-item.manual-flow-active{background:#246db8;color:white}.manual-flow-navigation{margin-top:12px}
.logout-button{min-height:30px;padding:4px 9px;border:1px solid #d6e1ec;border-radius:4px;color:#536f8b;background:#fff;cursor:pointer}.logout-button:disabled{opacity:.55}
/* The icon-only tablet rail cannot accommodate paragraph-length guidance. */
@media (min-width: 681px) and (max-width: 1020px) {
  .navigation-authority-note, .sidebar-footer { display: none; }
}
</style>
