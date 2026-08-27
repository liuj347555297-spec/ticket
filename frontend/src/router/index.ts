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
import KnowledgeView from '@/views/KnowledgeView.vue'
import KnowledgeArticleView from '@/views/KnowledgeArticleView.vue'
import OperationsReportView from '@/views/OperationsReportView.vue'
import SlaRuleView from '@/views/SlaRuleView.vue'
import IntegrationGovernanceView from '@/views/IntegrationGovernanceView.vue'
import ApprovalTaskInboxView from '@/views/ApprovalTaskInboxView.vue'
import BackofficeAccessView from '@/views/BackofficeAccessView.vue'

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
  { path: '/approval-tasks', name: 'approval-tasks', label: '审批待办', icon: '✓', component: ApprovalTaskInboxView },
  { path: '/tickets/new', name: 'create-ticket', label: '新建工单', icon: '+', primary: true, component: TicketCreateView },
  { path: '/notifications', name: 'notifications', label: '消息中心', icon: '♢', component: NotificationCenterView },
  { path: '/knowledge', name: 'knowledge', label: '知识库', icon: '▤', component: KnowledgeView },
  { path: '/service-config', name: 'service-config', label: '服务配置', icon: '⚙', component: ServiceConfigView },
  { path: '/iam-projection', name: 'iam-projection', label: 'IAM 投影', icon: '◎', component: IamProjectionView },
  { path: '/backoffice-access', name: 'backoffice-access', label: '后台人员授权', icon: '♙', component: BackofficeAccessView },
  { path: '/reports', name: 'reports', label: '运营报表', icon: '▥', component: OperationsReportView },
  { path: '/operations', name: 'operations', label: '运行治理', icon: '◈', component: IntegrationGovernanceView },
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
    { path: '/knowledge/:articleId', name: 'knowledge-article', component: KnowledgeArticleView, meta: { label: '知识详情' } },
    { path: '/sla-rules', name: 'sla-rules', component: SlaRuleView, meta: { label: 'SLA 规则' } },
    { path: '/notification-routing-preview', name: 'notification-routing-preview', component: NotificationRoutingPreviewView, meta: { label: '消息路由预览' } },
  ],
})

export default router
