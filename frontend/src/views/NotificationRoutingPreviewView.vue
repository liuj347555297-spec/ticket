<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { notificationApi, type NotificationChannel, type NotificationRoutingEvent, type NotificationRoutingPreview } from '@/api/notifications'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const event = ref<NotificationRoutingEvent>('WORKFLOW_TASK_CREATED')
const preview = ref<NotificationRoutingPreview | null>(null)
const source = ref<'api' | 'demo'>('api')
const loading = ref(false)
const errorMessage = ref('')
const channelNames: Record<NotificationChannel, string> = { IN_APP: '站内信', WPS_IM: 'WPS IM', WECHAT_WORK: '企业微信' }
const currentOrganization = computed(() => session.currentUser)

async function loadPreview(): Promise<void> {
  if (!currentOrganization.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await notificationApi.previewRoutingRule(currentOrganization.value.organizationIamOrganizationId, event.value)
    preview.value = result.data
    source.value = result.source
  } catch (error) {
    preview.value = null
    errorMessage.value = error instanceof ApiError ? error.message : '当前身份无可查看的路由预览，或路由服务暂不可用。'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!session.currentUser && !session.loading) await session.loadCurrentUser()
  await loadPreview()
})
</script>

<template>
  <div class="page-heading"><div><h2>消息路由预览</h2><p>只读预览当前 IAM 组织的事件→通道路由；生产投递时仍由服务端重新计算。</p></div><span class="readonly-badge">受控只读</span></div>
  <p v-if="source === 'demo'" class="demo-notice">演示数据：仅用于开发预览，不代表真实组织、收件人或外部 IM 投递配置。</p>
  <p class="projection-notice">不展示收件人、人员名单、外部 IM 标识、路由表达式、地址、凭证或密钥。此页面没有编辑规则、指定组织、任意收件人或触发投递的能力。</p>
  <section class="panel routing-preview-panel"><div class="panel-header"><div><h3>当前组织命中规则</h3><p>仅平台管理员或审计角色可由服务端授权读取。</p></div></div>
    <p v-if="!currentOrganization" class="workflow-unavailable">正在读取当前 IAM 组织投影…</p>
    <template v-else><form class="routing-preview-form" @submit.prevent="loadPreview"><label class="field"><span>当前组织</span><div class="readonly-field">{{ currentOrganization.organizationName }}</div></label><label class="field"><span>事件</span><select v-model="event"><option value="WORKFLOW_TASK_CREATED">流程任务创建</option><option value="TICKET_ASSIGNED">工单分派</option><option value="TICKET_STATUS_CHANGED">工单状态变更</option><option value="SLA_BREACH_RISK">SLA 违约风险</option><option value="INTEGRATION_ALERT">集成告警</option></select></label><button class="button button--secondary" type="submit" :disabled="loading">{{ loading ? '读取中…' : '预览命中' }}</button></form>
      <p v-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>
      <dl v-else-if="preview" class="notification-meta routing-preview-meta"><div><dt>组织范围</dt><dd>{{ currentOrganization.organizationName }}</dd></div><div><dt>事件</dt><dd>{{ preview.event }}</dd></div><div><dt>解析结果</dt><dd><span class="channel-status channel-status--delivered">{{ preview.resolution }}</span></dd></div><div><dt>请求通道</dt><dd>{{ preview.requestedChannel ? channelNames[preview.requestedChannel] : '服务端未返回' }}</dd></div><div><dt>最终通道</dt><dd>{{ preview.resolvedChannel ? channelNames[preview.resolvedChannel] : '服务端未返回' }}<span v-if="preview.inAppFallbackApplied" class="channel-status">已应用站内信回退</span></dd></div></dl>
      <div v-if="preview?.matchedRule" class="routing-match-summary"><b>命中已发布规则</b><span>规则 {{ preview.matchedRule.id }} · v{{ preview.matchedRule.version }} · 优先级 {{ preview.matchedRule.priority }}</span><small>{{ preview.matchedRule.includeDescendants ? '包含下级组织' : '仅当前组织' }} · 聚合窗 {{ preview.matchedRule.aggregationWindowSeconds }} 秒 · {{ preview.matchedRule.lifecycleStatus }}</small></div>
      <p v-else-if="loading" class="workflow-unavailable">正在计算服务端路由预览…</p></template>
  </section>
</template>
