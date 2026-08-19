<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { navigation } from '@/router'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const session = useSessionStore()
const title = computed(() => String(route.meta.label ?? 'ServiceHub'))
const userInitial = computed(() => session.currentUser?.displayName?.slice(0, 1) ?? '访')
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
        <button class="icon-button" type="button" aria-label="查看通知">♢</button>
        <button class="avatar" type="button" aria-label="查看身份投影">{{ userInitial }}</button>
      </header>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>
