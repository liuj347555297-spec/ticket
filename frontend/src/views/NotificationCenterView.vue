<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { extractTicketId, notificationApi, type NotificationCategory, type NotificationDelivery, type NotificationItem, type NotificationReadState } from '@/api/notifications'
import { useNotificationStore } from '@/stores/notifications'

const router = useRouter()
const notificationStore = useNotificationStore()
const items = ref<NotificationItem[]>([])
const selected = ref<NotificationItem | null>(null)
const deliveries = ref<NotificationDelivery[]>([])
const category = ref<NotificationCategory | ''>('')
const readState = ref<NotificationReadState | ''>('')
const groupedByTicket = ref(false)
const source = ref<'api' | 'demo'>('api')
const loading = ref(true)
const markingRead = ref(false)
const errorMessage = ref('')

const categoryNames: Record<NotificationCategory, string> = { TICKET: '工单', WORKFLOW: '流程待办', SLA: 'SLA', SYSTEM: '系统', INTEGRATION: '集成' }
const channelNames = { IN_APP: '站内信', WPS_IM: 'WPS IM', WECHAT_WORK: '企业微信' } as const
const deliveryNames = { PENDING: '待投递', DELIVERING: '投递中', DELIVERED: '已投递', RETRY_SCHEDULED: '待重试', FAILED_FINAL: '最终失败', SUPPRESSED: '策略抑制' } as const
const selectedTicketId = computed(() => selected.value && extractTicketId(selected.value))
const groups = computed(() => {
  const map = new Map<string, NotificationItem[]>()
  for (const item of items.value) { const key = extractTicketId(item) ?? 'SYSTEM'; map.set(key, [...(map.get(key) ?? []), item]) }
  return [...map.entries()].map(([key, groupedItems]) => ({ key, label: key === 'SYSTEM' ? '系统与集成通知' : key, items: groupedItems }))
})

function formatTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) }
function isRead(item: NotificationItem): boolean { return item.readState === 'READ' }
function deliveryClass(state: NotificationDelivery['state']): string { return state === 'DELIVERED' ? 'channel-status--delivered' : state === 'PENDING' || state === 'DELIVERING' || state === 'RETRY_SCHEDULED' ? 'channel-status--pending' : state === 'FAILED_FINAL' ? 'channel-status--failed' : '' }

async function loadDeliveries(item: NotificationItem | null): Promise<void> {
  deliveries.value = []
  if (!item) return
  try { deliveries.value = (await notificationApi.listDeliveries(item.id)).data.items } catch { /* delivery receipt is optional presentation data */ }
}

async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try {
    const result = await notificationApi.list({ page: 1, pageSize: 100, category: category.value || undefined, readState: readState.value || undefined })
    items.value = result.data.items; source.value = result.source
    if (!selected.value || !items.value.some((item) => item.id === selected.value?.id)) selected.value = items.value[0] ?? null
    await loadDeliveries(selected.value)
    await notificationStore.loadUnreadCount()
  } catch (error) {
    items.value = []; selected.value = null; deliveries.value = []
    if (error instanceof ApiError && error.status === 401) {
      await router.push({ path: '/login', query: { returnTo: '/notifications' } })
      return
    }
    errorMessage.value = error instanceof ApiError ? error.message : '通知加载失败，请稍后重试。'
  } finally { loading.value = false }
}

async function select(item: NotificationItem): Promise<void> { selected.value = item; await loadDeliveries(item) }

async function markSelectedRead(): Promise<void> {
  if (!selected.value || isRead(selected.value) || markingRead.value) return
  const current = selected.value
  markingRead.value = true
  try {
    const result = await notificationApi.markRead(current)
    selected.value = result.data
    items.value = items.value.map((item) => item.id === result.data.id ? result.data : item)
    notificationStore.markLocallyRead()
  } catch (error) { errorMessage.value = error instanceof ApiError ? error.message : '标记已读失败，请刷新后重试。' }
  finally { markingRead.value = false }
}

function internalTicketPath(ticketId: string): string { return `/tickets/${encodeURIComponent(ticketId)}` }
async function goTicket(): Promise<void> {
  if (!selectedTicketId.value) return
  await markSelectedRead()
  try { await router.push(internalTicketPath(selectedTicketId.value)) }
  catch { await router.push({ path: '/login', query: { returnTo: internalTicketPath(selectedTicketId.value) } }) }
}

onMounted(load)
</script>

<template>
  <div class="page-heading"><div><h2>消息中心</h2><p>仅展示当前 IAM 会话消息；投递与对象访问均由服务端重新校验。</p></div></div>
  <p v-if="source === 'demo'" class="demo-notice">演示通知：仅用于开发预览，未连接真实收件人、WPS IM 或企业微信。</p>
  <section class="panel notification-filter-panel"><form class="notification-filter" @submit.prevent="load"><label class="field"><span>通知类别</span><select v-model="category"><option value="">全部类别</option><option v-for="(label, value) in categoryNames" :key="value" :value="value">{{ label }}</option></select></label><label class="field"><span>阅读状态</span><select v-model="readState"><option value="">全部</option><option value="UNREAD">未读</option><option value="READ">已读</option></select></label><label class="notification-check"><input v-model="groupedByTicket" type="checkbox" />按工单聚合</label><button class="button button--secondary" type="submit">筛选</button></form></section>
  <section class="notification-workspace"><div class="panel notification-list-panel"><div class="panel-header"><div><h3>{{ groupedByTicket ? '工单聚合视图' : '通知列表' }}</h3><p>未读 / 全部、类别筛选均由服务端当前收件箱返回。</p></div></div><p v-if="loading" class="workflow-unavailable">正在加载通知…</p><p v-else-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>
    <template v-else-if="items.length"><div v-if="groupedByTicket" class="notification-group-list"><section v-for="group in groups" :key="group.key"><h4>{{ group.label }} <small>{{ group.items.length }} 条</small></h4><button v-for="item in group.items" :key="item.id" class="notification-row" :class="{ 'is-selected': item.id === selected?.id, 'is-unread': !isRead(item) }" type="button" @click="select(item)"><span class="notification-dot" aria-hidden="true"></span><span class="notification-main"><b>{{ item.title }}</b><small>{{ categoryNames[item.category] }} · {{ formatTime(item.createdAt) }}</small></span><span v-if="!isRead(item)" class="unread-label">未读</span></button></section></div><div v-else class="notification-list"><button v-for="item in items" :key="item.id" class="notification-row" :class="{ 'is-selected': item.id === selected?.id, 'is-unread': !isRead(item) }" type="button" @click="select(item)"><span class="notification-dot" aria-hidden="true"></span><span class="notification-main"><b>{{ item.title }}</b><small>{{ categoryNames[item.category] }} · {{ formatTime(item.createdAt) }}</small></span><span v-if="!isRead(item)" class="unread-label">未读</span></button></div></template><p v-else class="workflow-unavailable">没有符合条件的通知。</p></div>
    <aside class="panel notification-detail-panel"><template v-if="selected"><div class="panel-header"><div><span class="eyebrow">{{ categoryNames[selected.category] }} · {{ isRead(selected) ? '已读' : '未读' }}</span><h3>{{ selected.title }}</h3></div></div><p class="notification-content">{{ selected.body }}</p><dl class="notification-meta"><div><dt>发送时间</dt><dd>{{ formatTime(selected.createdAt) }}</dd></div><div><dt>来源摘要</dt><dd>{{ selected.sourceDisplayReference ?? '服务端未返回来源摘要' }}</dd></div><div><dt>通道投递</dt><dd><span v-for="delivery in deliveries" :key="delivery.id" class="channel-status" :class="deliveryClass(delivery.state)">{{ channelNames[delivery.channel] }}：{{ deliveryNames[delivery.state] }}<template v-if="delivery.attemptCount">（{{ delivery.attemptCount }} 次）</template></span><span v-if="!deliveries.length" class="channel-status">未返回投递回执</span></dd></div></dl><p class="notification-channel-note">站内信是安全兜底通道；外部 IM 状态只来自服务端脱敏回执。页面不保存凭证、不传递收件人，也不能重投。</p><div class="modal-actions"><button v-if="!isRead(selected)" class="button button--secondary" type="button" :disabled="markingRead" @click="markSelectedRead">{{ markingRead ? '处理中…' : '快捷标已读' }}</button><button v-if="selectedTicketId" class="button button--primary" type="button" @click="goTicket">进入关联工单</button></div></template><p v-else class="workflow-unavailable">选择一条通知查看详情。</p></aside></section>
  <section class="panel notification-preference-panel"><div class="panel-header"><div><h3>提醒偏好</h3><p>仅普通提醒允许配置；关键待办、审批与 SLA 预警由服务端强制投递，不能关闭。</p></div><span class="readonly-badge">关键待办强制</span></div><div class="preference-list"><label><input type="checkbox" checked disabled />普通工单状态更新（可配置接口预留）</label><label><input type="checkbox" checked disabled />关键待办 / 审批 / SLA 预警（不可关闭）</label></div></section>
</template>
