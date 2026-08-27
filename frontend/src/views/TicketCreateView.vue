<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi, type DictionaryEntry, type FormField, type PublishedServiceCatalogForm, type RuleMatch, type ServiceCatalogItem } from '@/api/catalog'
import { ApiError } from '@/api/client'
import { ticketApi, type TagKind, type TicketCreateRequest, type TicketType } from '@/api/tickets'
import TicketRichTextEditor from '@/components/TicketRichTextEditor.vue'
import { useSessionStore } from '@/stores/session'

type FieldValues = Record<string, string | boolean | string[]>
const router = useRouter()
const session = useSessionStore()
const submitting = ref(false), previewing = ref(false), catalogLoading = ref(true), formLoading = ref(false)
const submitError = ref(''), submitNotice = ref(''), previewError = ref(''), customTag = ref('')
const matchedRules = ref<RuleMatch[]>([]), items = ref<ServiceCatalogItem[]>([]), loadedForm = ref<PublishedServiceCatalogForm>()
const dictionaryOptions = ref<Record<string, DictionaryEntry[]>>({}), catalogSource = ref<'api' | 'demo'>('api')
const fieldValues = ref<FieldValues>({})
const form = ref({ catalogId: '', type: 'INCIDENT' as TicketType, title: '', descriptionHtml: '', descriptionText: '', tags: [] as string[] })
const attachmentInput = ref<HTMLInputElement>(), imageInput = ref<HTMLInputElement>()
const pendingAttachments = ref<File[]>([]), pendingInlineImages = ref<File[]>([]), createdTicketId = ref('')
const activeTab = ref('basic')
const selectedItem = computed(() => items.value.find((item) => item.id === form.value.catalogId))
const suggestedTags = computed(() => selectedItem.value?.tags?.map((tag) => tag.name) ?? [])
const effectiveMaxTags = computed(() => loadedForm.value?.tagPolicy.maxTags ?? 0)
function descriptionTemplate(type: TicketType): string {
  const rows = type === 'ACCESS_REQUEST'
    ? ['【申请内容】', '【目标系统 / 角色】', '【使用范围】', '【有效期（如有）】', '【补充说明】']
    : type === 'SERVICE_REQUEST'
      ? ['【服务诉求】', '【期望完成时间】', '【使用场景】', '【补充说明】']
      : ['【发生时间】', '【影响范围】', '【问题现象 / 报错信息】', '【已尝试操作】', '【补充说明】']
  return rows.map((row) => `<p>${row}</p>`).join('')
}
function valueOf(code: string): string { const value = fieldValues.value[code]; return typeof value === 'string' ? value : '' }
function valuesOf(code: string): string[] { const value = fieldValues.value[code]; return Array.isArray(value) ? value : [] }
function boolOf(code: string): boolean { return fieldValues.value[code] === true }
function setValue(code: string, value: string): void { fieldValues.value[code] = value; matchedRules.value = [] }
function setBoolean(code: string, value: boolean): void { fieldValues.value[code] = value; matchedRules.value = [] }
function optionsFor(field: FormField): DictionaryEntry[] { return field.dictionaryCode ? dictionaryOptions.value[field.dictionaryCode] ?? [] : [] }
function resetFormValues(schema: PublishedServiceCatalogForm): void {
  fieldValues.value = {}; matchedRules.value = []; previewError.value = ''
  form.value.type = schema.serviceCatalogItem.ticketType
  form.value.tags = (schema.serviceCatalogItem.tags ?? []).slice(0, 2).map((tag) => tag.name)
  if (!form.value.descriptionText.trim()) {
    form.value.descriptionHtml = descriptionTemplate(schema.serviceCatalogItem.ticketType)
    form.value.descriptionText = schema.serviceCatalogItem.ticketType === 'ACCESS_REQUEST' ? '【申请内容】 【目标系统 / 角色】 【使用范围】 【有效期（如有）】 【补充说明】' : schema.serviceCatalogItem.ticketType === 'SERVICE_REQUEST' ? '【服务诉求】 【期望完成时间】 【使用场景】 【补充说明】' : '【发生时间】 【影响范围】 【问题现象 / 报错信息】 【已尝试操作】 【补充说明】'
  }
  for (const field of schema.fields) fieldValues.value[field.code] = ['MULTI_SELECT', 'CHECKBOX_GROUP'].includes(field.type) ? [] : field.type === 'BOOLEAN' ? false : ''
}
async function loadForm(itemId: string): Promise<void> {
  formLoading.value = true; loadedForm.value = undefined; dictionaryOptions.value = {}
  try {
    const result = await catalogApi.getPublishedForm(itemId); loadedForm.value = result.data; if (result.source === 'demo') catalogSource.value = 'demo'; resetFormValues(result.data)
    const dictionaryFields = result.data.fields.filter((field) => field.dictionaryCode)
    const responses = await Promise.all(dictionaryFields.map(async (field) => [field.dictionaryCode!, await catalogApi.listDictionaryEntries(field.dictionaryCode!, itemId, result.data.formVersion, field.code)] as const))
    dictionaryOptions.value = Object.fromEntries(responses.map(([code, response]) => { if (response.source === 'demo') catalogSource.value = 'demo'; return [code, response.data.items] }))
  } catch (error) { submitError.value = error instanceof ApiError ? error.message : '已发布表单暂不可用，无法安全发起工单。' }
  finally { formLoading.value = false }
}
async function onCatalogChange(): Promise<void> { submitError.value = ''; if (form.value.catalogId) await loadForm(form.value.catalogId) }
function addTag(): void {
  const value = customTag.value.trim(), tag = value ? (value.startsWith('#') ? value : `#${value.replaceAll('#', '')}`) : ''
  if (loadedForm.value?.tagPolicy.allowFreeTags && tag && !form.value.tags.includes(tag) && form.value.tags.length < effectiveMaxTags.value) form.value.tags.push(tag)
  customTag.value = ''; matchedRules.value = []
}
function toggleTag(tag: string): void { const index = form.value.tags.indexOf(tag); if (index >= 0) form.value.tags.splice(index, 1); else if (loadedForm.value?.tagPolicy.allowStandardTags && form.value.tags.length < effectiveMaxTags.value) form.value.tags.push(tag); matchedRules.value = [] }
function tagKind(name: string): TagKind { return suggestedTags.value.includes(name) ? 'STANDARD' : 'FREE' }
function structuredFields(): Record<string, string | boolean | string[]> { return Object.fromEntries(Object.entries(fieldValues.value).filter(([, value]) => Array.isArray(value) ? value.length > 0 : typeof value === 'boolean' ? value : value.trim().length > 0)) }
function isMulti(field: FormField): boolean { return field.type === 'MULTI_SELECT' || field.type === 'CHECKBOX_GROUP' }
function isChoice(field: FormField): boolean { return field.type === 'SINGLE_SELECT' || field.type === 'RADIO' }
function isDate(field: FormField): boolean { return field.type === 'DATE' || field.type === 'DATETIME' }
function requestAttachmentSelection(): void { attachmentInput.value?.click() }
function requestImageSelection(): void { imageInput.value?.click() }
function queueFiles(event: Event, inlineImage: boolean): void {
  const source = event.target as HTMLInputElement
  const files = Array.from(source.files ?? [])
  const target = inlineImage ? pendingInlineImages.value : pendingAttachments.value
  const accepted = inlineImage ? files.filter((file) => ['image/png', 'image/jpeg'].includes(file.type)) : files
  const remaining = 10 - pendingAttachments.value.length - pendingInlineImages.value.length
  target.push(...accepted.slice(0, Math.max(0, remaining)))
  source.value = ''
}
function removeQueuedFile(inlineImage: boolean, index: number): void { (inlineImage ? pendingInlineImages.value : pendingAttachments.value).splice(index, 1) }
function escapeHtml(value: string): string { return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;') }
async function uploadQueuedFiles(ticket: { id: string; version: number }): Promise<void> {
  for (const file of pendingAttachments.value) await ticketApi.uploadAttachment(ticket.id, file)
  const imageParts: string[] = []
  for (const file of pendingInlineImages.value) {
    const uploaded = await ticketApi.uploadAttachment(ticket.id, file)
    if (uploaded.scanStatus !== 'CLEAN' || !uploaded.detectedMediaType.startsWith('image/')) throw new Error(`图片 ${file.name} 未通过安全扫描`)
    imageParts.push(`<p><img src="/api/v1/tickets/${ticket.id}/attachments/${uploaded.id}/inline" alt="${escapeHtml(file.name)}"></p>`)
  }
  if (imageParts.length) await ticketApi.updateDescription(ticket.id, ticket.version, `${form.value.descriptionHtml}${imageParts.join('')}`)
}
async function preview(): Promise<void> {
  previewError.value = ''; matchedRules.value = []
  if (!selectedItem.value || !loadedForm.value) { previewError.value = '请等待已发布表单加载完成。'; return }
  previewing.value = true
  try { const result = await catalogApi.matchRules({ serviceCatalogItemId: selectedItem.value.id, formVersion: loadedForm.value.formVersion, title: form.value.title.trim(), description: form.value.descriptionText.trim(), structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })) }); matchedRules.value = result.data.matches }
  catch (error) { previewError.value = error instanceof ApiError ? error.message : '案例预览暂不可用，请稍后重试。' }
  finally { previewing.value = false }
}
async function submit(): Promise<void> {
  submitError.value = ''; submitNotice.value = ''
  if (!selectedItem.value || !loadedForm.value) { submitError.value = '请选择服务目录并等待已发布表单加载完成。'; return }
  if (form.value.title.trim().length < 4) { submitError.value = '请填写至少 4 个字符的工单主题。'; return }
  if (!form.value.descriptionText.trim()) { submitError.value = '请说明问题现象或服务诉求。'; return }
  const missing = loadedForm.value.fields.find((field) => field.required && (Array.isArray(fieldValues.value[field.code]) ? valuesOf(field.code).length === 0 : field.type === 'BOOLEAN' ? !boolOf(field.code) : !valueOf(field.code).trim()))
  if (missing) { submitError.value = `请补充必填字段：${missing.label}。`; return }
  submitting.value = true
  const request: TicketCreateRequest = { serviceCatalogItemId: selectedItem.value.id, serviceCatalogFormVersion: loadedForm.value.formVersion, type: selectedItem.value.ticketType, title: form.value.title.trim(), description: form.value.descriptionHtml, descriptionFormat: 'RICH_TEXT', structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })) }
  try {
    const result = await ticketApi.create(request)
    createdTicketId.value = result.data.id
    if (result.source !== 'demo' && (pendingAttachments.value.length || pendingInlineImages.value.length)) await uploadQueuedFiles(result.data)
    submitNotice.value = result.source === 'demo' ? '演示工单已创建，正在打开详情。' : '工单、附件和已插入图片均已安全提交，正在打开详情。'
    await router.push(`/tickets/${result.data.id}`)
  }
  catch (error) { submitError.value = createdTicketId.value ? `工单 ${createdTicketId.value} 已创建，但附件或图片未全部完成上传；请进入工单详情后重试。` : error instanceof ApiError ? error.message : '提交失败，请稍后重试。'; return }
  finally { submitting.value = false }
}
onMounted(async () => {
  if (!session.currentUser) { try { await session.loadCurrentUser() } catch { /* Identity remains server-authoritative at submit time. */ } }
  try { const result = await catalogApi.listPublishedItems(); items.value = result.data.items; catalogSource.value = result.source; if (items.value[0]) { form.value.catalogId = items.value[0].id; await loadForm(items.value[0].id) } } catch { submitError.value = '服务目录暂不可用，无法安全发起工单。' } finally { catalogLoading.value = false }
})
</script>

<template>
  <div class="page-heading"><div><h2>流程发起 · 工单</h2><p>提交人、组织等身份字段取自 IAM 只读投影；服务目录决定已发布表单、流程、审批、分派与 SLA。</p></div></div>
  <p v-if="catalogSource === 'demo'" class="demo-notice">开发演示目录：仅在 API 确实不可用时展示；提交时仍由服务端校验目录、版本、字段、权限与路由规则。</p>
  <form class="ticket-create-layout" @submit.prevent="submit"><section class="panel form-panel ticket-create-main"><el-form :model="form" label-position="top" class="ticket-create-el-form">
    <el-form-item class="field field--full ticket-title-field" required><template #label>主题</template><el-input v-model="form.title" maxlength="200" placeholder="请输入主题，例如：ERP 采购订单页面加载缓慢" @input="matchedRules = []" /></el-form-item>
    <el-tabs v-model="activeTab" class="ticket-create-tabs">
      <el-tab-pane label="基础信息" name="basic">
        <div class="form-section-heading">申请人信息 <small>由 IAM 同步，仅作身份快照展示</small></div>
        <div class="form-grid identity-form-grid">
          <el-form-item class="field" required><template #label>申请人</template><el-input :model-value="session.currentUser?.displayName ?? '正在读取 IAM 身份…'" readonly /></el-form-item>
          <el-form-item class="field" required><template #label>申请人部门</template><el-input :model-value="session.currentUser?.organizationName ?? '正在读取 IAM 组织…'" readonly /></el-form-item>
          <el-form-item class="field"><template #label>IAM 用户 ID</template><el-input :model-value="session.currentUser?.iamUserId ?? '—'" readonly /></el-form-item>
        </div>
        <div class="form-section-heading">工单基本信息 <small>目录字段可在后台按版本配置并发布</small></div>
        <p v-if="catalogLoading" class="compact-loading">正在加载服务目录…</p>
        <div v-else class="form-grid">
          <el-form-item class="field field--full" required><template #label>服务目录</template><el-select v-model="form.catalogId" :disabled="!items.length" @change="onCatalogChange"><el-option v-for="item in items" :key="item.id" :label="item.name" :value="item.id" /></el-select><small>{{ selectedItem?.summary ?? '当前没有可用服务目录。' }}</small></el-form-item>
          <el-form-item class="field"><template #label>工单类型</template><el-input :model-value="form.type === 'INCIDENT' ? '故障报修' : form.type === 'ACCESS_REQUEST' ? '账号权限' : '服务请求'" readonly /></el-form-item>
          <el-form-item class="field field--catalog-note"><template #label>已发布表单版本 <el-tooltip content="此版本由服务端在提交时再次校验；切换服务目录后会自动刷新。" placement="top"><span class="form-help-trigger" aria-label="显示帮助">?</span></el-tooltip></template><div class="readonly-field">{{ loadedForm ? `v${loadedForm.formVersion} · ${selectedItem?.code}` : '加载中…' }}</div></el-form-item>
          <p v-if="formLoading" class="compact-loading field--full">正在加载动态字段与可用字典…</p>
          <template v-for="field in loadedForm?.fields" :key="field.code"><el-form-item v-if="isChoice(field)" class="field" :required="field.required"><template #label>{{ field.label }}</template><el-select :model-value="valueOf(field.code)" placeholder="请选择" @update:model-value="setValue(field.code, $event)"><el-option v-for="option in optionsFor(field)" :key="option.code" :label="option.label" :value="option.code" /></el-select><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item><fieldset v-else-if="isMulti(field)" class="field field--full multi-field"><legend>{{ field.label }} <b v-if="field.required">*</b></legend><el-checkbox-group :model-value="valuesOf(field.code)" @update:model-value="fieldValues[field.code] = $event; matchedRules = []"><el-checkbox v-for="option in optionsFor(field)" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox></el-checkbox-group></fieldset><el-form-item v-else-if="field.type === 'TEXTAREA'" class="field field--full" :required="field.required"><template #label>{{ field.label }}</template><el-input :model-value="valueOf(field.code)" :maxlength="field.validation?.maxLength" type="textarea" :rows="3" @update:model-value="setValue(field.code, $event)" /></el-form-item><el-form-item v-else-if="isDate(field)" class="field" :required="field.required"><template #label>{{ field.label }}</template><el-date-picker :model-value="valueOf(field.code)" :type="field.type === 'DATE' ? 'date' : 'datetime'" value-format="YYYY-MM-DDTHH:mm:ss" @update:model-value="setValue(field.code, $event ?? '')" /></el-form-item><label v-else-if="field.type === 'BOOLEAN'" class="field field--full boolean-field"><span><input type="checkbox" :checked="boolOf(field.code)" @change="setBoolean(field.code, ($event.target as HTMLInputElement).checked)" /> {{ field.label }} <b v-if="field.required">*</b></span><small v-if="field.helpText">{{ field.helpText }}</small></label><el-form-item v-else class="field" :required="field.required"><template #label>{{ field.label }}</template><el-input :model-value="valueOf(field.code)" :maxlength="field.validation?.maxLength" @update:model-value="setValue(field.code, $event)" /><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item></template>
          <el-form-item class="field field--full ticket-description-field" required><template #label>问题现象 / 服务说明</template><TicketRichTextEditor v-model="form.descriptionHtml" :disabled="formLoading" @plain-text-change="form.descriptionText = $event; matchedRules = []" @request-image="requestImageSelection" /><div class="ticket-upload-zone"><input ref="imageInput" type="file" accept="image/png,image/jpeg" multiple hidden @change="queueFiles($event, true)" /><input ref="attachmentInput" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv" multiple hidden @change="queueFiles($event, false)" /><el-button size="small" plain type="primary" @click="requestAttachmentSelection">上传附件</el-button><span>图片先隔离扫描后插入正文末尾；附件与图片合计最多 10 个。</span></div><div v-if="pendingInlineImages.length || pendingAttachments.length" class="queued-file-list"><span v-for="(file, index) in pendingInlineImages" :key="`image-${file.name}-${index}`" class="tag tag--blue">图片：{{ file.name }} <button type="button" @click="removeQueuedFile(true, index)">×</button></span><span v-for="(file, index) in pendingAttachments" :key="`attachment-${file.name}-${index}`" class="tag tag--muted">附件：{{ file.name }} <button type="button" @click="removeQueuedFile(false, index)">×</button></span></div><small>仅支持受限文字格式、HTTP/HTTPS 链接，以及经扫描的 PNG/JPEG 图片；服务端会再次清洗并保留纯文本摘要。</small></el-form-item>
        </div>
      </el-tab-pane>
      <el-tab-pane label="关联工单" name="relations"><div class="tab-empty-state">工单创建后可在详情页通过“关联工单”添加关联、重复单、父子单、问题单或变更单；关联操作会进行对象权限校验并写入审计。</div></el-tab-pane>
      <el-tab-pane label="流程图" name="workflow"><div class="tab-empty-state">流程实例在提交后由 Flowable 创建。此处仅显示已发布目录对应的流程说明；实际节点、候选人和审批结果请在工单详情查看。</div></el-tab-pane>
    </el-tabs>
  </el-form></section>
    <aside class="form-sidebar"><section class="panel form-panel"><div class="panel-header"><div><h3>标签</h3><p>标准选项优先；自定义标签须符合已发布目录策略。</p></div></div><div class="tag-choice"><button v-for="tag in suggestedTags" :key="tag" class="tag-choice__item" :class="{ 'is-selected': form.tags.includes(tag) }" type="button" :disabled="!loadedForm?.tagPolicy.allowStandardTags" @click="toggleTag(tag)">{{ tag }}</button></div><div v-if="loadedForm?.tagPolicy.allowFreeTags" class="tag-adder"><input v-model="customTag" maxlength="50" placeholder="#自定义标签" @keyup.enter.prevent="addTag" /><button class="button button--secondary" type="button" @click="addTag">添加</button></div><div v-if="form.tags.length" class="tag-row selected-tags"><span v-for="tag in form.tags" :key="tag" class="tag tag--blue">{{ tag }} <button type="button" :aria-label="`移除 ${tag}`" @click="toggleTag(tag)">×</button></span></div></section>
      <section class="panel rule-hint"><div class="panel-header"><div><h3>案例匹配（参考）</h3><p>基于目录、字段、标签、错误码及关键词；不使用 AI，不执行自动操作。</p></div></div><button class="button button--secondary" type="button" :disabled="previewing || !loadedForm" @click="preview">{{ previewing ? '匹配中…' : '预览匹配案例' }}</button><p v-if="previewError" class="form-alert form-alert--error">{{ previewError }}</p><div v-else-if="matchedRules.length" class="case-preview-list"><article v-for="item in matchedRules" :key="item.ruleCode"><b>{{ item.suggestion.title }}</b><span>{{ item.suggestion.kind === 'KNOWLEDGE_ARTICLE' ? '知识建议' : '已解决案例' }}</span><small>{{ item.suggestion.summary }}</small></article></div><p v-else class="rule-hint__empty">填写后可预览相关已解决案例；结果仅供参考，不改变工单流程。</p></section></aside>
    <div class="form-actions"><p v-if="submitError" class="form-alert form-alert--error">{{ submitError }}</p><p v-else-if="submitNotice" class="form-alert form-alert--success">{{ submitNotice }}</p><RouterLink class="button button--secondary" to="/tickets">取消</RouterLink><button class="button button--primary" type="submit" :disabled="submitting || catalogLoading || formLoading || !loadedForm">{{ submitting ? '提交中…' : '提交工单' }}</button></div></form>
</template>
