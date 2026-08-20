import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import TicketCreateView from '@/views/TicketCreateView.vue'
import TicketDetailView from '@/views/TicketDetailView.vue'
import TicketListView from '@/views/TicketListView.vue'
import IamProjectionView from '@/views/IamProjectionView.vue'
import ServiceConfigView from '@/views/ServiceConfigView.vue'
import NotificationCenterView from '@/views/NotificationCenterView.vue'
import NotificationRoutingPreviewView from '@/views/NotificationRoutingPreviewView.vue'

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
  { path: '/tickets', name: 'tickets', label: '工单中心', icon: '▤', component: TicketListView },
  { path: '/tickets/new', name: 'create-ticket', label: '新建工单', icon: '+', primary: true, component: TicketCreateView },
  { path: '/notifications', name: 'notifications', label: '消息中心', icon: '♢', component: NotificationCenterView },
  { path: '/knowledge', name: 'knowledge', label: '知识库', icon: '▤' },
  { path: '/service-config', name: 'service-config', label: '服务配置', icon: '⚙', component: ServiceConfigView },
  { path: '/iam-projection', name: 'iam-projection', label: 'IAM 投影', icon: '◎', component: IamProjectionView },
  { path: '/reports', name: 'reports', label: '运营报表', icon: '▥' },
  { path: '/operations', name: 'operations', label: '运行治理', icon: '◈', component: NotificationRoutingPreviewView },
] satisfies NavigationItem[]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...navigation.map((item) => ({
    path: item.path,
    name: item.name,
    component: item.component ?? PlaceholderView,
    meta: { label: item.label, primary: item.primary ?? false },
    })),
    { path: '/login', name: 'login', component: PlaceholderView, meta: { label: 'IAM 登录' } },
    { path: '/tickets/:ticketId', name: 'ticket-detail', component: TicketDetailView, meta: { label: '工单详情' } },
  ],
})

export default router
