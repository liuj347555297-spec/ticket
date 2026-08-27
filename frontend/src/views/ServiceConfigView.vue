<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { catalogApi, type PublishedServiceCatalogForm, type ServiceCatalogItem } from '@/api/catalog'
import { announcementApi, type ServiceAnnouncement } from '@/api/announcements'
import { ApiError } from '@/api/client'
import { useSessionStore } from '@/stores/session'

const items = ref<ServiceCatalogItem[]>([]), selectedId = ref(''), selectedForm = ref<PublishedServiceCatalogForm>()
const source = ref<'api' | 'demo'>('api'), error = ref(''), loadingForm = ref(false)
const session = useSessionStore()
const announcements = ref<ServiceAnnouncement[]>([]), showAnnouncementForm = ref(false), announcementSubmitting = ref(false), announcementError = ref(''), announcementNotice = ref('')
const announcementForm = ref({ title: '', body: '', audienceScope: 'ALL' as 'ALL' | 'ORGANIZATION', targetOrganizationIamId: '', pinned: false, effectiveUntil: '' })
const selected = computed(() => items.value.find((item) => item.id === selectedId.value))
const canManageAnnouncements = computed(() => session.authorization?.roles.some((role) => role === 'SERVICE_MANAGER' || role === 'PLATFORM_ADMIN') ?? false)
const typeLabel = (type: ServiceCatalogItem['ticketType']) => ({ INCIDENT: '故障报修', ACCESS_REQUEST: '账号权限', SERVICE_REQUEST: '服务请求', PROBLEM: '问题管理', CHANGE: '变更' })[type]
async function select(item: ServiceCatalogItem): Promise<void> {
  selectedId.value = item.id; selectedForm.value = undefined; error.value = ''; loadingForm.value = true
  try { const result = await catalogApi.getPublishedForm(item.id); selectedForm.value = result.data; if (result.source === 'demo') source.value = 'demo' }
  catch { error.value = '该目录表单暂不可用，请检查服务端授权或稍后重试。' }
  finally { loadingForm.value = false }
}
function openAnnouncementForm(): void {
  announcementError.value = ''; announcementNotice.value = ''
  announcementForm.value = { title: '', body: '', audienceScope: 'ALL', targetOrganizationIamId: session.currentUser?.organizationIamOrganizationId ?? '', pinned: false, effectiveUntil: '' }
  showAnnouncementForm.value = true
}
async function submitAnnouncement(): Promise<void> {
  const value = announcementForm.value; announcementError.value = ''; announcementNotice.value = ''
  if (!value.title.trim() || !value.body.trim() || !value.effectiveUntil || (value.audienceScope === 'ORGANIZATION' && !value.targetOrganizationIamId.trim())) { announcementError.value = '请填写标题、正文、有效期；指定组织范围时还必须填写组织 IAM ID。'; return }
  announcementSubmitting.value = true
  try {
    const created = await announcementApi.create({ title: value.title.trim(), body: value.body.trim(), audienceScope: value.audienceScope, targetOrganizationIamId: value.audienceScope === 'ORGANIZATION' ? value.targetOrganizationIamId.trim() : undefined, pinned: value.pinned, effectiveUntil: new Date(value.effectiveUntil).toISOString() })
    announcements.value = [created, ...announcements.value]; showAnnouncementForm.value = false; announcementNotice.value = '公告已发布。服务端已记录发布人、范围、有效期和审计事件。'
  } catch (cause) { announcementError.value = cause instanceof ApiError ? cause.message : '公告发布未完成。' } finally { announcementSubmitting.value = false }
}
onMounted(async () => {
  if (!session.authorization) { try { await session.loadCurrentUser() } catch { /* The backend will make the final authorization decision. */ } }
  try { const result = await catalogApi.listPublishedItems(); items.value = result.data.items; source.value = result.source; if (items.value[0]) await select(items.value[0]) }
  catch { error.value = '服务配置暂不可用，请检查后端服务或稍后重试。' }
  if (canManageAnnouncements.value) { try { announcements.value = await announcementApi.list(20) } catch { /* Announcement management remains unavailable without affecting catalog read-only views. */ } }
})
</script>

<template>
  <div class="page-heading"><div><h2>服务配置</h2><p>展示当前主体可见的已发布目录、动态表单和标签策略；草稿、审批及管理动作须走受控管理接口。</p></div><span class="readonly-badge">已发布配置只读预览</span></div>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在 API 确实不可用时展示，不代表生产配置。</p><p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <section class="panel config-catalog-panel"><div class="panel-header"><div><h3>可用服务目录</h3><p>服务端已按 IAM 身份、组织及服务数据范围过滤。</p></div><span class="readonly-badge">{{ items.length }} 项</span></div><div class="table-scroll"><table><thead><tr><th>目录名称</th><th>分类 / 类型</th><th>已发布表单版本</th><th>标准标签</th><th>表单摘要</th></tr></thead><tbody><tr v-for="item in items" :key="item.id" :class="{ 'config-row--selected': item.id === selected?.id }" @click="select(item)"><td><b>{{ item.name }}</b><span class="table-subtext">{{ item.code }}</span></td><td>{{ item.categoryCode }}<span class="table-subtext">{{ typeLabel(item.ticketType) }}</span></td><td>v{{ item.publishedVersion }}</td><td>{{ item.tags?.length ?? 0 }}</td><td>{{ item.summary ?? '—' }}</td></tr></tbody></table></div></section>
  <template v-if="selected"><section class="config-summary"><div><b>{{ selected.name }}</b><span>{{ selected.summary ?? '当前目录未提供摘要。' }}</span></div><div class="tag-row"><span class="tag tag--blue">{{ typeLabel(selected.ticketType) }}</span><span class="tag tag--muted">v{{ selected.publishedVersion }}</span></div></section>
    <p v-if="loadingForm" class="compact-loading">正在读取已发布表单…</p>
    <div v-else-if="selectedForm" class="config-workspace"><section class="panel"><div class="panel-header"><div><h3>动态表单字段</h3><p>字段定义来自已发布版本，前端只负责安全呈现。</p></div></div><div class="field-config-list"><article v-for="field in selectedForm.fields" :key="field.code"><div><b>{{ field.label }} <em v-if="field.required">必填</em></b><small>{{ field.code }} · {{ field.sensitivity }} / {{ field.masking }}</small></div><span>{{ field.type }}<small v-if="field.dictionaryCode"> · {{ field.dictionaryCode }}</small></span></article></div></section>
      <section class="panel"><div class="panel-header"><div><h3>标签与字段策略</h3><p>目录标签及字段可见性仍由服务端再次校验。</p></div></div><div class="tag-row config-tags"><span v-for="tag in selected.tags" :key="tag.code" class="tag tag--blue">{{ tag.name }}</span></div><div class="config-note"><b>标签策略</b><span>标准标签：{{ selectedForm.tagPolicy.allowStandardTags ? '允许' : '不允许' }}；自定义标签：{{ selectedForm.tagPolicy.allowFreeTags ? '允许' : '不允许' }}；最多 {{ selectedForm.tagPolicy.maxTags }} 个。</span></div><div class="config-note"><b>规则案例</b><span>匹配接口只返回当前主体可见、已脱敏的建议，不暴露规则表达式或内部路由。</span></div></section></div>
  </template>
  <section v-if="canManageAnnouncements" class="panel announcement-admin-panel"><div class="panel-header"><div><h3>公告管理</h3><p>只显示当前管理员可见的有效公告；发布范围、有效期和权限均由服务端校验。</p></div><button class="button button--primary" type="button" @click="openAnnouncementForm">发布公告</button></div><p v-if="announcementNotice" class="form-alert form-alert--success">{{ announcementNotice }}</p><div class="table-scroll"><table><thead><tr><th>公告</th><th>阅读范围</th><th>有效至</th><th>状态</th></tr></thead><tbody><tr v-for="item in announcements" :key="item.id"><td><b>{{ item.title }}</b><small class="table-subtext">{{ item.body }}</small></td><td>{{ item.audienceScope === 'ALL' ? '全员' : '指定组织' }}</td><td>{{ new Date(item.effectiveUntil).toLocaleString('zh-CN', { hour12: false }) }}</td><td><span class="tag" :class="item.pinned ? 'tag--orange' : 'tag--muted'">{{ item.pinned ? '置顶' : '有效' }}</span></td></tr><tr v-if="!announcements.length"><td colspan="4" class="table-empty">当前范围内暂无有效公告。</td></tr></tbody></table></div></section>
  <div v-if="showAnnouncementForm" class="modal-backdrop" @mousedown.self="showAnnouncementForm = false"><section class="action-modal" role="dialog" aria-modal="true" aria-label="发布公告"><div class="modal-heading"><div><span class="eyebrow">公告管理</span><h3>发布公告</h3><p>服务端会以当前 IAM 身份写入发布人和审计记录；不接收浏览器传入的发布人。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="showAnnouncementForm = false">×</button></div><form class="action-form" @submit.prevent="submitAnnouncement"><label class="field"><span>公告标题 <b>*</b></span><input v-model.trim="announcementForm.title" maxlength="200" placeholder="例如：周末服务窗口调整" /></label><label class="field"><span>公告正文 <b>*</b></span><textarea v-model.trim="announcementForm.body" maxlength="4000" rows="4" placeholder="填写服务影响、时间和用户需要采取的操作。请勿填写密码、令牌或敏感配置。" /></label><label class="field"><span>阅读范围 <b>*</b></span><select v-model="announcementForm.audienceScope"><option value="ALL">全员</option><option value="ORGANIZATION">指定组织</option></select></label><label v-if="announcementForm.audienceScope === 'ORGANIZATION'" class="field"><span>组织 IAM ID <b>*</b></span><input v-model.trim="announcementForm.targetOrganizationIamId" maxlength="128" placeholder="例如 ORG-LOCAL-IT" /><small>服务端不接受组织名称，并在发布时校验数据范围。</small></label><label class="field"><span>有效至 <b>*</b></span><input v-model="announcementForm.effectiveUntil" type="datetime-local" /></label><label class="checkbox-field"><input v-model="announcementForm.pinned" type="checkbox" />置顶显示</label><p v-if="announcementError" class="form-alert form-alert--error">{{ announcementError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="announcementSubmitting" @click="showAnnouncementForm = false">取消</button><button class="button button--primary" type="submit" :disabled="announcementSubmitting">{{ announcementSubmitting ? '发布中…' : '确认发布' }}</button></div></form></section></div>
</template>
