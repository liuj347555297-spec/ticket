<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSessionStore } from '@/stores/session'
import type { DataScopeSummary, PlatformRole, ProjectionUserStatus } from '@/api/identity'

interface ProjectionDirectoryUser {
  iamUserId: string
  displayName: string
  loginName: string
  organizationName: string
  organizationId: string
  positionName: string
  status: ProjectionUserStatus
  roles: PlatformRole[]
  dataScopes: DataScopeSummary[]
  syncedAt: string
}

const session = useSessionStore()
const selectedUserId = ref('iam-100231')
const organizations = [
  { id: 'ORG-GROUP', name: '集团总部', level: 0, count: 1820 },
  { id: 'ORG-HQ-IT', name: '信息技术部', level: 1, count: 238 },
  { id: 'ORG-HQ-IT-OPS', name: '运维服务中心', level: 2, count: 86 },
  { id: 'ORG-HQ-IT-APP', name: '应用支持中心', level: 2, count: 72 },
  { id: 'ORG-HQ-FIN', name: '财务管理部', level: 1, count: 165 },
  { id: 'ORG-EAST', name: '华东分支机构', level: 1, count: 430 },
]
const users: ProjectionDirectoryUser[] = [
  { iamUserId: 'iam-100231', displayName: '张晨', loginName: 'zhang.chen', organizationName: '总部 / 信息技术部 / 运维服务中心', organizationId: 'ORG-HQ-IT-OPS', positionName: '一线支持工程师', status: 'ACTIVE', roles: ['FIRST_LINE_SUPPORT'], dataScopes: [{ scopeType: 'QUEUE', scopeId: 'QUEUE-DESK-01' }, { scopeType: 'ORGANIZATION', scopeId: 'ORG-HQ-IT' }], syncedAt: '2026-08-20 09:30:12' },
  { iamUserId: 'iam-100488', displayName: '李敏', loginName: 'li.min', organizationName: '总部 / 信息技术部 / 应用支持中心', organizationId: 'ORG-HQ-IT-APP', positionName: '应用支持工程师', status: 'ACTIVE', roles: ['SECOND_LINE_SUPPORT', 'APPROVER'], dataScopes: [{ scopeType: 'SERVICE', scopeId: 'SVC-ERP' }, { scopeType: 'QUEUE', scopeId: 'QUEUE-APP-02' }], syncedAt: '2026-08-20 09:30:12' },
  { iamUserId: 'iam-100755', displayName: '王璐', loginName: 'wang.lu', organizationName: '总部 / 财务管理部', organizationId: 'ORG-HQ-FIN', positionName: '财务专员', status: 'ACTIVE', roles: ['REQUESTER'], dataScopes: [{ scopeType: 'ORGANIZATION', scopeId: 'ORG-HQ-FIN' }], syncedAt: '2026-08-20 09:30:12' },
  { iamUserId: 'iam-101027', displayName: '陈宇', loginName: 'chen.yu', organizationName: '华东分支机构', organizationId: 'ORG-EAST', positionName: '区域 IT 负责人', status: 'DISABLED', roles: ['SERVICE_MANAGER'], dataScopes: [{ scopeType: 'ORGANIZATION', scopeId: 'ORG-EAST' }], syncedAt: '2026-08-20 09:30:12' },
]
const selectedUser = computed(() => users.find((user) => user.iamUserId === selectedUserId.value) ?? users[0])
const currentUser = computed(() => session.currentUser)
const currentAuthorization = computed(() => session.authorization)
const sessionSourceText = computed(() => ({ api: '当前 IAM 会话', 'development-preview': '开发预览回退', unauthenticated: '未认证会话' }[session.source ?? 'unauthenticated']))
const roleLabels: Record<PlatformRole, string> = { REQUESTER: '普通提单人', FIRST_LINE_SUPPORT: '一线支持', SECOND_LINE_SUPPORT: '二线支持', APPROVER: '审批人', SERVICE_MANAGER: '服务经理', PLATFORM_ADMIN: '平台管理员', AUDITOR: '审计员' }
const scopeLabels: Record<DataScopeSummary['scopeType'], string> = { ORGANIZATION: '组织范围', SERVICE: '服务范围', QUEUE: '队列范围', CONFIGURATION_ITEM: 'CI 范围' }
const statusLabels: Record<ProjectionUserStatus, string> = { ACTIVE: '启用', DISABLED: '停用' }
</script>

<template>
  <div class="page-heading"><div><h2>IAM 只读投影</h2><p>全量同步的用户与组织只读副本，用于候选人、数据范围、消息投递和审计。</p></div><div class="projection-heading-tags"><span class="readonly-badge">🔒 平台只读</span><span class="sync-badge sync-badge--success">● 最近同步成功</span></div></div>
  <p class="projection-notice">平台不保存密码、不创建本地账号、不回写或编辑 IAM 组织。角色与数据范围仅作界面提示，所有访问和工单操作均由后端实时鉴权。</p>
  <div class="metric-grid projection-metrics"><article class="metric-card"><span>投影用户</span><strong>50,236</strong><small class="text-green">同步覆盖率 100%</small></article><article class="metric-card"><span>组织节点</span><strong>1,286</strong><small class="text-blue">全量组织字段映射</small></article><article class="metric-card"><span>本次同步</span><strong>09:30</strong><small class="text-green">新增 12 / 更新 36</small></article><article class="metric-card"><span>同步异常</span><strong>0</strong><small>失败记录待重试 0</small></article></div>
  <section class="projection-session panel"><div class="panel-header"><div><h3>当前会话投影</h3><p>来源：{{ sessionSourceText }}；仅显示服务端 `/api/v1/me` 返回的展示字段。</p></div><span class="readonly-badge">只读</span></div><div v-if="currentUser" class="session-summary"><div><span>姓名</span><b>{{ currentUser.displayName }}</b></div><div><span>IAM 用户 ID</span><b class="mono-text">{{ currentUser.iamUserId }}</b></div><div><span>所属组织</span><b>{{ currentUser.organizationName }}</b></div><div><span>平台角色</span><p class="compact-pill-row"><i v-for="role in currentAuthorization?.roles ?? []" :key="role" class="role-pill">{{ roleLabels[role] }}</i><em v-if="!(currentAuthorization?.roles.length)">未返回</em></p></div></div><div v-else class="session-empty">未识别到 IAM 会话。请通过 IAM 登录后重试；前端不会自行创建身份或权限。</div></section>
  <div class="iam-workspace"><section class="panel iam-org-panel"><div class="panel-header"><div><h3>组织树</h3><p>来源：IAM 组织主数据</p></div><span class="sync-source">全量同步</span></div><ul class="organization-tree" aria-label="只读组织树"><li v-for="organization in organizations" :key="organization.id" :style="{ '--tree-level': organization.level }"><span class="tree-mark">{{ organization.level === 0 ? '▾' : '└' }}</span><b>{{ organization.name }}</b><small>{{ organization.count.toLocaleString() }} 人</small></li></ul></section>
    <section class="panel iam-users-panel"><div class="panel-header"><div><h3>用户投影</h3><p>列表仅用于候选人预览；实际可见范围由服务端控制。</p></div><span class="sync-source">IAM 主数据</span></div><div class="projection-user-list" role="listbox" aria-label="只读用户投影列表"><button v-for="user in users" :key="user.iamUserId" type="button" class="projection-user-row" :class="{ 'is-selected': selectedUser.iamUserId === user.iamUserId }" :aria-selected="selectedUser.iamUserId === user.iamUserId" @click="selectedUserId = user.iamUserId"><span class="user-avatar">{{ user.displayName.slice(0, 1) }}</span><span class="projection-user-main"><b>{{ user.displayName }}</b><small>{{ user.positionName }}</small></span><span class="projection-user-state" :class="`projection-user-state--${user.status.toLowerCase()}`">{{ statusLabels[user.status] }}</span></button></div></section>
    <section class="panel iam-detail-panel"><div class="panel-header"><div><h3>用户详情</h3><p>字段均来自 IAM 只读投影</p></div><span class="readonly-badge">不可编辑</span></div><div class="detail-user-title"><span class="detail-user-avatar">{{ selectedUser.displayName.slice(0, 1) }}</span><div><h4>{{ selectedUser.displayName }}</h4><p>{{ selectedUser.positionName }} · {{ selectedUser.organizationName }}</p></div></div><dl class="projection-definition"><div><dt>IAM 用户 ID</dt><dd class="mono-text">{{ selectedUser.iamUserId }}</dd></div><div><dt>登录名</dt><dd>{{ selectedUser.loginName }}</dd></div><div><dt>账号状态</dt><dd><span class="status-pill" :class="`projection-user-state--${selectedUser.status.toLowerCase()}`">{{ statusLabels[selectedUser.status] }}</span></dd></div><div><dt>组织节点 ID</dt><dd class="mono-text">{{ selectedUser.organizationId }}</dd></div><div><dt>最近同步</dt><dd>{{ selectedUser.syncedAt }}</dd></div></dl><div class="access-section"><h4>平台角色 <small>展示用途，非前端授权</small></h4><div class="compact-pill-row"><span v-for="role in selectedUser.roles" :key="role" class="role-pill">{{ roleLabels[role] }}</span></div></div><div class="access-section"><h4>数据范围 <small>由服务端逐请求重新计算</small></h4><ul class="scope-list"><li v-for="scope in selectedUser.dataScopes" :key="`${scope.scopeType}-${scope.scopeId}`"><span>{{ scopeLabels[scope.scopeType] }}</span><b class="mono-text">{{ scope.scopeId }}</b></li></ul></div></section></div>
  <section class="panel field-source-panel"><div class="panel-header"><div><h3>字段来源与留存规则</h3><p>工单事件将保存 IAM ID 与当时身份快照，避免调岗后历史失真。</p></div></div><div class="table-scroll"><table><thead><tr><th>字段组</th><th>主数据来源</th><th>平台处理</th><th>用途</th></tr></thead><tbody><tr><td>用户 / 登录名 / 状态</td><td>IAM 用户主数据</td><td><span class="tag tag--blue">只读同步</span></td><td>候选人、消息投递、审计</td></tr><tr><td>组织 / 岗位 / 上级组织</td><td>IAM 组织主数据</td><td><span class="tag tag--blue">只读同步</span></td><td>组织数据范围、审批候选人</td></tr><tr><td>工单提单 / 转派身份</td><td>IAM ID + 提交时快照</td><td><span class="tag tag--muted">不可回写</span></td><td>历史追溯、审计取证</td></tr><tr><td>密码 / 凭据 / 访问令牌</td><td>IAM 会话域</td><td><span class="tag tag--red">不落平台</span></td><td>不存储、不展示、不导出</td></tr></tbody></table></div></section>
</template>
