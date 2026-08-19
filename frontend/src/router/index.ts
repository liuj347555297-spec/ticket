import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

interface NavigationItem {
  path: string
  name: string
  label: string
  icon: string
  primary?: boolean
  component?: typeof DashboardView
}

export const navigation = [
  { path: '/', name: 'dashboard', label: '服务概览', icon: '▦', component: DashboardView },
  { path: '/tickets', name: 'tickets', label: '工单中心', icon: '▤' },
  { path: '/tickets/new', name: 'create-ticket', label: '新建工单', icon: '+', primary: true },
  { path: '/knowledge', name: 'knowledge', label: '知识库', icon: '▤' },
  { path: '/service-config', name: 'service-config', label: '服务配置', icon: '⚙' },
  { path: '/reports', name: 'reports', label: '运营报表', icon: '▥' },
  { path: '/operations', name: 'operations', label: '运行治理', icon: '◈' },
] satisfies NavigationItem[]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: navigation.map((item) => ({
    path: item.path,
    name: item.name,
    component: item.component ?? PlaceholderView,
    meta: { label: item.label, primary: item.primary ?? false },
  })),
})

export default router
