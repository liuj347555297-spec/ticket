<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { catalogApi, type DictionaryEntry, type FormCondition, type FormField, type PublishedServiceCatalogForm, type RuleMatch, type ServiceCatalogItem } from '@/api/catalog'
import { serviceSystemApi, type ServiceSystem, type ServiceSystemModule } from '@/api/service-systems'
import { servicePortalApi } from '@/api/service-portal'
import { matchesServiceSelection, parseServiceLaunch, type ServiceLaunchIntent } from '@/utils/serviceLaunch'
import { ApiError } from '@/api/client'
import { ticketApi, type TagKind, type TicketCreateRequest, type TicketType } from '@/api/tickets'
import TicketRichTextEditor from '@/components/TicketRichTextEditor.vue'
import WorkflowDiagramPanel from '@/components/WorkflowDiagramPanel.vue'
import { useSessionStore } from '@/stores/session'
import { readTicketDraft, removeTicketDraft, type TicketDraft, writeTicketDraft } from '@/utils/ticketDraft'
import { canReleaseSubmission, createSubmissionSession } from '@/utils/submissionSession'
import '@/styles/ticket-create-workspace.css'

type FieldValues = Record<string, string | boolean | string[]>
const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const submitting = ref(false), previewing = ref(false), catalogLoading = ref(true), formLoading = ref(false)
const submitError = ref(''), submitNotice = ref(''), previewError = ref(''), customTag = ref('')
const matchedRules = ref<RuleMatch[]>([]), items = ref<ServiceCatalogItem[]>([]), loadedForm = ref<PublishedServiceCatalogForm>()
const serviceSystems = ref<ServiceSystem[]>([]), systemModules = ref<ServiceSystemModule[]>([])
const systemRegistryLoading = ref(true), systemModulesLoading = ref(false), systemMappingLoading = ref(false), systemRegistryError = ref('')
const dictionaryOptions = ref<Record<string, DictionaryEntry[]>>({})
const fieldValues = ref<FieldValues>({})
const form = ref({ systemCode: '', moduleCode: '', catalogId: '', type: 'INCIDENT' as TicketType, title: '', descriptionHtml: '', descriptionText: '', tags: [] as string[] })
const attachmentInput = ref<HTMLInputElement>(), imageInput = ref<HTMLInputElement>()
const richTextEditor = ref<InstanceType<typeof TicketRichTextEditor>>()
const pendingAttachments = ref<File[]>([]), pendingInlineImages = ref<{ file: File; placeholderToken: string; previewUrl: string }[]>([]), createdTicketId = ref('')
const validationErrors = ref<Record<string, string>>({})
const activeTab = ref('basic')
const confirming = ref(false)
let viewDisposed = false
let selectionGeneration = 0, formGeneration = 0, sourceGeneration = 0
let launchSourcesInitialized = false, contextGeneration = 0
const scopeNeedsRefresh = ref(false)
const pendingLaunch = ref<ServiceLaunchIntent | null>(null), launchLoading = ref(false), launchError = ref('')
const restoringDraft = ref(false)
const creationUncertain = ref(false)
const creation = createSubmissionSession((request: TicketCreateRequest, key: string) => ticketApi.create(request, key))
type UploadState = 'WAITING' | 'UPLOADING' | 'UPLOADED' | 'BLOCKED' | 'UNKNOWN'
const uploadProgress = ref<{ name: string; kind: string; state: UploadState }[]>([])
const uploadStateNames: Record<UploadState, string> = { WAITING: '待上传', UPLOADING: '上传中', UPLOADED: '已上传并通过扫描', BLOCKED: '已接收，扫描未通过或待处理', UNKNOWN: '结果待核对，请勿重复上传' }
const imageLinkError = ref(false)
const formLocked = computed(() => confirming.value || submitting.value || creationUncertain.value || launchLoading.value || restoringDraft.value || session.loading || scopeNeedsRefresh.value || Boolean(createdTicketId.value))
const draftCandidate = ref<TicketDraft | null>(null)
const draftReady = ref(false), draftDirty = ref(false), allowNavigation = ref(false)
const suppressDraftTracking = ref(false)
const draftStatus = ref('')
let draftSaveTimer: ReturnType<typeof setTimeout> | undefined
// The local-dev backend bypasses only the unavailable enterprise scanner. Production builds keep
// the scan gate and do not render this convenience wording.
const localAttachmentScanBypass = import.meta.env.DEV
const attachmentQueueStatus = localAttachmentScanBypass ? '本地调试：提交后直接上传' : '待上传和扫描'
const inlineImageQueueStatus = localAttachmentScanBypass ? '本地调试：提交后直接上传' : '待隔离扫描'
const selectedItem = computed(() => items.value.find((item) => item.id === form.value.catalogId))
const selectedSystem = computed(() => serviceSystems.value.find((system) => system.systemCode === form.value.systemCode))
const selectedTypeLabel = computed(() => ({ INCIDENT: '故障报修', ACCESS_REQUEST: '账号权限', SERVICE_REQUEST: '服务请求', PROBLEM: '问题管理', CHANGE: '变更申请' })[form.value.type])
const selectableModules = computed(() => systemModules.value.filter((module) => module.active))
const mappedCatalogItems = computed(() => items.value)
const systemRoutingReady = computed(() => Boolean(form.value.systemCode) && !systemModulesLoading.value && !systemMappingLoading.value && !systemRegistryError.value)
const suggestedTags = computed(() => selectedItem.value?.tags?.map((tag) => tag.name) ?? [])
const effectiveMaxTags = computed(() => loadedForm.value?.tagPolicy.maxTags ?? 0)
const draftSubjectId = computed(() => session.currentUser ? `${session.currentUser.organizationIamOrganizationId}:${session.currentUser.iamUserId}` : '')
const validationSummary = computed(() => [...new Set(Object.values(validationErrors.value))])
const hasMeaningfulInput = computed(() => Boolean(form.value.systemCode || form.value.catalogId || form.value.title.trim() || form.value.descriptionText.trim() || Object.values(fieldValues.value).some(valuePresent)))
function publicApiError(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback
  if (error.status === 401) return '登录会话无效或已过期，请重新登录后再试。'
  if (error.status === 403) return '当前身份没有执行此操作的权限。'
  if (error.status === 404) return '相关服务配置不存在或已经下线，请重新选择。'
  if (error.status === 409) return '服务配置已发生变化，请刷新后重新确认。'
  if (error.status === 422) return '提交内容未通过校验，请检查标出的字段。'
  if (error.status === 429) return '操作过于频繁，请稍后再试。'
  if (error.status >= 500) return '服务暂时不可用，请稍后重试。'
  return fallback
}
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
function clearValidationError(code: string): void { if (validationErrors.value[code]) delete validationErrors.value[code] }
function fieldError(code: string): string | undefined { return validationErrors.value[code] }
function setValue(code: string, value: string): void { fieldValues.value[code] = value; clearValidationError(code); matchedRules.value = [] }
function setBoolean(code: string, value: boolean): void { fieldValues.value[code] = value; clearValidationError(code); matchedRules.value = [] }
function optionsFor(field: FormField): DictionaryEntry[] { return field.dictionaryCode ? dictionaryOptions.value[field.dictionaryCode] ?? [] : [] }
function fieldDefault(field: FormField): string | boolean | string[] {
  if (field.type === 'BOOLEAN') return field.defaultValue?.toLowerCase() === 'true'
  if (field.type === 'MULTI_SELECT' || field.type === 'CHECKBOX_GROUP') return field.defaultValue ? field.defaultValue.split(',').map((value) => value.trim()).filter(Boolean) : []
  return field.defaultValue ?? ''
}
function valuePresent(value: string | boolean | string[] | undefined): boolean {
  return Array.isArray(value) ? value.length > 0 : typeof value === 'string' ? value.trim().length > 0 : value !== undefined
}
function conditionMatches(condition: FormCondition): boolean {
  const actual = fieldValues.value[condition.fieldCode]
  const values = condition.values ?? []
  const matches = (expected: string): boolean => Array.isArray(actual) ? actual.some((value) => value === expected) : actual === expected
  switch (condition.operator) {
    case 'HAS_VALUE': return valuePresent(actual)
    case 'NO_VALUE': return !valuePresent(actual)
    case 'EQUALS': case 'IN': return values.some(matches)
    case 'NOT_EQUALS': case 'NOT_IN': return values.every((expected) => !matches(expected))
  }
}
function conditionsMatch(conditions: FormCondition[] | undefined): boolean { return !conditions?.length || conditions.every(conditionMatches) }
function isRegistryManagedField(field: FormField): boolean { return field.code === 'affected_system' || field.code === 'affected_page' || field.code === 'affected_module' }
function isFieldVisible(field: FormField): boolean { return field.type !== 'RICH_TEXT' && field.type !== 'TAGS' && !isRegistryManagedField(field) && conditionsMatch(field.visibleWhen) }
function isFieldRequired(field: FormField): boolean { return field.required || conditionsMatch(field.requiredWhen) && Boolean(field.requiredWhen?.length) }
const visibleFields = computed(() => (loadedForm.value?.fields ?? []).filter(isFieldVisible).sort((left, right) => left.displayOrder - right.displayOrder))
const descriptionField = computed(() => loadedForm.value?.fields.find((field) => field.type === 'RICH_TEXT'))
const tagField = computed(() => loadedForm.value?.fields.find((field) => field.type === 'TAGS'))
function resetFormValues(schema: PublishedServiceCatalogForm): void {
  fieldValues.value = {}; validationErrors.value = {}; matchedRules.value = []; previewError.value = ''
  form.value.type = schema.serviceCatalogItem.ticketType
  form.value.tags = (schema.serviceCatalogItem.tags ?? []).slice(0, 2).map((tag) => tag.name)
  if (!form.value.descriptionText.trim()) {
    form.value.descriptionHtml = descriptionTemplate(schema.serviceCatalogItem.ticketType)
    form.value.descriptionText = schema.serviceCatalogItem.ticketType === 'ACCESS_REQUEST' ? '【申请内容】 【目标系统 / 角色】 【使用范围】 【有效期（如有）】 【补充说明】' : schema.serviceCatalogItem.ticketType === 'SERVICE_REQUEST' ? '【服务诉求】 【期望完成时间】 【使用场景】 【补充说明】' : '【发生时间】 【影响范围】 【问题现象 / 报错信息】 【已尝试操作】 【补充说明】'
  }
  for (const field of schema.fields) {
    if (field.type !== 'RICH_TEXT' && field.type !== 'TAGS') fieldValues.value[field.code] = fieldDefault(field)
  }
  syncRegistryStructuredFields()
}
function syncRegistryStructuredFields(): void {
  if (!loadedForm.value) return
  if (loadedForm.value.fields.some((field) => field.code === 'affected_system')) fieldValues.value.affected_system = form.value.systemCode
  const module = selectableModules.value.find((item) => item.moduleCode === form.value.moduleCode)
  const moduleValue = module?.moduleName ?? ''
  if (loadedForm.value.fields.some((field) => field.code === 'affected_page')) fieldValues.value.affected_page = moduleValue
  if (loadedForm.value.fields.some((field) => field.code === 'affected_module')) fieldValues.value.affected_module = form.value.moduleCode
}
async function loadForm(itemId: string): Promise<void> {
  const generation = ++formGeneration
  const expected = { systemCode: form.value.systemCode, moduleCode: form.value.moduleCode, catalogId: itemId }
  const isCurrent = () => !viewDisposed && generation === formGeneration && matchesServiceSelection(expected, form.value)
  formLoading.value = true; loadedForm.value = undefined; dictionaryOptions.value = {}
  try {
    if (!items.value.some(item => item.id === itemId)) throw new Error('Unmapped service')
    const result = await catalogApi.getPublishedForm(itemId)
    if (!isCurrent()) return
    if (result.source !== 'api' || result.data.serviceCatalogItem.id !== itemId) throw new Error('Unverified service form')
    const dictionaryFields = result.data.fields.filter((field) => field.dictionaryCode)
    const responses = await Promise.all(dictionaryFields.map(async (field) => [field.dictionaryCode!, await catalogApi.listDictionaryEntries(field.dictionaryCode!, itemId, result.data.formVersion, field.code)] as const))
    if (!isCurrent()) return
    if (responses.some(([, response]) => response.source !== 'api')) throw new Error('Unverified dictionary')
    dictionaryOptions.value = Object.fromEntries(responses.map(([code, response]) => [code, response.data.items]))
    loadedForm.value = result.data; resetFormValues(result.data)
  } catch (error) { if (isCurrent()) submitError.value = publicApiError(error, '已发布表单暂不可用，无法安全发起工单。') }
  finally { if (isCurrent()) formLoading.value = false }
}
async function onCatalogChange(): Promise<void> { submitError.value = ''; clearValidationError('catalogId'); if (form.value.catalogId) await loadForm(form.value.catalogId) }
function clearCatalogSelection(): void {
  formGeneration++; formLoading.value = false
  form.value.catalogId = ''; loadedForm.value = undefined; dictionaryOptions.value = {}; fieldValues.value = {}; form.value.tags = []
}
async function loadCatalogMappings(generation = selectionGeneration): Promise<void> {
  if (!form.value.systemCode) return
  const systemCode = form.value.systemCode, moduleCode = form.value.moduleCode
  const isCurrent = () => !viewDisposed && generation === selectionGeneration && form.value.systemCode === systemCode && form.value.moduleCode === moduleCode
  systemMappingLoading.value = true; systemRegistryError.value = ''
  items.value = []
  try {
    const result = await servicePortalApi.catalogItems(systemCode, moduleCode || undefined)
    if (!isCurrent()) return
    items.value = result
    clearValidationError('systemCode'); clearValidationError('moduleCode')
  } catch (error) {
    if (!isCurrent()) return
    items.value = []
    systemRegistryError.value = publicApiError(error, '无法读取该系统允许的工单服务，请重新选择系统或稍后重试。')
  } finally { if (isCurrent()) systemMappingLoading.value = false }
}
async function onSystemChange(): Promise<void> {
  const generation = ++selectionGeneration, systemCode = form.value.systemCode
  const isCurrent = () => !viewDisposed && generation === selectionGeneration && form.value.systemCode === systemCode
  form.value.moduleCode = ''; systemModules.value = []; items.value = []; clearCatalogSelection(); submitError.value = ''; systemRegistryError.value = ''; systemMappingLoading.value = false
  if (!systemCode) { systemModulesLoading.value = false; return }
  systemModulesLoading.value = true; systemRegistryError.value = ''
  try {
    if (!serviceSystems.value.some(system => system.systemCode === systemCode)) throw new Error('Unavailable system')
    const modules = await serviceSystemApi.listModules(systemCode)
    if (!isCurrent()) return
    systemModules.value = modules
    await loadCatalogMappings(generation)
  } catch (error) {
    if (isCurrent()) systemRegistryError.value = publicApiError(error, '无法读取系统的业务模块，暂不能安全提交工单。')
  } finally { if (isCurrent()) systemModulesLoading.value = false }
}
async function onModuleChange(): Promise<void> {
  const generation = ++selectionGeneration
  clearCatalogSelection(); items.value = []; systemMappingLoading.value = false
  if (form.value.moduleCode && !selectableModules.value.some(module => module.moduleCode === form.value.moduleCode)) { systemRegistryError.value = '所选模块不存在或已经停用。'; return }
  await loadCatalogMappings(generation)
  if (!viewDisposed && generation === selectionGeneration) syncRegistryStructuredFields()
}
function addTag(): void {
  const value = customTag.value.trim(), tag = value ? (value.startsWith('#') ? value : `#${value.replaceAll('#', '')}`) : ''
  if (loadedForm.value?.tagPolicy.allowFreeTags && tag && !form.value.tags.includes(tag) && form.value.tags.length < effectiveMaxTags.value) { form.value.tags.push(tag); clearValidationError('tags') }
  customTag.value = ''; matchedRules.value = []
}
function toggleTag(tag: string): void { const index = form.value.tags.indexOf(tag); if (index >= 0) form.value.tags.splice(index, 1); else if (loadedForm.value?.tagPolicy.allowStandardTags && form.value.tags.length < effectiveMaxTags.value) form.value.tags.push(tag); if (form.value.tags.length) clearValidationError('tags'); matchedRules.value = [] }
function tagKind(name: string): TagKind { return suggestedTags.value.includes(name) ? 'STANDARD' : 'FREE' }
function structuredFields(): Record<string, string | boolean | string[]> {
  const managedFields = (loadedForm.value?.fields ?? []).filter(isRegistryManagedField)
  return Object.fromEntries([...visibleFields.value, ...managedFields]
    .filter((field) => valuePresent(fieldValues.value[field.code]))
    .map((field) => [field.code, fieldValues.value[field.code]]))
}
function configurationItemIds(): string[] { return [...new Set(visibleFields.value.filter((field) => field.type === 'CI_REFERENCE').map((field) => valueOf(field.code).trim()).filter(Boolean))] }
function isMulti(field: FormField): boolean { return field.type === 'MULTI_SELECT' || field.type === 'CHECKBOX_GROUP' }
function isChoice(field: FormField): boolean { return field.type === 'SINGLE_SELECT' || field.type === 'RADIO' }
function isDate(field: FormField): boolean { return field.type === 'DATE' || field.type === 'DATETIME' }
function onDescriptionChange(value: string): void { form.value.descriptionText = value; clearValidationError('description'); matchedRules.value = [] }
function focusFirstError(errors: Record<string, string>): false {
  activeTab.value = 'basic'
  const firstCode = Object.keys(errors)[0]
  void nextTick(() => {
    const target = Array.from(document.querySelectorAll<HTMLElement>('[data-validation-field]'))
      .find((element) => element.dataset.validationField === firstCode)
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    target?.querySelector<HTMLElement>('input, textarea, [contenteditable="true"], button')?.focus({ preventScroll: true })
  })
  return false
}
function validateSelection(): boolean {
  const errors: Record<string, string> = {}
  if (!form.value.systemCode) errors.systemCode = '请选择影响系统'
  else if (!systemRoutingReady.value) errors.systemCode = systemRegistryError.value || '请等待影响系统路由信息加载完成'
  if (!form.value.catalogId) errors.catalogId = '请选择服务目录'
  else if (!mappedCatalogItems.value.some(item => item.id === form.value.catalogId)) errors.catalogId = '当前系统或模块已不允许发起该工单服务，请重新选择'
  validationErrors.value = errors
  return Object.keys(errors).length ? focusFirstError(errors) : true
}
function validateTicketForm(): boolean {
  const errors: Record<string, string> = {}
  if (form.value.title.trim().length < 4) errors.title = '请输入至少 4 个字符的主题'
  const descriptionDetails = form.value.descriptionText.replace(/【[^】]+】/g, '').trim()
  if (descriptionDetails.length < 4) errors.description = '请在提示项下补充实际问题现象或服务说明'
  for (const field of visibleFields.value) {
    if (isFieldRequired(field) && !valuePresent(fieldValues.value[field.code])) errors[field.code] = `请填写${field.label}`
  }
  if (tagField.value && isFieldRequired(tagField.value) && !form.value.tags.length) errors.tags = `请选择或添加${tagField.value.label}`
  validationErrors.value = errors
  return Object.keys(errors).length ? focusFirstError(errors) : true
}
async function requestSubmit(): Promise<void> {
  if (viewDisposed || confirming.value || submitting.value || createdTicketId.value) return
  if (creationUncertain.value) { await submit(); return }
  if (formLocked.value) return
  submitError.value = ''
  if (!validateSelection()) { submitError.value = '请先完成服务事项选择。'; return }
  if (!loadedForm.value || formLoading.value) { submitError.value = '请等待服务表单加载完成。'; return }
  if (!validateTicketForm()) { submitError.value = '请先补充标出的信息。'; return }
  confirming.value = true
  try {
    await ElMessageBox.confirm(`将提交工单“${form.value.title.trim()}”，并创建正式流程。是否继续？`, '确认提交工单', {
      confirmButtonText: '确认提交', cancelButtonText: '继续填写', type: 'info',
      closeOnClickModal: false, customClass: 'ticket-create-confirmation',
    })
  } catch { return }
  finally { confirming.value = false }
  if (viewDisposed) return
  await submit()
}
function handleFormEnter(event: KeyboardEvent): void {
  if (event.isComposing || !(event.target instanceof HTMLInputElement)) return
  const input = event.target
  // Keep dropdown selection, tags, date widgets and IME input under their own controls.
  if (!['text', 'number', 'search'].includes(input.type) || input.getAttribute('role') === 'combobox' || input.closest('.el-select, .el-date-editor, .tag-adder')) return
  event.preventDefault()
  // Single-page editing: Enter must not navigate away from or submit the form.
}
function formatDraftTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(timestamp))
}
function persistDraft(): void {
  draftSaveTimer = undefined
  if (createdTicketId.value || !draftReady.value || !draftSubjectId.value || !hasMeaningfulInput.value) return
  const safeDraftTypes = new Set(['BOOLEAN', 'SINGLE_SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX_GROUP', 'DATE', 'DATETIME'])
  const draftableCodes = new Set((loadedForm.value?.fields ?? [])
    .filter((field) => field.sensitivity !== 'SENSITIVE' && safeDraftTypes.has(field.type))
    .map((field) => field.code))
  const saved = writeTicketDraft({
    subjectId: draftSubjectId.value,
    formVersion: loadedForm.value?.formVersion ?? null,
    form: {
      systemCode: form.value.systemCode,
      moduleCode: form.value.moduleCode,
      catalogId: form.value.catalogId,
      type: form.value.type,
      title: form.value.title,
      descriptionHtml: form.value.descriptionHtml,
      descriptionText: form.value.descriptionText,
      tags: [...form.value.tags],
    },
    fieldValues: Object.fromEntries(Object.entries(fieldValues.value).filter(([code]) => draftableCodes.has(code))),
  })
  if (saved) draftStatus.value = `服务选择和非敏感选项已于 ${formatDraftTime(saved.updatedAt)} 暂存于本标签页，最长保留 24 小时`
  else draftStatus.value = '浏览器未允许保存本地草稿，请避免关闭页面'
}
function scheduleDraftSave(): void {
  if (suppressDraftTracking.value) return
  draftDirty.value = true
  if (!draftReady.value) return
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = setTimeout(persistDraft, 2_000)
}
function discardDraft(): void {
  if (draftSubjectId.value) removeTicketDraft(draftSubjectId.value)
  draftCandidate.value = null
  draftReady.value = true
  draftStatus.value = '已丢弃旧草稿；后续输入仍会自动保存'
}
async function restoreDraft(): Promise<void> {
  const draft = draftCandidate.value
  if (!draft || formLocked.value) return
  const context = contextGeneration
  const isCurrent = () => !viewDisposed && context === contextGeneration
  if (!draftSubjectId.value || draft.subjectId !== draftSubjectId.value) {
    removeTicketDraft(draft.subjectId)
    draftCandidate.value = null
    draftReady.value = true
    submitError.value = '登录身份已经变化，旧身份草稿已清理。'
    return
  }
  draftReady.value = false
  restoringDraft.value = true
  pendingLaunch.value = null; launchError.value = ''
  submitError.value = ''
  try {
    form.value.systemCode = draft.form.systemCode
    if (form.value.systemCode) await onSystemChange()
    if (!isCurrent()) return
    form.value.moduleCode = draft.form.moduleCode
    if (form.value.moduleCode) await onModuleChange()
    if (!isCurrent()) return
    if (!mappedCatalogItems.value.some(item => item.id === draft.form.catalogId)) throw new Error('Draft service no longer available')
    form.value.catalogId = draft.form.catalogId
    if (form.value.catalogId) await onCatalogChange()
    if (!isCurrent()) return
    if (draft.formVersion !== null && loadedForm.value?.formVersion !== draft.formVersion) {
      removeTicketDraft(draft.subjectId)
      submitError.value = '原草稿使用的表单版本已经下线。已保留服务事项选择，请按当前表单重新填写。'
      activeTab.value = 'basic'
      return
    }
    form.value.type = draft.form.type as TicketType
    if (draft.form.title) form.value.title = draft.form.title
    if (draft.form.descriptionHtml || draft.form.descriptionText) {
      form.value.descriptionHtml = draft.form.descriptionHtml
      form.value.descriptionText = draft.form.descriptionText
    }
    if (draft.form.tags.length) form.value.tags = [...draft.form.tags]
    const safeDraftTypes = new Set(['BOOLEAN', 'SINGLE_SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX_GROUP', 'DATE', 'DATETIME'])
    const availableCodes = new Set((loadedForm.value?.fields ?? [])
      .filter((field) => field.sensitivity !== 'SENSITIVE' && safeDraftTypes.has(field.type))
      .map((field) => field.code))
    fieldValues.value = Object.fromEntries(Object.entries(draft.fieldValues).filter(([code]) => availableCodes.has(code)))
    syncRegistryStructuredFields()
    activeTab.value = 'basic'
    draftStatus.value = `已恢复 ${formatDraftTime(draft.updatedAt)} 暂存的服务选择和非敏感选项；主题、正文及附件需重新填写`
    draftDirty.value = true
  } catch (error) {
    if (isCurrent()) submitError.value = publicApiError(error, '草稿恢复失败，请重新选择服务事项。')
  } finally {
    if (isCurrent()) { draftCandidate.value = null; draftReady.value = true; restoringDraft.value = false }
  }
}
function requestAttachmentSelection(): void { attachmentInput.value?.click() }
function requestImageSelection(): void { imageInput.value?.click() }
function queueFiles(event: Event, inlineImage: boolean): void {
  draftDirty.value = true
  const source = event.target as HTMLInputElement
  const files = Array.from(source.files ?? [])
  const accepted = inlineImage ? files.filter((file) => ['image/png', 'image/jpeg'].includes(file.type)) : files
  const remaining = 10 - pendingAttachments.value.length - pendingInlineImages.value.length
  if (inlineImage) {
    const image = accepted.slice(0, Math.max(0, remaining))[0]
    if (image) {
      const placeholderToken = crypto.randomUUID(), previewUrl = URL.createObjectURL(image)
      pendingInlineImages.value.push({ file: image, placeholderToken, previewUrl })
      richTextEditor.value?.insertPendingImage(placeholderToken, previewUrl, image.name)
      clearValidationError('description')
    }
  } else pendingAttachments.value.push(...accepted.slice(0, Math.max(0, remaining)))
  source.value = ''
}
function pendingImagePattern(token: string): RegExp { const safeToken = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); return new RegExp(`<img\\b(?=[^>]*\\bdata-pending-image="${safeToken}")[^>]*>`, 'g') }
function removePendingImage(token: string): void { form.value.descriptionHtml = form.value.descriptionHtml.replace(pendingImagePattern(token), ''); form.value.descriptionText = form.value.descriptionText.replace(`【图片待上传：${token}】`, '').trim() }
function removeQueuedFile(inlineImage: boolean, index: number): void {
  if (inlineImage) {
    const removed = pendingInlineImages.value.splice(index, 1)[0]
    if (removed) { URL.revokeObjectURL(removed.previewUrl); removePendingImage(removed.placeholderToken) }
  } else pendingAttachments.value.splice(index, 1)
}
function escapeHtml(value: string): string { return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;') }
function descriptionForInitialCreate(): string { return pendingInlineImages.value.reduce((content, image) => content.replace(pendingImagePattern(image.placeholderToken), `【图片待上传：${image.placeholderToken}】`), form.value.descriptionHtml) }
async function uploadQueuedFiles(ticket: { id: string; version: number }): Promise<boolean> {
  const tasks = [
    ...pendingAttachments.value.map((file) => ({ file, token: '', kind: '附件' })),
    ...pendingInlineImages.value.map((queued) => ({ file: queued.file, token: queued.placeholderToken, kind: '正文图片' })),
  ]
  uploadProgress.value = tasks.map((task) => ({ name: task.file.name, kind: task.kind, state: 'WAITING' }))
  let updatedDescription = form.value.descriptionHtml
  let linkedImages = 0
  for (const [index, task] of tasks.entries()) {
    uploadProgress.value[index].state = 'UPLOADING'
    try {
      const uploaded = await ticketApi.uploadAttachment(ticket.id, task.file)
      if (uploaded.scanStatus !== 'CLEAN' || (task.token && !uploaded.detectedMediaType.startsWith('image/'))) {
        uploadProgress.value[index].state = 'BLOCKED'
        continue
      }
      uploadProgress.value[index].state = 'UPLOADED'
      if (task.token) {
        const imageHtml = `<img src="/api/v1/tickets/${encodeURIComponent(ticket.id)}/attachments/${encodeURIComponent(uploaded.id)}/inline" alt="${escapeHtml(task.file.name)}">`
        updatedDescription = updatedDescription.replace(pendingImagePattern(task.token), imageHtml)
        linkedImages++
      }
    } catch {
      // The server may have accepted the upload even when the response was lost.
      uploadProgress.value[index].state = 'UNKNOWN'
    }
  }
  if (linkedImages) {
    // Never send a still-local blob URL for an image which was not accepted as CLEAN.
    updatedDescription = pendingInlineImages.value.reduce((content, queued) => content.replace(pendingImagePattern(queued.placeholderToken), '【图片尚未关联，请在附件列表核对】'), updatedDescription)
    try { await ticketApi.updateDescription(ticket.id, ticket.version, updatedDescription) }
    catch { imageLinkError.value = true }
  }
  return !imageLinkError.value && uploadProgress.value.every((item) => item.state === 'UPLOADED')
}
async function preview(): Promise<void> {
  previewError.value = ''; matchedRules.value = []
  if (!selectedItem.value || !loadedForm.value) { previewError.value = '请等待已发布表单加载完成。'; return }
  previewing.value = true
  try { const result = await catalogApi.matchRules({ serviceCatalogItemId: selectedItem.value.id, formVersion: loadedForm.value.formVersion, title: form.value.title.trim(), description: form.value.descriptionText.trim(), structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })), relatedConfigurationItemIds: configurationItemIds() }); matchedRules.value = result.data.matches }
  catch (error) { previewError.value = publicApiError(error, '案例预览暂不可用，请稍后重试。') }
  finally { previewing.value = false }
}
async function submit(): Promise<void> {
  if (submitting.value || createdTicketId.value) return
  submitError.value = ''; submitNotice.value = ''
  let request: TicketCreateRequest | undefined
  if (!creationUncertain.value) {
    if (!selectedItem.value || !loadedForm.value) { submitError.value = '请选择服务目录并等待已发布表单加载完成。'; return }
    if (!validateSelection()) { submitError.value = '请重新确认服务事项选择。'; return }
    if (!validateTicketForm()) { submitError.value = '请先补充标出的信息。'; return }
    request = { serviceCatalogItemId: selectedItem.value.id, serviceCatalogFormVersion: loadedForm.value.formVersion, serviceSystemCode: form.value.systemCode, serviceSystemModuleCode: form.value.moduleCode || undefined, type: selectedItem.value.ticketType, title: form.value.title.trim(), description: descriptionForInitialCreate(), descriptionFormat: 'RICH_TEXT', structuredFields: structuredFields(), tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })), relatedConfigurationItemIds: configurationItemIds() }
  }
  submitting.value = true
  try {
    const result = await (request ? creation.submit(request) : creation.retry())
    createdTicketId.value = result.data.id
    creationUncertain.value = false
    draftReady.value = false
    draftDirty.value = false
    draftStatus.value = ''
    if (draftSaveTimer) { clearTimeout(draftSaveTimer); draftSaveTimer = undefined }
    removeTicketDraft(draftSubjectId.value)
    const uploadsComplete = await uploadQueuedFiles(result.data)
    submitNotice.value = uploadsComplete ? '工单已创建，附件处理已完成。' : '工单已创建；部分附件或图片关联需要到原单核对。'
    if (uploadsComplete) await openCreatedTicket()
  }
  catch (error) {
    if (createdTicketId.value) { submitError.value = '工单已创建，请打开原单查看处理结果。'; return }
    const definitelyRejected = canReleaseSubmission(error instanceof ApiError ? error.status : undefined, creationUncertain.value)
    creationUncertain.value = !definitelyRejected
    if (definitelyRejected) creation.resetRejected()
    submitError.value = definitelyRejected ? publicApiError(error, '提交被拒绝，请检查输入后重试。') : '尚未确认创建结果。重试将复用本次提交内容和幂等键；也可先到工单中心核对，请勿另开页面重复建单。'
  }
  finally { submitting.value = false }
}
async function openCreatedTicket(): Promise<void> {
  if (!createdTicketId.value) return
  allowNavigation.value = true
  try {
    const failure = await router.push(`/tickets/${encodeURIComponent(createdTicketId.value)}`)
    if (failure) { allowNavigation.value = false; submitError.value = '详情暂未打开，请点击“打开已创建工单”重试。' }
  } catch {
    allowNavigation.value = false
    submitError.value = '详情暂未打开，请点击“打开已创建工单”重试。'
  }
}
function resetTicketContext(): void {
  selectionGeneration++; formGeneration++; sourceGeneration++
  suppressDraftTracking.value = true
  if (draftSaveTimer) { clearTimeout(draftSaveTimer); draftSaveTimer = undefined }
  pendingInlineImages.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
  pendingInlineImages.value = []
  pendingAttachments.value = []
  form.value = { systemCode: '', moduleCode: '', catalogId: '', type: 'INCIDENT', title: '', descriptionHtml: '', descriptionText: '', tags: [] }
  fieldValues.value = {}
  items.value = []
  serviceSystems.value = []
  systemModules.value = []
  dictionaryOptions.value = {}
  loadedForm.value = undefined
  matchedRules.value = []
  validationErrors.value = {}
  previewError.value = ''
  submitError.value = ''
  submitNotice.value = ''
  systemRegistryError.value = ''
  customTag.value = ''
  createdTicketId.value = ''
  creationUncertain.value = false
  uploadProgress.value = []
  imageLinkError.value = false
  confirming.value = false
  activeTab.value = 'basic'
  draftCandidate.value = null
  draftStatus.value = ''
  draftDirty.value = false
  draftReady.value = false
  allowNavigation.value = false
  submitting.value = false
  previewing.value = false
  formLoading.value = false
  systemModulesLoading.value = false
  systemMappingLoading.value = false
  void nextTick(() => { suppressDraftTracking.value = false })
}
async function loadLaunchSources(): Promise<void> {
  const generation = ++sourceGeneration
  const isCurrent = () => !viewDisposed && generation === sourceGeneration
  catalogLoading.value = true
  systemRegistryLoading.value = true
  systemRegistryError.value = ''
  try { const result = await serviceSystemApi.list(); if (isCurrent()) serviceSystems.value = result.items.filter((item) => item.lifecycleStatus === 'PUBLISHED') }
  catch (error) { if (isCurrent()) systemRegistryError.value = publicApiError(error, '服务系统暂不可用，无法安全发起工单。') }
  finally { if (isCurrent()) { systemRegistryLoading.value = false; catalogLoading.value = false } }
}
async function applyLaunchIntent(): Promise<void> {
  const intent = pendingLaunch.value
  if (!intent || formLocked.value) return
  const context = contextGeneration
  const isCurrent = () => !viewDisposed && context === contextGeneration
  if (hasMeaningfulInput.value && !window.confirm('将按新的工单服务重新开始，清除本页已填写的主题、正文、业务字段和待上传附件。确定继续吗？')) return
  if (!serviceSystems.value.some(system => system.systemCode === intent.systemCode)) { launchError.value = '入口对应的系统未发布或不在当前权限范围，请返回首页重新选择。'; return }
  launchLoading.value = true; launchError.value = ''; submitNotice.value = ''
  try {
    form.value = { systemCode: intent.systemCode, moduleCode: '', catalogId: '', type: 'INCIDENT', title: '', descriptionHtml: '', descriptionText: '', tags: [] }
    pendingInlineImages.value.forEach(image => URL.revokeObjectURL(image.previewUrl))
    pendingInlineImages.value = []; pendingAttachments.value = []
    await onSystemChange()
    if (!isCurrent()) return
    if (systemRegistryError.value) throw new Error('System not available')
    if (intent.moduleCode) {
      if (!selectableModules.value.some(module => module.moduleCode === intent.moduleCode)) { launchError.value = '入口对应的模块已停用或不可见，请重新选择。'; return }
      form.value.moduleCode = intent.moduleCode
      await onModuleChange()
      if (!isCurrent()) return
    }
    if (systemRegistryError.value) throw new Error('Catalog not available')
    if (!mappedCatalogItems.value.some(item => item.id === intent.catalogId)) { launchError.value = '该工单服务已下线、取消关联或不在当前范围内，请重新选择服务。'; return }
    form.value.catalogId = intent.catalogId
    await onCatalogChange()
    if (!isCurrent()) return
    if (!loadedForm.value) throw new Error('Form not available')
    // Only replace an older local draft after the explicit new-entry choice succeeded.
    if (draftCandidate.value) discardDraft()
    draftReady.value = true; pendingLaunch.value = null
    submitNotice.value = `已从服务目录选择：${selectedSystem.value?.systemName} → ${selectedItem.value?.name}；使用表单 v${loadedForm.value.formVersion}。`
    activeTab.value = 'basic'
  } catch { if (isCurrent()) launchError.value = '未能加载该入口对应的系统、服务或表单，请重试或返回首页重新选择。' }
  finally { if (isCurrent()) launchLoading.value = false }
}
onMounted(async () => {
  if (!session.currentUser) { try { await session.loadCurrentUser() } catch { /* Identity remains server-authoritative at submit time. */ } }
  if (viewDisposed) return
  await loadLaunchSources()
  if (viewDisposed) return
  launchSourcesInitialized = true
  if (draftSubjectId.value) draftCandidate.value = readTicketDraft(draftSubjectId.value)
  draftReady.value = !draftCandidate.value
  const launch = parseServiceLaunch(route.query)
  if (launch.kind === 'INVALID') launchError.value = '服务入口参数不完整或无效，请从首页重新选择系统与工单服务。'
  if (launch.kind === 'VALID') {
    pendingLaunch.value = launch.intent
    if (!draftCandidate.value) await applyLaunchIntent()
  }
})
async function refreshServiceScope(): Promise<void> {
  if (session.loading || !session.currentUser || systemRegistryLoading.value) return
  const context = contextGeneration
  await loadLaunchSources()
  if (viewDisposed || context !== contextGeneration || systemRegistryError.value) return
  form.value.systemCode = ''; form.value.moduleCode = ''
  scopeNeedsRefresh.value = false; draftReady.value = !creationUncertain.value && !createdTicketId.value
  submitNotice.value = '权限范围已重新核对，请重新选择系统和工单服务。公共主题与正文仍保留。'
}
watch(() => JSON.stringify([session.loading, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId, session.source, session.authorization]), () => {
  if (!launchSourcesInitialized) return
  contextGeneration++; selectionGeneration++; formGeneration++; sourceGeneration++
  clearCatalogSelection(); items.value = []; serviceSystems.value = []; systemModules.value = []
  systemModulesLoading.value = false; systemMappingLoading.value = false; systemRegistryLoading.value = false; catalogLoading.value = false
  launchLoading.value = false; restoringDraft.value = false; pendingLaunch.value = null; launchError.value = ''
  draftReady.value = false; draftCandidate.value = null; scopeNeedsRefresh.value = true
  if (draftSaveTimer) { clearTimeout(draftSaveTimer); draftSaveTimer = undefined }
  // A same-subject capability refresh invalidates all service projections synchronously.
  // Public free text is retained; a subject change is handled by the existing full reset below.
}, { flush: 'sync' })
onBeforeRouteUpdate(to => {
  if (formLocked.value) return false
  const launch = parseServiceLaunch(to.query)
  if (launch.kind === 'INVALID') { launchError.value = '新的服务入口无效，当前填写内容已保留。'; return false }
  if (launch.kind === 'VALID' && !matchesServiceSelection(launch.intent, form.value)) {
    pendingLaunch.value = launch.intent
    launchError.value = ''
  }
  // Applying another entry always requires the explicit banner action, never a query watcher overwrite.
  return true
})
watch([form, fieldValues], scheduleDraftSave, { deep: true })
watch(draftSubjectId, (current, previous) => {
  if (previous && current !== previous) {
    removeTicketDraft(previous)
    resetTicketContext()
    // Rebuilding the document aborts every old-subject request and prevents late responses
    // from repopulating the new subject's in-memory context.
    window.location.reload()
    return
  }
  if (current && current !== previous) {
    draftCandidate.value = readTicketDraft(current)
    draftReady.value = !draftCandidate.value
  } else if (!current) draftReady.value = false
})
function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (allowNavigation.value || (!draftDirty.value && !pendingAttachments.value.length && !pendingInlineImages.value.length)) return
  event.preventDefault()
}
window.addEventListener('beforeunload', handleBeforeUnload)
onBeforeUnmount(() => {
  viewDisposed = true
  contextGeneration++; selectionGeneration++; formGeneration++; sourceGeneration++
  if (confirming.value) ElMessageBox.close()
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  if (!allowNavigation.value && draftReady.value && draftDirty.value) persistDraft()
  pendingInlineImages.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
onBeforeRouteLeave(() => {
  // The body-mounted confirmation must be cancelled before leaving this editor.
  if (confirming.value) return false
  if (allowNavigation.value || (!draftDirty.value && !pendingAttachments.value.length && !pendingInlineImages.value.length)) return true
  return window.confirm(createdTicketId.value ? '工单已创建。请先在原单核对附件，离开后本页待上传文件不会保留。确定离开吗？' : creationUncertain.value ? '本次创建结果尚未确认。离开后将无法复用本页提交信息，请先到工单中心核对再发起新单。确定离开吗？' : '当前工单尚未提交。服务选择和非敏感选项已暂存，但主题、正文和附件不会保存。确定离开吗？')
})
</script>

<template>
  <section class="ticket-create-workspace">
  <div class="page-heading ticket-create-heading"><div><h2>新建工单</h2><p>先选所属系统，再选工单服务；每项服务加载自己的已发布表单。</p><RouterLink to="/">← 返回系统服务目录</RouterLink></div><span class="status-pill">{{ createdTicketId ? '已创建' : '未提交' }}</span></div>
  <p v-if="scopeNeedsRefresh" class="form-alert" role="alert">身份或权限范围已刷新，旧服务信息已清除。请重新核对后再填写业务字段。<button class="button button--secondary" type="button" :disabled="session.loading || systemRegistryLoading || !session.currentUser" @click="refreshServiceScope">{{ systemRegistryLoading ? '重新核对中…' : '重新核对服务范围' }}</button></p>
  <section v-if="pendingLaunch" class="draft-restore-banner" role="status"><div><b>已选择一个系统服务入口</b><span>{{ pendingLaunch.systemCode }} → {{ pendingLaunch.catalogId }}。{{ draftCandidate ? '发现旧草稿：可恢复上次选项，或按当前入口重新开始，两者不会混合。' : '点击后将重新核对系统、模块、服务映射与已发布表单。' }}</span></div><button type="button" class="button button--primary" :disabled="formLocked || systemRegistryLoading" @click="applyLaunchIntent">{{ launchLoading ? '核对服务入口…' : '按当前入口新建' }}</button><button type="button" class="button button--secondary" :disabled="formLocked" @click="pendingLaunch = null; launchError = ''">忽略此入口</button></section>
  <p v-if="launchError" class="form-alert form-alert--error" role="alert">{{ launchError }}</p>
  <section v-if="draftCandidate" class="draft-restore-banner" role="status"><div><b>发现未完成的安全草稿</b><span>暂存于 {{ formatDraftTime(draftCandidate.updatedAt) }}，仅包含服务选择和非敏感选项；主题、正文及附件不会恢复。</span></div><button class="button button--primary" type="button" :disabled="formLocked || session.loading || catalogLoading || systemRegistryLoading" @click="restoreDraft">{{ restoringDraft ? '恢复中…' : '恢复选项' }}</button><button class="button button--secondary" type="button" :disabled="formLocked" @click="discardDraft">丢弃</button></section>
  <p v-else-if="draftStatus" class="draft-save-status" role="status">{{ draftStatus }}</p>
  <section v-if="validationSummary.length" class="form-error-summary" role="alert" aria-live="assertive"><b>还有 {{ validationSummary.length }} 项需要处理</b><ul><li v-for="message in validationSummary" :key="message">{{ message }}</li></ul></section>
  <p v-if="submitError" class="form-alert form-alert--error" role="alert">{{ submitError }}</p>
  <p v-if="submitNotice" class="form-alert form-alert--success" role="status">{{ submitNotice }}</p>
  <div class="ticket-create-layout" @keydown.enter="handleFormEnter">
    <section v-if="!createdTicketId" class="panel form-panel ticket-create-main" :inert="formLocked">
      <header class="ticket-create-document-header"><small>工单编号：提交后生成</small><h3>{{ selectedItem?.name ?? selectedTypeLabel }}{{ selectedSystem ? ` · ${selectedSystem.systemName}` : '' }}</h3><span>新建</span></header>
      <el-form :model="form" label-position="top" class="ticket-create-el-form" @submit.prevent="requestSubmit">
        <el-form-item class="field field--full ticket-title-field" required data-validation-field="title" :error="fieldError('title')"><template #label>主题</template><el-input v-model="form.title" maxlength="200" show-word-limit placeholder="一句话说明问题，例如：ERP 采购订单页面加载缓慢" @input="matchedRules = []; clearValidationError('title')" /></el-form-item>
        <el-tabs v-model="activeTab" class="ticket-create-tabs field--full">
      <el-tab-pane label="基础信息" name="basic">
        <section class="ticket-form-section">
          <header class="form-section-heading"><span>申请人信息</span><small>IAM 同步，只读展示；提交后保存身份快照</small></header>
          <div class="form-grid form-grid--three identity-form-grid">
            <el-form-item class="field identity-display-field" required><template #label>申请人</template><div class="readonly-field">{{ session.currentUser?.displayName ?? '正在读取 IAM 身份…' }}</div></el-form-item>
            <el-form-item class="field identity-display-field" required><template #label>申请人部门</template><div class="readonly-field">{{ session.currentUser?.organizationName ?? '正在读取 IAM 组织…' }}</div></el-form-item>
            <el-form-item class="field identity-display-field"><template #label>IAM 用户 ID</template><div class="readonly-field mono-text">{{ session.currentUser?.iamUserId ?? '—' }}</div></el-form-item>
          </div>
        </section>
        <section class="ticket-form-section">
          <header class="form-section-heading"><span>工单服务信息</span><small>系统 → 模块（可选） → 具体工单服务；业务字段按所选服务加载</small></header>
          <p v-if="catalogLoading" class="compact-loading">正在加载服务目录…</p>
          <div v-else class="form-grid form-grid--three ticket-business-grid">
            <el-form-item class="field" required data-validation-field="systemCode" :error="fieldError('systemCode')"><template #label>影响系统</template><el-select v-model="form.systemCode" filterable :disabled="systemRegistryLoading || !serviceSystems.length" :loading="systemRegistryLoading" placeholder="搜索或选择已注册系统" @change="onSystemChange"><el-option v-for="item in serviceSystems" :key="item.systemCode" :label="item.systemName" :value="item.systemCode" /></el-select><small v-if="selectedSystem">{{ selectedSystem.systemCode }}{{ selectedSystem.ciId ? ` · CI ${selectedSystem.ciId}` : '' }}</small><small v-else-if="systemRegistryError" class="form-field-error">{{ systemRegistryError }}</small><small v-else>只展示当前 IAM 范围内已发布的系统。</small></el-form-item>
            <el-form-item class="field" data-validation-field="moduleCode"><template #label>受影响模块 / 页面</template><el-select v-model="form.moduleCode" filterable clearable :disabled="!form.systemCode || systemModulesLoading" :loading="systemModulesLoading" placeholder="可选：缩小到具体模块" @change="onModuleChange"><el-option v-for="item in selectableModules" :key="item.moduleCode" :label="item.moduleName" :value="item.moduleCode" /></el-select><small>{{ form.systemCode ? (selectableModules.length ? '选择模块后会进一步收敛服务事项。' : '该系统暂无模块，可直接选择服务事项。') : '请先选择影响系统。' }}</small></el-form-item>
            <el-form-item class="field" required data-validation-field="catalogId" :error="fieldError('catalogId')"><template #label>服务事项</template><el-select v-model="form.catalogId" filterable :disabled="!systemRoutingReady || !mappedCatalogItems.length" :loading="systemMappingLoading" placeholder="请选择要办理的事项" @change="onCatalogChange"><el-option v-for="item in mappedCatalogItems" :key="item.id" :label="item.name" :value="item.id" /></el-select><small v-if="form.systemCode && !systemMappingLoading && !mappedCatalogItems.length" class="form-field-error">该系统/模块尚未配置可发起的服务事项。</small><small v-else>{{ selectedItem?.summary ?? '选择后会自动加载对应表单和处理流程。' }}</small></el-form-item>
            <p v-if="formLoading" class="compact-loading field--full">正在加载动态字段与可用字典…</p>
            <template v-for="field in visibleFields" :key="field.code">
              <el-form-item v-if="isChoice(field)" class="field" :required="isFieldRequired(field)" :data-validation-field="field.code" :error="fieldError(field.code)"><template #label>{{ field.label }}</template><el-select :model-value="valueOf(field.code)" placeholder="请选择" @update:model-value="setValue(field.code, $event)"><el-option v-for="option in optionsFor(field)" :key="option.code" :label="option.label" :value="option.code" /></el-select><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item>
              <fieldset v-else-if="isMulti(field)" class="field field--full multi-field" :class="{ 'validation-field--error': fieldError(field.code) }" :data-validation-field="field.code"><legend>{{ field.label }} <b v-if="isFieldRequired(field)">*</b></legend><el-checkbox-group :model-value="valuesOf(field.code)" @update:model-value="fieldValues[field.code] = $event; clearValidationError(field.code); matchedRules = []"><el-checkbox v-for="option in optionsFor(field)" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox></el-checkbox-group><small v-if="field.helpText">{{ field.helpText }}</small><small v-if="fieldError(field.code)" class="form-field-error">{{ fieldError(field.code) }}</small></fieldset>
              <el-form-item v-else-if="field.type === 'LONG_TEXT' || field.type === 'TEXTAREA'" class="field field--full" :required="isFieldRequired(field)" :data-validation-field="field.code" :error="fieldError(field.code)"><template #label>{{ field.label }}</template><el-input :model-value="valueOf(field.code)" :maxlength="field.validation?.maxLength" type="textarea" :rows="3" @update:model-value="setValue(field.code, $event)" /><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item>
              <el-form-item v-else-if="isDate(field)" class="field" :required="isFieldRequired(field)" :data-validation-field="field.code" :error="fieldError(field.code)"><template #label>{{ field.label }}</template><el-date-picker :model-value="valueOf(field.code)" :type="field.type === 'DATE' ? 'date' : 'datetime'" value-format="YYYY-MM-DDTHH:mm:ss" @update:model-value="setValue(field.code, $event ?? '')" /><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item>
              <label v-else-if="field.type === 'BOOLEAN'" class="field field--full boolean-field" :class="{ 'validation-field--error': fieldError(field.code) }" :data-validation-field="field.code"><span><input type="checkbox" :checked="boolOf(field.code)" @change="setBoolean(field.code, ($event.target as HTMLInputElement).checked)" /> {{ field.label }} <b v-if="isFieldRequired(field)">*</b></span><small v-if="field.helpText">{{ field.helpText }}</small><small v-if="fieldError(field.code)" class="form-field-error">{{ fieldError(field.code) }}</small></label>
              <el-form-item v-else-if="field.type === 'CI_REFERENCE'" class="field" :required="isFieldRequired(field)" :data-validation-field="field.code" :error="fieldError(field.code)"><template #label>{{ field.label }}</template><el-input :model-value="valueOf(field.code)" :maxlength="field.validation?.maxLength" placeholder="请输入受管 CMDB CI 编码" @update:model-value="setValue(field.code, $event)" /><small>{{ field.helpText || '仅填写已纳入 CMDB 只读投影的 CI 编码；提交时会再次校验。' }}</small></el-form-item>
              <el-form-item v-else class="field" :required="isFieldRequired(field)" :data-validation-field="field.code" :error="fieldError(field.code)"><template #label>{{ field.label }}</template><el-input :model-value="valueOf(field.code)" :maxlength="field.validation?.maxLength" @update:model-value="setValue(field.code, $event)" /><small v-if="field.helpText">{{ field.helpText }}</small></el-form-item>
            </template>
          </div>
        </section>
        <section class="ticket-form-section ticket-description-section" :class="{ 'is-invalid': fieldError('description') }" data-validation-field="description">
          <div class="section-field-label">{{ descriptionField?.label ?? '问题现象 / 服务说明' }} <b>*</b><el-tooltip :content="descriptionField?.helpText || '请填写现象、影响范围、已尝试操作、错误码或截图说明；不要上传敏感数据。'" placement="top"><span class="form-help-trigger" aria-label="显示帮助">?</span></el-tooltip></div>
          <TicketRichTextEditor ref="richTextEditor" v-model="form.descriptionHtml" :disabled="formLoading" @plain-text-change="onDescriptionChange" @request-image="requestImageSelection" />
          <small v-if="fieldError('description')" class="form-field-error">{{ fieldError('description') }}</small>
          <small class="rich-editor-boundary">支持文字格式、HTTP/HTTPS 链接及 PNG/JPEG 图片；服务端会清洗正文并保留纯文本摘要。</small>
        </section>
        <section class="ticket-form-section ticket-attachment-section">
          <div class="attachment-section-header"><div class="section-field-label">附件列表 <el-tooltip content="请勿上传密码、密钥、身份证号等敏感数据。" placement="top"><span class="form-help-trigger" aria-label="显示帮助">?</span></el-tooltip></div><span>单个文件及总大小以服务端策略为准</span></div>
          <div class="ticket-upload-zone"><input ref="imageInput" type="file" accept="image/png,image/jpeg" hidden @change="queueFiles($event, true)" /><input ref="attachmentInput" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv" multiple hidden @change="queueFiles($event, false)" /><el-button size="small" plain type="primary" @click="requestAttachmentSelection">选择附件</el-button><el-button size="small" plain @click="requestImageSelection()">插入正文图片</el-button><span>支持 PDF、PNG、JPG、JPEG、TXT、CSV；附件与正文图片合计最多 10 个。</span></div>
          <div v-if="pendingInlineImages.length || pendingAttachments.length" class="attachment-queue"><div v-for="(queued, index) in pendingInlineImages" :key="`image-${queued.file.name}-${index}`" class="attachment-queue__row"><span class="attachment-queue__type">图片</span><b>{{ queued.file.name }}</b><small>{{ Math.ceil(queued.file.size / 1024) }} KB · {{ inlineImageQueueStatus }}{{ queued.placeholderToken ? '，并替换正文原位置' : '，并追加到正文末尾' }}</small><button type="button" aria-label="移除图片" @click="removeQueuedFile(true, index)">移除</button></div><div v-for="(file, index) in pendingAttachments" :key="`attachment-${file.name}-${index}`" class="attachment-queue__row"><span class="attachment-queue__type attachment-queue__type--file">附件</span><b>{{ file.name }}</b><small>{{ Math.ceil(file.size / 1024) }} KB · {{ attachmentQueueStatus }}</small><button type="button" aria-label="移除附件" @click="removeQueuedFile(false, index)">移除</button></div></div>
          <p v-else class="attachment-empty">暂未选择附件。图片会在工单创建后上传并写入正文；生产环境需通过安全扫描。</p>
        </section>
      </el-tab-pane>
      <el-tab-pane label="关联工单" name="relations"><div class="tab-empty-state"><b>关联工单在创建后维护</b><span>当前工单尚未生成编号，无法安全检索或关联其他工单。提交成功后可在工单详情添加关联、重复单、父子单、问题单或变更单；每次操作均进行对象权限校验并写入审计。</span></div></el-tab-pane>
      <el-tab-pane label="审批记录" name="approvals"><div class="tab-empty-state"><b>暂无审批记录</b><span>工单提交后，按服务目录及审批策略生成实际审批任务；此处不会提前创建审批。</span></div></el-tab-pane>
      <el-tab-pane label="流程图" name="workflow">
        <WorkflowDiagramPanel v-if="selectedItem?.ticketType === 'INCIDENT'" :active="activeTab === 'workflow'" />
        <div v-else class="tab-empty-state"><b>请先选择事件工单服务目录</b><span>当前只预览平台已部署的标准事件流程；其他类型及设计草稿不会伪装成已可运行流程。</span></div>
      </el-tab-pane>
      <el-tab-pane label="流转记录" name="history"><div class="tab-empty-state"><b>暂无流转记录</b><span>工单提交后，可在详情页查看处理节点、转办、协办及操作记录。</span></div></el-tab-pane>
        </el-tabs>
      </el-form>
    </section>
    <aside v-if="!createdTicketId" class="form-sidebar" :inert="formLocked">
      <section class="panel form-panel ticket-create-overview">
        <div class="ticket-create-sla"><b>◷ 服务时效</b><span>提交后开始计算</span><dl><div><dt>响应截止</dt><dd>提交后按 SLA 生成</dd></div><div><dt>解决截止</dt><dd>提交后按 SLA 生成</dd></div></dl></div>
        <h3>工单概况</h3>
        <dl class="ticket-create-facts"><div><dt>工单状态</dt><dd>未提交</dd></div><div><dt>工单类型</dt><dd>{{ form.type === 'ACCESS_REQUEST' ? '账号权限' : form.type === 'SERVICE_REQUEST' ? '服务请求' : '故障报修' }}</dd></div><div><dt>影响系统</dt><dd>{{ selectedSystem?.systemName ?? '待选择' }}</dd></div><div><dt>当前处理人</dt><dd>提交后分派</dd></div><div><dt>创建时间</dt><dd>提交后记录</dd></div><div><dt>表单版本</dt><dd>{{ loadedForm ? `v${loadedForm.formVersion}` : '选择服务后加载' }}</dd></div></dl>
      </section>
      <section class="panel form-panel knowledge-reference-panel"><div class="panel-header"><div><h3>知识库</h3><p>按目录、标签、字段与描述匹配，仅作参考。</p></div><RouterLink to="/knowledge">查看更多</RouterLink></div><button class="button button--secondary" type="button" :disabled="previewing || !loadedForm" @click="preview">{{ previewing ? '匹配中…' : '匹配案例与知识' }}</button><p v-if="previewError" class="form-alert form-alert--error">{{ previewError }}</p><div v-else-if="matchedRules.length" class="case-preview-list"><article v-for="item in matchedRules" :key="item.ruleCode"><b>{{ item.suggestion.title }}</b><span>{{ item.suggestion.kind === 'KNOWLEDGE_ARTICLE' ? '知识建议' : '已解决案例' }}</span><small>{{ item.suggestion.summary }}</small></article></div><p v-else class="rule-hint__empty">填写事件信息后可匹配已解决案例；不使用 AI，不自动改变工单流程。</p></section>
      <section class="panel form-panel related-process-panel"><div class="panel-header"><div><h3>相关流程</h3><p>由已发布服务目录决定。</p></div></div><div class="related-process-item"><b>{{ selectedItem?.name ?? '尚未选择服务目录' }}</b><small>提交后创建 Flowable 流程实例；当前不展示或预创建审批任务。</small></div></section>
      <section class="panel form-panel tag-panel" :class="{ 'is-invalid': fieldError('tags') }" data-validation-field="tags"><div class="panel-header"><div><h3>{{ tagField?.label ?? '问题标签' }} <b v-if="tagField?.required">*</b></h3><p>{{ tagField?.helpText ?? '标准选项优先；是否允许自定义由表单版本决定。' }}</p></div></div><div class="tag-choice"><button v-for="tag in suggestedTags" :key="tag" class="tag-choice__item" :class="{ 'is-selected': form.tags.includes(tag) }" type="button" :disabled="!loadedForm?.tagPolicy.allowStandardTags" @click="toggleTag(tag)">{{ tag }}</button></div><p v-if="!loadedForm" class="rule-hint__empty">选择服务目录后显示可用标签。</p><div v-if="loadedForm?.tagPolicy.allowFreeTags" class="tag-adder"><input v-model="customTag" maxlength="50" placeholder="#自定义标签" @keyup.enter.prevent="addTag" /><button class="button button--secondary" type="button" @click="addTag">添加</button></div><small v-if="fieldError('tags')" class="form-field-error">{{ fieldError('tags') }}</small><div v-if="form.tags.length" class="tag-row selected-tags"><span v-for="tag in form.tags" :key="tag" class="tag tag--blue">{{ tag }} <button type="button" :aria-label="`移除 ${tag}`" @click="toggleTag(tag)">×</button></span></div></section>
    </aside>
    <section v-if="createdTicketId" class="panel form-panel submission-receipt" aria-labelledby="submission-receipt-title" :aria-busy="submitting">
      <h3 id="submission-receipt-title">工单已创建：{{ createdTicketId }}</h3>
      <p role="status">{{ submitting ? '正在处理附件，请勿关闭此页。不会再次创建工单。' : '请在原单继续处理，无需重新填写或重复建单。' }}</p>
      <ul v-if="uploadProgress.length" class="submission-upload-list">
        <li v-for="(item, index) in uploadProgress" :key="index"><span>{{ item.kind }} · {{ item.name }}</span><strong :class="`upload-state--${item.state.toLowerCase()}`">{{ uploadStateNames[item.state] }}</strong></li>
      </ul>
      <p v-if="imageLinkError" class="form-alert form-alert--error" role="alert">图片已上传，但正文关联未确认完成。请到原单查看附件和正文，勿重复上传已存在的图片。</p>
      <p v-if="!submitting && uploadProgress.some(item => item.state !== 'UPLOADED')">请先在原单附件列表核对已接收文件，再补齐缺失文件；扫描未通过的附件不能下载。</p>
    </section>
    <div class="form-actions">
      <button v-if="createdTicketId" class="button button--primary" type="button" :disabled="submitting" @click="openCreatedTicket">打开已创建工单</button>
      <template v-else>
        <RouterLink v-if="!submitting" class="button button--secondary" to="/tickets">{{ creationUncertain ? '先到工单中心核对' : '取消' }}</RouterLink>
        <button class="button button--primary" type="button" :disabled="confirming || submitting || session.loading || catalogLoading || formLoading || systemRegistryLoading || systemMappingLoading || (!creationUncertain && formLocked)" @click="requestSubmit">{{ submitting ? '正在安全提交…' : creationUncertain ? '重试原提交' : '提交工单' }}</button>
      </template>
    </div>
  </div>
  </section>
</template>

<style scoped>
.submission-receipt { grid-column: 1 / -1; min-width: 0; }
.submission-receipt h3, .submission-receipt p { overflow-wrap: anywhere; }
.submission-upload-list { list-style: none; margin: 16px 0; padding: 0; }
.submission-upload-list li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding: 12px 0; border-bottom: 1px solid #e2eaf3; }
.submission-upload-list li span { overflow-wrap: anywhere; min-width: 0; }
.submission-upload-list strong { font-size: 12px; color: #5b6d82; }
.submission-upload-list .upload-state--uploaded { color: #217450; }
.submission-upload-list .upload-state--unknown, .submission-upload-list .upload-state--blocked { color: #994f12; }
</style>
