import { createRouter, createWebHistory } from 'vue-router'
import PlaceholderView from '@/views/PlaceholderView.vue'
import type { RouteRecordRaw } from 'vue-router'

interface NavigationItem {
  path: string
  name: string
  label: string
  icon: string
  primary?: boolean
  component?: RouteRecordRaw['component']
}

export const navigation = [
  { path: '/', name: 'dashboard', label: '服务概览', icon: '▦', component: () => import('@/views/DashboardView.vue') },
  { path: '/tickets', name: 'tickets', label: '工单中心', icon: '▤', component: () => import('@/views/TicketListView.vue') },
  { path: '/approval-tasks', name: 'approval-tasks', label: '审批待办', icon: '✓', component: () => import('@/views/ApprovalTaskInboxView.vue') },
  { path: '/tickets/new', name: 'create-ticket', label: '新建工单', icon: '+', primary: true, component: () => import('@/views/TicketCreateView.vue') },
  { path: '/notifications', name: 'notifications', label: '消息中心', icon: '♢', component: () => import('@/views/NotificationCenterView.vue') },
  { path: '/knowledge', name: 'knowledge', label: '知识库', icon: '▤', component: () => import('@/views/KnowledgeView.vue') },
  { path: '/service-config', name: 'service-config', label: '系统服务配置', icon: '⚙', component: () => import('@/views/ServiceConfigView.vue') },
  { path: '/iam-projection', name: 'iam-projection', label: '身份目录', icon: '◎', component: () => import('@/views/IamProjectionView.vue') },
  { path: '/account-management', name: 'account-management', label: '账号管理', icon: '♟', component: () => import('@/views/AccountManagementView.vue') },
  { path: '/approval-policies', name: 'approval-policies', label: '审批策略', icon: '✓', component: () => import('@/views/LifecycleApprovalPolicyView.vue') },
  { path: '/reports', name: 'reports', label: '运营报表', icon: '▥', component: () => import('@/views/OperationsReportView.vue') },
  { path: '/operations', name: 'operations', label: '运行治理', icon: '◈', component: () => import('@/views/IntegrationGovernanceView.vue') },
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
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { label: '账号登录' } },
    { path: '/design-studio', redirect: '/service-config' },
    { path: '/ticket-drafts', name:'ticket-drafts', component:()=>import('@/views/TicketDraftsView.vue'), meta:{label:'草稿箱'} },
    { path: '/backoffice-access', redirect: '/account-management' },
    { path: '/tickets/:ticketId', name: 'ticket-detail', component: () => import('@/views/TicketDetailView.vue'), meta: { label: '工单详情' } },
    { path: '/knowledge/:articleId', name: 'knowledge-article', component: () => import('@/views/KnowledgeArticleView.vue'), meta: { label: '知识详情' } },
    { path: '/sla-rules', name: 'sla-rules', component: () => import('@/views/SlaRuleView.vue'), meta: { label: 'SLA 规则' } },
    { path: '/notification-routing-preview', name: 'notification-routing-preview', component: () => import('@/views/NotificationRoutingPreviewView.vue'), meta: { label: '消息路由预览' } },
  ],
})

export default router
