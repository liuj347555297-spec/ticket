<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi, type DictionaryEntry, type FormField, type PublishedServiceCatalogForm, type RuleMatch, type ServiceCatalogItem } from '@/api/catalog'
import { ApiError } from '@/api/client'
import { ticketApi, type TagKind, type TicketCreateRequest, type TicketType } from '@/api/tickets'

type FieldValues = Record<string, string | boolean | string[]>
const router = useRouter()
const submitting = ref(false), previewing = ref(false), catalogLoading = ref(true), formLoading = ref(false)
const submitError = ref(''), submitNotice = ref(''), previewError = ref(''), customTag = ref('')
const matchedRules = ref<RuleMatch[]>([]), items = ref<ServiceCatalogItem[]>([]), loadedForm = ref<PublishedServiceCatalogForm>()
const dictionaryOptions = ref<Record<string, DictionaryEntry[]>>({}), catalogSource = ref<'api' | 'demo'>('api')
const fieldValues = ref<FieldValues>({})
const form = ref({ catalogId: '', type: 'INCIDENT' as TicketType, title: '', description: '', tags: [] as string[] })
const selectedItem = computed(() => items.value.find((item) => item.id === form.value.catalogId))
const suggestedTags = computed(() => selectedItem.value?.tags?.map((tag) => tag.name) ?? [])
const effectiveMaxTags = computed(() => loadedForm.value?.tagPolicy.maxTags ?? 0)
function valueOf(code: string): string { const value = fieldValues.value[code]; return typeof value === 'string' ? value : '' }
function valuesOf(code: string): string[] { const value = fieldValues.value[code]; return Array.isArray(value) ? value : [] }
function boolOf(code: string): boolean { return fieldValues.value[code] === true }
function setValue(code: string, value: string): void { fieldValues.value[code] = value; matchedRules.value = [] }
function setBoolean(code: string, value: boolean): void { fieldValues.value[code] = value; matchedRules.value = [] }
function toggleMultiValue(code: string, value: string): void { const current = valuesOf(code); fieldValues.value[code] = current.includes(value) ? current.filter((item) => item !== value) : [...current, value]; matchedRules.value = [] }
function optionsFor(field: FormField): DictionaryEntry[] { return field.dictionaryCode ? dictionaryOptions.value[field.dictionaryCode] ?? [] : [] }
function resetFormValues(schema: PublishedServiceCatalogForm): void {
  fieldValues.value = {}; matchedRules.value = []; previewError.value = ''
  form.value.type = schema.serviceCatalogItem.ticketType
  form.value.tags = (schema.serviceCatalogItem.tags ?? []).slice(0, 2).map((tag) => tag.name)
  for (const field of schema.fields) fieldValues.value[field.code] = ['MULTI_SELECT', 'CHECKBOX_GROUP'].includes(field.type) ? [] : field.type === 'BOOLEAN' ? false : ''
}
async function loadForm(itemId: string): Promise<void> {
  formLoading.value = true; loadedForm.value = undefined; dictionaryOptions.value = {}
  try {
    const result = await catalogApi.getPublishedForm(itemId); loadedForm.value = result.data; if (result.source === 'demo') catalogSource.value = 'demo'; resetFormValues(result.data)
    const dictionaryCodes = [...new Set(result.data.fields.flatMap((field) => field.dictionaryCode ? [field.dictionaryCode] : []))]
    const responses = await Promise.all(dictionaryCodes.map(async (code) => [code, await catalogApi.listDictionaryEntries(code, itemId)] as const))
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
async function preview(): Promise<void> {
  previewError.value = ''; matchedRules.value = []
  if (!selectedItem.value || !loadedForm.value) { previewError.value = '请等待已发布表单加载完成。'; return }
  previewing.value = true
  try { const result = await catalogApi.matchRules({ serviceCatalogItemId: selectedItem.value.id, formVersion: loadedForm.value.formVersion, title: form.value.title.trim(), description: form.value.description.trim(), structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })) }); matchedRules.value = result.data.matches }
  catch (error) { previewError.value = error instanceof ApiError ? error.message : '案例预览暂不可用，请稍后重试。' }
  finally { previewing.value = false }
}
async function submit(): Promise<void> {
  submitError.value = ''; submitNotice.value = ''
  if (!selectedItem.value || !loadedForm.value) { submitError.value = '请选择服务目录并等待已发布表单加载完成。'; return }
  if (form.value.title.trim().length < 4) { submitError.value = '请填写至少 4 个字符的工单主题。'; return }
  if (!form.value.description.trim()) { submitError.value = '请说明问题现象或服务诉求。'; return }
  const missing = loadedForm.value.fields.find((field) => field.required && (Array.isArray(fieldValues.value[field.code]) ? valuesOf(field.code).length === 0 : field.type === 'BOOLEAN' ? !boolOf(field.code) : !valueOf(field.code).trim()))
  if (missing) { submitError.value = `请补充必填字段：${missing.label}。`; return }
  submitting.value = true
  const request: TicketCreateRequest = { serviceCatalogItemId: selectedItem.value.id, serviceCatalogFormVersion: loadedForm.value.formVersion, type: selectedItem.value.ticketType, title: form.value.title.trim(), description: form.value.description.trim(), structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })) }
  try { const result = await ticketApi.create(request); submitNotice.value = result.source === 'demo' ? '演示工单已创建，正在打开详情。' : '工单已提交，正在打开详情。'; await router.push(`/tickets/${result.data.id}`) }
  catch (error) { submitError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试。' }
  finally { submitting.value = false }
}
onMounted(async () => { try { const result = await catalogApi.listPublishedItems(); items.value = result.data.items; catalogSource.value = result.source; if (items.value[0]) { form.value.catalogId = items.value[0].id; await loadForm(items.value[0].id) } } catch { submitError.value = '服务目录暂不可用，无法安全发起工单。' } finally { catalogLoading.value = false } })
</script>

<template>
  <div class="page-heading"><div><h2>发起工单</h2><p>优先选择服务目录并填写标准字段；提交人、组织、优先级与处理人由后端确定。</p></div></div>
  <p v-if="catalogSource === 'demo'" class="demo-notice">开发演示目录：仅在 API 确实不可用时展示；提交时仍由服务端校验目录、版本、字段、权限与路由规则。</p>
  <form class="ticket-create-layout" @submit.prevent="submit"><section class="panel form-panel"><div class="panel-header"><div><h3>服务与问题描述</h3><p>目录决定已发布表单、流程、审批、分派与 SLA 规则。</p></div></div>
    <p v-if="catalogLoading" class="compact-loading">正在加载服务目录…</p><div v-else class="form-grid">
      <label class="field field--full"><span>服务目录 <b>*</b></span><select v-model="form.catalogId" :disabled="!items.length" @change="onCatalogChange"><option v-for="item in items" :key="item.id" :value="item.id">{{ item.name }}</option></select><small>{{ selectedItem?.summary ?? '当前没有可用服务目录。' }}</small></label>
      <label class="field"><span>工单类型</span><input :value="form.type === 'INCIDENT' ? '故障报修' : form.type === 'ACCESS_REQUEST' ? '账号权限' : '服务请求'" readonly /></label><div class="field field--catalog-note"><span>已发布表单版本</span><div class="readonly-field">{{ loadedForm ? `v${loadedForm.formVersion} · ${selectedItem?.code}` : '加载中…' }}</div><small>服务端按此版本重新校验。</small></div>
      <label class="field field--full"><span>工单主题 <b>*</b></span><input v-model="form.title" maxlength="200" placeholder="例如：ERP 采购订单页面加载缓慢" @input="matchedRules = []" /></label><label class="field field--full"><span>问题现象 / 服务说明 <b>*</b></span><textarea v-model="form.description" maxlength="4000" rows="4" placeholder="请填写发生时间、影响范围、已尝试操作及报错信息；避免填写密码、令牌等敏感信息。" @input="matchedRules = []"></textarea><small>{{ form.description.length }}/4000</small></label>
      <p v-if="formLoading" class="compact-loading field--full">正在加载动态字段与可用字典…</p>
      <template v-for="field in loadedForm?.fields" :key="field.code"><label v-if="isChoice(field)" class="field"><span>{{ field.label }} <b v-if="field.required">*</b></span><select :value="valueOf(field.code)" @change="setValue(field.code, ($event.target as HTMLSelectElement).value)"><option value="">请选择</option><option v-for="option in optionsFor(field)" :key="option.code" :value="option.code">{{ option.label }}</option></select><small v-if="field.helpText">{{ field.helpText }}</small></label><fieldset v-else-if="isMulti(field)" class="field field--full multi-field"><legend>{{ field.label }} <b v-if="field.required">*</b></legend><div class="choice-checks"><label v-for="option in optionsFor(field)" :key="option.code"><input type="checkbox" :checked="valuesOf(field.code).includes(option.code)" @change="toggleMultiValue(field.code, option.code)" /> {{ option.label }}</label></div></fieldset><label v-else-if="field.type === 'TEXTAREA'" class="field field--full"><span>{{ field.label }} <b v-if="field.required">*</b></span><textarea :value="valueOf(field.code)" :maxlength="field.validation?.maxLength" rows="3" @input="setValue(field.code, ($event.target as HTMLTextAreaElement).value)"></textarea></label><label v-else-if="isDate(field)" class="field"><span>{{ field.label }} <b v-if="field.required">*</b></span><input :type="field.type === 'DATE' ? 'date' : 'datetime-local'" :value="valueOf(field.code)" @input="setValue(field.code, ($event.target as HTMLInputElement).value)" /></label><label v-else-if="field.type === 'BOOLEAN'" class="field field--full boolean-field"><span><input type="checkbox" :checked="boolOf(field.code)" @change="setBoolean(field.code, ($event.target as HTMLInputElement).checked)" /> {{ field.label }} <b v-if="field.required">*</b></span><small v-if="field.helpText">{{ field.helpText }}</small></label><label v-else class="field"><span>{{ field.label }} <b v-if="field.required">*</b></span><input :value="valueOf(field.code)" :maxlength="field.validation?.maxLength" @input="setValue(field.code, ($event.target as HTMLInputElement).value)" /><small v-if="field.helpText">{{ field.helpText }}</small></label></template>
    </div></section>
    <aside class="form-sidebar"><section class="panel form-panel"><div class="panel-header"><div><h3>标签</h3><p>标准选项优先；自定义标签须符合已发布目录策略。</p></div></div><div class="tag-choice"><button v-for="tag in suggestedTags" :key="tag" class="tag-choice__item" :class="{ 'is-selected': form.tags.includes(tag) }" type="button" :disabled="!loadedForm?.tagPolicy.allowStandardTags" @click="toggleTag(tag)">{{ tag }}</button></div><div v-if="loadedForm?.tagPolicy.allowFreeTags" class="tag-adder"><input v-model="customTag" maxlength="50" placeholder="#自定义标签" @keyup.enter.prevent="addTag" /><button class="button button--secondary" type="button" @click="addTag">添加</button></div><div v-if="form.tags.length" class="tag-row selected-tags"><span v-for="tag in form.tags" :key="tag" class="tag tag--blue">{{ tag }} <button type="button" :aria-label="`移除 ${tag}`" @click="toggleTag(tag)">×</button></span></div></section>
      <section class="panel rule-hint"><div class="panel-header"><div><h3>案例匹配（参考）</h3><p>基于目录、字段、标签、错误码及关键词；不使用 AI，不执行自动操作。</p></div></div><button class="button button--secondary" type="button" :disabled="previewing || !loadedForm" @click="preview">{{ previewing ? '匹配中…' : '预览匹配案例' }}</button><p v-if="previewError" class="form-alert form-alert--error">{{ previewError }}</p><div v-else-if="matchedRules.length" class="case-preview-list"><article v-for="item in matchedRules" :key="item.ruleCode"><b>{{ item.suggestion.title }}</b><span>{{ item.suggestion.kind === 'KNOWLEDGE_ARTICLE' ? '知识建议' : '已解决案例' }}</span><small>{{ item.suggestion.summary }}</small></article></div><p v-else class="rule-hint__empty">填写后可预览相关已解决案例；结果仅供参考，不改变工单流程。</p></section></aside>
    <div class="form-actions"><p v-if="submitError" class="form-alert form-alert--error">{{ submitError }}</p><p v-else-if="submitNotice" class="form-alert form-alert--success">{{ submitNotice }}</p><RouterLink class="button button--secondary" to="/tickets">取消</RouterLink><button class="button button--primary" type="submit" :disabled="submitting || catalogLoading || formLoading || !loadedForm">{{ submitting ? '提交中…' : '提交工单' }}</button></div></form>
</template>
