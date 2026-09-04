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
function taskLabel(item: ApprovalTaskInboxItem): string {
  return ({ CONTROLLED_JUMP: '受控跳转审批', LIFECYCLE_ACTION: '生命周期审批', HANDOVER_CONFIRMATION: '交接班确认', COHANDLER_CONFIRMATION: '协办确认' } as const)[item.taskType]
}
function isConfirmation(item: ApprovalTaskInboxItem): boolean { return item.taskType === 'HANDOVER_CONFIRMATION' || item.taskType === 'COHANDLER_CONFIRMATION' }

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
    if (selected.value.taskType === 'CONTROLLED_JUMP') await ticketApi.decideControlledJump(selected.value.ticketId, selected.value.requestId, decision.value, reason.value.trim())
    else if (selected.value.taskType === 'LIFECYCLE_ACTION') await ticketApi.decideLifecycleActionApproval(selected.value.ticketId, selected.value.requestId, decision.value, reason.value.trim())
    else if (selected.value.taskType === 'HANDOVER_CONFIRMATION') await ticketApi.decideHandover(selected.value.ticketId, selected.value.requestId, decision.value === 'APPROVED' ? 'ACCEPTED' : 'REJECTED', reason.value.trim())
    else await ticketApi.decideCoHandler(selected.value.ticketId, selected.value.requestId, decision.value === 'APPROVED' ? 'ACCEPTED' : 'REJECTED', reason.value.trim())
    notice.value = decision.value === 'APPROVED' ? (isConfirmation(selected.value) ? '确认已提交，处理权限将由服务端和流程引擎同步更新。' : '审批结果已提交，工单动作仅会由服务端在流程完成后执行。') : '已拒绝该待办，流程引擎与审计记录已同步更新。'
    cancel(); await load()
  } catch (error) { errorMessage.value = error instanceof ApiError ? error.message : '审批提交失败；未假定流程状态已改变。' } finally { submitting.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><h2>我的审批与确认</h2><p>统一展示当前 IAM 身份已被 Flowable 指派、且仍通过工单对象授权校验的待办。</p></div><button class="button button--secondary" type="button" :disabled="loading" @click="load">刷新</button></div>
  <p class="projection-notice"><b>审批边界：</b>不展示冻结候选人或其数量；申请人不可自审，所有操作由服务端再次核验流程任务、对象权限与版本。</p>
  <p v-if="notice" class="form-alert form-alert--success">{{ notice }}</p><p v-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>
  <section class="panel table-panel"><div class="panel-header"><div><h3>Flowable 待办</h3><p>含受控跳转、生命周期审批、交接班确认与协办确认；不返回无权或失效任务的数量。</p></div></div>
    <div v-if="loading" class="compact-loading">正在加载审批待办…</div>
    <div v-else-if="items.length" class="table-scroll"><table><thead><tr><th>工单</th><th>待办类型</th><th>申请内容</th><th>提单人</th><th>进入待办</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="`${item.taskType}:${item.requestId}`">
      <td><RouterLink class="ticket-id" :to="`/tickets/${item.ticketId}`">{{ item.ticketId }}</RouterLink><RouterLink class="ticket-title" :to="`/tickets/${item.ticketId}`">{{ item.ticketTitle }}</RouterLink><small class="table-subtext">{{ item.serviceCatalogItem.name }} · {{ item.ticketPriority }}</small></td>
      <td><span class="tag tag--blue">{{ taskLabel(item) }}</span><small class="table-subtext">{{ item.actionCode }}</small></td><td><b>{{ item.actionCode }}</b><small class="table-subtext">{{ item.summary }}</small></td><td>{{ item.requester.displayName }}</td><td>{{ formatTime(item.engineTaskCreatedAt) }}</td>
      <td><div class="trusted-link-actions"><button class="button button--primary" type="button" @click="begin(item, 'APPROVED')">{{ isConfirmation(item) ? '确认' : '通过' }}</button><button class="button button--secondary" type="button" @click="begin(item, 'REJECTED')">{{ isConfirmation(item) ? '拒绝' : '驳回' }}</button></div></td>
    </tr></tbody></table></div>
    <div v-else class="empty-state compact-empty"><span class="empty-icon">✓</span><h3>暂无可处理审批</h3><p>当前角色没有处于 Flowable 候选状态且在数据范围内的审批任务。</p></div>
  </section>
  <section v-if="selected" class="panel form-panel approval-decision-panel"><div class="panel-header"><div><h3>{{ decision === 'APPROVED' ? (isConfirmation(selected) ? '确认待办' : '通过审批') : (isConfirmation(selected) ? '拒绝确认' : '驳回审批') }}</h3><p>{{ selected.ticketId }} · {{ taskLabel(selected) }} · {{ selected.actionCode }}</p></div></div>
    <form class="form-grid" @submit.prevent="submit"><label class="field field--full"><span>审批意见 <b>*</b></span><textarea v-model="reason" maxlength="1000" rows="4" placeholder="至少 5 个字符，说明审批依据或驳回原因" required /></label><div class="form-actions"><button class="button button--secondary" type="button" :disabled="submitting" @click="cancel">取消</button><button class="button" :class="decision === 'APPROVED' ? 'button--primary' : 'button--secondary'" type="submit" :disabled="submitting || reason.trim().length < 5">{{ submitting ? '提交中…' : `确认${decision === 'APPROVED' ? '通过' : '驳回'}` }}</button></div></form>
  </section>
</template>
