<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { navigation } from '@/router'
import { useSessionStore } from '@/stores/session'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const session = useSessionStore()
const notifications = useNotificationStore()
const title = computed(() => String(route.meta.label ?? 'ServiceHub'))
const userInitial = computed(() => session.currentUser?.displayName?.slice(0, 1) ?? '访')
const isDevelopmentPreview = computed(() => session.source === 'development-preview')
const workbenchNavigation = computed(() => navigation.filter((item) => ['dashboard', 'create-ticket', 'tickets', 'approval-tasks', 'notifications', 'knowledge'].includes(item.name)))
const governanceNavigation = computed(() => navigation.filter((item) => !['dashboard', 'create-ticket', 'tickets', 'approval-tasks', 'notifications', 'knowledge'].includes(item.name)))
const topNavigation = [
  { label: '工作台', path: '/', names: ['dashboard'] },
  { label: '我的流程', path: '/tickets', names: ['tickets', 'ticket-detail', 'create-ticket', 'approval-tasks', 'notifications'] },
  { label: '基础配置', path: '/service-config', names: ['service-config', 'iam-projection', 'backoffice-access', 'operations'] },
  { label: '知识管理', path: '/knowledge', names: ['knowledge', 'knowledge-article'] },
  { label: '报表', path: '/reports', names: ['reports', 'sla-rules', 'notification-routing-preview'] },
]
function isTopActive(names: string[]): boolean { return names.includes(String(route.name)) }

onMounted(async () => {
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
</script>

<template>
  <div class="app-shell">
    <header class="topbar topbar--itsupport">
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

    <aside class="sidebar" aria-label="功能导航">
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
      <div class="sidebar-section">
        <p class="navigation-title">基础配置</p>
      <nav class="navigation navigation--governance" aria-label="平台治理">
        <RouterLink v-for="item in governanceNavigation" :key="item.name" :to="item.path" class="nav-item">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      </div>
      <div class="sidebar-footer"><span class="sidebar-footer__dot"></span>内网服务台<br /><small>由 IAM 统一鉴权</small></div>
    </aside>

    <main class="main-content">
      <div class="workspace-crumb"><span>⌂</span><span>服务台</span><i>›</i><b>{{ title }}</b><em>内网</em></div>
      <p v-if="isDevelopmentPreview" class="session-preview-notice">开发预览身份：仅用于界面展示，权限始终由服务端 IAM 会话校验。</p>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>
