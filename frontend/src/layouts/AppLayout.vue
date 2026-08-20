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
    <aside class="sidebar" aria-label="主导航">
      <RouterLink class="brand" to="/" aria-label="ServiceHub 服务台首页">
        <span class="brand-mark">S</span><span>ServiceHub</span>
      </RouterLink>
      <nav class="navigation">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item--primary': item.primary }"
        >
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-footer">内网服务台<br /><small>安全会话由 IAM 统一管理</small></div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div><h1>{{ title }}</h1><p>服务台 / 当前工作区</p></div>
        <label class="global-search"><span>⌕</span><input type="search" placeholder="搜索工单、CI、知识库" /></label>
        <RouterLink class="icon-button notification-trigger" to="/notifications" aria-label="查看消息中心">
          <span aria-hidden="true">♢</span><span v-if="notifications.unreadCount" class="notification-badge">{{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}</span>
        </RouterLink>
        <RouterLink class="avatar" to="/iam-projection" aria-label="查看身份投影">{{ userInitial }}</RouterLink>
      </header>
      <p v-if="isDevelopmentPreview" class="session-preview-notice">开发预览身份：仅用于界面展示，权限始终由服务端 IAM 会话校验。</p>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>
