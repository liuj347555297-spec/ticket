<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type ApprovalTaskInboxItem } from '@/api/tickets'

const items = ref<ApprovalTaskInboxItem[]>([])
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const notice = ref('')
const selected = ref<ApprovalTaskInboxItem | null>(null)
const decision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reason = ref('')

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}
function approvalRule(item: ApprovalTaskInboxItem): string {
  if (item.decisionMode === 'ANY_ONE') return `或签 · 任意 1 人同意（候选 ${item.candidateApprovalCount} 人）`
  if (item.decisionMode === 'ALL_OF') return `会签 · 需 ${item.requiredApprovalCount}/${item.candidateApprovalCount} 人同意`
  return '历史记录未固化审批规则'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try { items.value = (await ticketApi.listApprovalTasks()).items } catch (error) {
    items.value = []
    errorMessage.value = error instanceof ApiError ? error.message : '无法加载审批待办，请稍后重试。'
  } finally { loading.value = false }
}
function begin(item: ApprovalTaskInboxItem, next: 'APPROVED' | 'REJECTED'): void { selected.value = item; decision.value = next; reason.value = ''; notice.value = '' }
function cancel(): void { selected.value = null; reason.value = '' }
async function submit(): Promise<void> {
  if (!selected.value || reason.value.trim().length < 5) return
  submitting.value = true; errorMessage.value = ''
  try {
    await ticketApi.decideControlledJump(selected.value.ticketId, selected.value.approvalRequestId, decision.value, reason.value.trim())
    notice.value = decision.value === 'APPROVED' ? '审批已通过。该申请还需由具备权限的管理人员执行预演并二次确认迁移。' : '审批已驳回，流程引擎与审计记录已同步更新。'
    cancel(); await load()
  } catch (error) { errorMessage.value = error instanceof ApiError ? error.message : '审批提交失败；未假定流程状态已改变。' } finally { submitting.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><h2>审批待办</h2><p>仅显示当前 IAM 身份在数据范围内、且 Flowable 引擎仍存在的候选审批任务。</p></div><button class="button button--secondary" type="button" :disabled="loading" @click="load">刷新</button></div>
  <p class="projection-notice"><b>审批边界：</b>申请人不能审批自己的申请；通过只表示获得受控执行资格，不会直接改变工单状态、SLA 或流程节点。</p>
  <p v-if="notice" class="form-alert form-alert--success">{{ notice }}</p><p v-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>
  <section class="panel table-panel"><div class="panel-header"><div><h3>Flowable 审批任务</h3><p>任务与工单均在服务端逐项核验；不返回无权工单数量。</p></div></div>
    <div v-if="loading" class="compact-loading">正在加载审批待办…</div>
    <div v-else-if="items.length" class="table-scroll"><table><thead><tr><th>工单</th><th>申请内容</th><th>审批规则</th><th>提单人</th><th>进入待办</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.approvalRequestId">
      <td><RouterLink class="ticket-id" :to="`/tickets/${item.ticketId}`">{{ item.ticketId }}</RouterLink><RouterLink class="ticket-title" :to="`/tickets/${item.ticketId}`">{{ item.ticketTitle }}</RouterLink><small class="table-subtext">{{ item.serviceCatalogItem.name }} · {{ item.ticketPriority }}</small></td>
      <td><b>{{ item.sourceNode }} → {{ item.targetNode }}</b><small class="table-subtext">{{ item.reason }}</small></td><td><b>{{ approvalRule(item) }}</b><small class="table-subtext">候选身份已在发起时冻结；此处不展示人员名单。</small></td><td>{{ item.requester.displayName }}<small class="table-subtext">申请人：{{ item.applicantIamUserId }}</small></td><td>{{ formatTime(item.engineTaskCreatedAt) }}</td>
      <td><div class="trusted-link-actions"><button class="button button--primary" type="button" :disabled="!item.canDecide" :title="item.disabledReason" @click="begin(item, 'APPROVED')">通过</button><button class="button button--secondary" type="button" :disabled="!item.canDecide" :title="item.disabledReason" @click="begin(item, 'REJECTED')">驳回</button></div><small v-if="item.disabledReason" class="table-subtext">{{ item.disabledReason }}</small></td>
    </tr></tbody></table></div>
    <div v-else class="empty-state compact-empty"><span class="empty-icon">✓</span><h3>暂无可处理审批</h3><p>当前角色没有处于 Flowable 候选状态且在数据范围内的审批任务。</p></div>
  </section>
  <section v-if="selected" class="panel form-panel approval-decision-panel"><div class="panel-header"><div><h3>{{ decision === 'APPROVED' ? '通过审批' : '驳回审批' }}</h3><p>{{ selected.ticketId }} · {{ selected.sourceNode }} → {{ selected.targetNode }}</p></div></div>
    <form class="form-grid" @submit.prevent="submit"><label class="field field--full"><span>审批意见 <b>*</b></span><textarea v-model="reason" maxlength="1000" rows="4" placeholder="至少 5 个字符，说明审批依据或驳回原因" required /></label><div class="form-actions"><button class="button button--secondary" type="button" :disabled="submitting" @click="cancel">取消</button><button class="button" :class="decision === 'APPROVED' ? 'button--primary' : 'button--secondary'" type="submit" :disabled="submitting || reason.trim().length < 5">{{ submitting ? '提交中…' : `确认${decision === 'APPROVED' ? '通过' : '驳回'}` }}</button></div></form>
  </section>
</template>
