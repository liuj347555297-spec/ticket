<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { navigation } from '@/router'
import type { PlatformRole } from '@/api/identity'
import { useSessionStore } from '@/stores/session'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const session = useSessionStore()
const notifications = useNotificationStore()
const navigationOpen = ref(false)
const navigationDrawer = ref<HTMLElement | null>(null)
const navigationToggle = ref<HTMLButtonElement | null>(null)
const title = computed(() => String(route.meta.label ?? 'ServiceHub'))
const userInitial = computed(() => session.currentUser?.displayName?.slice(0, 1) ?? '访')
const isDevelopmentPreview = computed(() => session.source === 'development-preview')
const roleRequirements: Partial<Record<string, PlatformRole[]>> = {
  'approval-tasks': ['APPROVER', 'SERVICE_MANAGER', 'PLATFORM_ADMIN'],
  'service-config': ['SERVICE_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'],
  'iam-projection': ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'],
  'backoffice-access': ['PLATFORM_ADMIN'],
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
const workbenchNavigation = computed(() => visibleNavigation.value.filter((item) => workbenchNames.includes(item.name)))
const governanceNavigation = computed(() => visibleNavigation.value.filter((item) => !workbenchNames.includes(item.name)))
const topNavigationCandidates = [
  { label: '工作台', path: '/', names: ['dashboard'] },
  { label: '我的流程', path: '/tickets', names: ['tickets', 'ticket-detail', 'create-ticket', 'approval-tasks', 'notifications'] },
  { label: '基础配置', path: '/service-config', names: ['service-config', 'design-studio', 'iam-projection', 'backoffice-access', 'operations'], roles: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'] as PlatformRole[] },
  { label: '知识管理', path: '/knowledge', names: ['knowledge', 'knowledge-article'] },
  { label: '报表', path: '/reports', names: ['reports', 'sla-rules', 'notification-routing-preview'], roles: ['SERVICE_MANAGER', 'SLA_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'] as PlatformRole[] },
]
const topNavigation = computed(() => topNavigationCandidates.filter((item) => !item.roles || item.roles.some((role) => currentRoles.value.has(role))))
function isTopActive(names: string[]): boolean { return names.includes(String(route.name)) }

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

onMounted(async () => {
  window.addEventListener('keydown', handleGlobalKeydown)
  try {
    await session.loadCurrentUser()
  } catch {
    // The application remains usable for public/development shell rendering. No client-side
    // authentication decision or privilege fallback is made when the identity endpoint fails.
  }
  try {
    await notifications.loadUnreadCount()
  } catch {
    // Notification availability must not influence IAM session rendering or authorization.
  }
})
onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalKeydown))
</script>

<template>
  <div class="app-shell">
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
      <RouterLink class="user-entry" to="/iam-projection" aria-label="查看身份投影"><span class="avatar">{{ userInitial }}</span><span><b>{{ session.currentUser?.displayName ?? '访客预览' }}</b><small>IAM 身份</small></span></RouterLink>
    </header>

    <button v-if="navigationOpen" class="mobile-nav-backdrop" type="button" aria-label="关闭功能导航" @click="closeNavigation(true)"></button>
    <aside id="mobile-navigation" ref="navigationDrawer" class="sidebar" :class="{ 'is-mobile-open': navigationOpen }" aria-label="功能导航" @keydown="trapDrawerFocus">
      <div class="sidebar-mobile-header"><b>功能导航</b><button type="button" aria-label="关闭功能导航" @click="closeNavigation(true)">×</button></div>
      <div class="sidebar-section">
        <p class="navigation-title">服务工作台</p>
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
      </div>
      <div v-if="governanceNavigation.length" class="sidebar-section">
        <p class="navigation-title">基础配置</p>
      <nav class="navigation navigation--governance" aria-label="平台治理">
        <RouterLink v-for="item in governanceNavigation" :key="item.name" :to="item.path" class="nav-item">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      </div>
      <p class="navigation-authority-note">菜单仅按当前 IAM 角色优化呈现；访问权限始终由服务端逐次校验。</p>
      <div class="sidebar-footer"><span class="sidebar-footer__dot"></span>内网服务台<br /><small>由 IAM 统一鉴权</small></div>
    </aside>

    <main class="main-content">
      <div class="workspace-crumb"><span>⌂</span><span>服务台</span><i>›</i><b>{{ title }}</b><em>内网</em></div>
      <p v-if="isDevelopmentPreview" class="session-preview-notice">开发预览身份：仅用于界面展示，权限始终由服务端 IAM 会话校验。</p>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>

<style scoped>
/* The icon-only tablet rail cannot accommodate paragraph-length guidance. */
@media (min-width: 681px) and (max-width: 1020px) {
  .navigation-authority-note, .sidebar-footer { display: none; }
}
</style>
