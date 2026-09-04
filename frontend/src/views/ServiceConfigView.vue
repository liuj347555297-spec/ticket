<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { catalogApi, serviceCatalogAdminApi, type ConfigurableFormFieldType, type FormConditionOperator, type FormField, type ManagedFormConfiguration, type ManagedFormDraftInput, type ManagedFormField, type PublishedServiceCatalogForm, type ServiceCatalogItem, type WorkflowNodeAssignmentPolicy } from '@/api/catalog'
import { serviceSystemApi, serviceSystemAdminApi, type ServiceSystem, type ServiceSystemCatalogMapping, type ServiceSystemDraftInput, type ServiceSystemModule, type ServiceSystemModuleInput } from '@/api/service-systems'
import { announcementApi, type ServiceAnnouncement } from '@/api/announcements'
import { servicePortalApi } from '@/api/service-portal'
import { ApiError, apiRequest } from '@/api/client'
import { allowDesignerTransition, createRequestScope, effectiveMappings, embeddedDesignerKey, mappingChanges, mergeOfferingMetadata, preserveDesignerDuringIdentityRefresh, type EmbeddedDesignerHandle, type EmbeddedDesignerState } from '@/utils/serviceCatalogWorkspace'
import { useSessionStore } from '@/stores/session'

const items = ref<ServiceCatalogItem[]>([])
const selectedId = ref('')
const selectedForm = ref<PublishedServiceCatalogForm>()
const source = ref<'api' | 'demo'>('api')
const error = ref('')
const loadingForm = ref(false)
const managementNotice = ref('')
const managementError = ref('')
const selectedFieldCode = ref('')
const adminConfigs = ref<ManagedFormConfiguration[]>([])
const adminLoading = ref(false)
const savingDraft = ref(false)
const runningAction = ref(false)
const showEditor = ref(false)
const showActionDialog = ref(false)
const editorMode = ref<'CREATE' | 'UPDATE'>('UPDATE')
const actionKind = ref<'PUBLISH' | 'APPROVE' | 'RETIRE' | 'ROLLBACK'>('PUBLISH')
const publicationRequestId = ref('')
const actionReason = ref('')
const rollbackVersion = ref<number | undefined>()
const editor = ref<ManagedFormDraftInput>({ version: 0, code: '', name: '', summary: '', ticketType: 'INCIDENT', categoryCode: '', applicableOrganizationIds: [], fields: [], tagPolicy: { allowStandardTags: true, allowFreeTags: false, maxTags: 10, allowedStandardTagCodes: [] }, reason: '' })
const session = useSessionStore()
const announcements = ref<ServiceAnnouncement[]>([])
const showAnnouncementForm = ref(false)
const announcementSubmitting = ref(false)
const announcementError = ref('')
const announcementNotice = ref('')
const announcementForm = ref({ title: '', body: '', audienceScope: 'ALL' as 'ALL' | 'ORGANIZATION', targetOrganizationIamId: '', pinned: false, effectiveUntil: '' })
const routingPolicies = ref<WorkflowNodeAssignmentPolicy[]>([]), routingLoading = ref(false), routingSaving = ref<string>(), routingError = ref('')
const serviceSystems = ref<ServiceSystem[]>([]), serviceSystemsLoading = ref(false), selectedServiceSystemCode = ref('')
const serviceSystemModules = ref<ServiceSystemModule[]>([]), selectedServiceSystemModuleCode = ref('')
const serviceSystemMappings = ref<ServiceSystemCatalogMapping[]>([]), systemMappingScope = ref<'SYSTEM' | 'MODULE'>('SYSTEM')
const serviceSystemError = ref(''), serviceSystemNotice = ref(''), serviceSystemSaving = ref(false)
const serviceSystemEditor = ref<ServiceSystemDraftInput>({ version: 0, systemCode: '', systemName: '', ciId: '', ownerIamUserId: '', owningOrganizationId: '', lifecycleStatus: 'DRAFT', reason: '' })
const serviceSystemModuleEditor = ref<ServiceSystemModuleInput>({ version: 0, moduleCode: '', moduleName: '', modulePath: '', active: true, sortOrder: 0 })
const mappedCatalogIds = ref<string[]>([]), defaultMappedCatalogId = ref('')
type WorkspaceSection = 'SERVICES' | 'SETTINGS' | 'MODULES' | 'MAPPINGS' | 'LIBRARY' | 'DETAIL' | 'ANNOUNCEMENTS' | 'DESIGN'
const workspaceSection = ref<WorkspaceSection>('SERVICES'), systemSearch = ref(''), serviceSearch = ref('')
const EmbeddedDesignStudio = defineAsyncComponent(() => import('@/views/DesignStudioView.vue'))
const embeddedDesigner = ref<EmbeddedDesignerHandle>()
const designerEpoch = ref(0)
const designerTarget = ref<{ systemCode: string; systemName: string; organizationId: string; serviceCatalogItemId?: string; serviceName?: string; serviceManaged?: boolean }>()
const designerState = ref<EmbeddedDesignerState>({ dirty: false, busy: false, uncertain: false })
const designerKey = computed(() => embeddedDesignerKey(designerEpoch.value, designerTarget.value?.systemCode ?? '', designerTarget.value?.serviceCatalogItemId))
const modulesLoading = ref(false), mappingLoading = ref(false), mappingReady = ref(false), adminReady = ref(false)
const systemLevelMappings = ref<ServiceSystemCatalogMapping[]>([])
const publishedScopeItems = ref<ServiceCatalogItem[]>([]), publishedMetadataNotice = ref('')
const creatingSystem = ref(false), systemListWarning = ref(''), loadedModuleCode = ref('')
const publicationRequests = new Map<string, string>()
const identityScope = createRequestScope(), systemScope = createRequestScope(), systemListScope = createRequestScope(), mappingScope = createRequestScope(), formScope = createRequestScope(), adminScope = createRequestScope(), routingScope = createRequestScope()
let disposed = false, loadedSubject = ''
const alive = (identity: number) => !disposed && identityScope.accepts(identity)
const mutationBusy = computed(() => savingDraft.value || runningAction.value || serviceSystemSaving.value || announcementSubmitting.value || Boolean(routingSaving.value))
const filteredSystems = computed(() => serviceSystems.value.filter(system => `${system.systemName} ${system.systemCode}`.toLowerCase().includes(systemSearch.value.trim().toLowerCase())))
const effectiveServiceMappings = computed(() => mappingReady.value ? effectiveMappings(systemLevelMappings.value, serviceSystemMappings.value, systemMappingScope.value === 'MODULE') : [])
const offeringMetadata = computed(() => mergeOfferingMetadata(items.value, publishedScopeItems.value, effectiveServiceMappings.value))
const scopedOfferings = computed(() => effectiveServiceMappings.value.map(mapping => ({ mapping, item: offeringMetadata.value.find(item => item.id === mapping.serviceCatalogItemId) })))
const filteredOfferings = computed(() => scopedOfferings.value.filter(({ item, mapping }) => `${item?.name ?? ''} ${item?.code ?? mapping.serviceCatalogItemId}`.toLowerCase().includes(serviceSearch.value.trim().toLowerCase())))
const libraryItems = computed(() => items.value.filter(item => `${item.name} ${item.code}`.toLowerCase().includes(serviceSearch.value.trim().toLowerCase())))
const inheritedServices = computed(() => systemMappingScope.value === 'MODULE' && !serviceSystemMappings.value.some(mapping => mapping.active))
const mappingSaveAllowed = computed(() => (systemMappingScope.value !== 'MODULE' || selectedServiceSystemModule.value?.active) && selectedServiceSystem.value?.lifecycleStatus === 'DRAFT' && canManageForms.value && adminReady.value && mappingReady.value && !mappingLoading.value && !modulesLoading.value && !mutationBusy.value && Boolean(selectedServiceSystem.value) && (systemMappingScope.value === 'SYSTEM' || Boolean(selectedServiceSystemModule.value)))
const statusLabel = (id: string) => ({ DRAFT: '草稿', PENDING_REVIEW: '待复核', PUBLISHED: '已发布', RETIRED: '已停用', REJECTED: '已驳回' })[adminConfigs.value.find(item => item.id === id)?.lifecycleStatus ?? 'PUBLISHED']
function clearOffering(): void { formScope.next(); routingScope.next(); selectedId.value = ''; selectedForm.value = undefined; selectedFieldCode.value = ''; routingPolicies.value = []; routingLoading.value = false; publicationRequestId.value = ''; loadingForm.value = false }
function canLeaveDesigner(): boolean { return allowDesignerTransition(workspaceSection.value === 'DESIGN', embeddedDesigner.value, designerState.value) }
function clearDesigner(): void { designerTarget.value = undefined; designerEpoch.value++; designerState.value = { dirty: false, busy: false, uncertain: false } }
function setWorkspace(section: WorkspaceSection): boolean {
  if (mutationBusy.value || (workspaceSection.value !== section && !canLeaveDesigner())) return false
  if (section !== 'DESIGN') clearDesigner()
  if (section !== 'SETTINGS' && creatingSystem.value) { creatingSystem.value = false; if (selectedServiceSystem.value) resetServiceSystemEditor(selectedServiceSystem.value) }
  workspaceSection.value = section; if (section !== 'DETAIL') clearOffering()
  return true
}
function openSystemSettings(): void { if (setWorkspace('SETTINGS') && !creatingSystem.value && selectedServiceSystem.value) resetServiceSystemEditor(selectedServiceSystem.value) }
function isDesignableService(id: string): boolean { return mappingReady.value && effectiveServiceMappings.value.some(mapping => mapping.serviceCatalogItemId === id) }
function openDesigner(item?: ServiceCatalogItem): void {
  const system = selectedServiceSystem.value
  if (!system || creatingSystem.value || !canReadDesign.value || mutationBusy.value) return
  if (item && !isDesignableService(item.id)) { managementError.value = '请先在系统或模块范围内关联该服务，再配置其表单与流程。'; return }
  if (!canLeaveDesigner()) return
  clearOffering(); clearDesigner()
  designerTarget.value = { systemCode: system.systemCode, systemName: system.systemName, organizationId: system.owningOrganizationId, ...(item ? { serviceCatalogItemId: item.id, serviceName: item.name, serviceManaged: adminConfigs.value.some(config => config.id === item.id) } : {}) }
  workspaceSection.value = 'DESIGN'
}
const designerListeners = computed(() => {
  const epoch = designerEpoch.value
  return {
    close: () => { if (!disposed && epoch === designerEpoch.value && workspaceSection.value === 'DESIGN') setWorkspace('SERVICES') },
    'state-change': (state: EmbeddedDesignerState) => { if (!disposed && epoch === designerEpoch.value && workspaceSection.value === 'DESIGN') designerState.value = { ...state } },
  }
})
function allowMappingDiscard(): boolean {
  if (!mappingReady.value || selectedServiceSystem.value?.lifecycleStatus !== 'DRAFT') return true
  let changed = true
  try { changed = mappingChanges(serviceSystemMappings.value, mappedCatalogIds.value, defaultMappedCatalogId.value).length > 0 } catch { /* Invalid local edits are still unsaved work. */ }
  return !changed || window.confirm('当前服务关联有未保存修改，切换范围将放弃这些修改。确定继续？')
}
function newSystem(): void { if (mutationBusy.value || !canManageForms.value || !canLeaveDesigner()) return; clearDesigner(); creatingSystem.value = true; clearOffering(); resetServiceSystemEditor(); workspaceSection.value = 'SETTINGS' }
async function changeModule(): Promise<void> {
  if (mutationBusy.value) return
  if (!canLeaveDesigner()) { selectedServiceSystemModuleCode.value = loadedModuleCode.value; return }
  if (!allowMappingDiscard()) { selectedServiceSystemModuleCode.value = loadedModuleCode.value; return }
  if (workspaceSection.value === 'DESIGN') { clearDesigner(); workspaceSection.value = 'SERVICES' }
  systemMappingScope.value = selectedServiceSystemModuleCode.value ? 'MODULE' : 'SYSTEM'
  const module = selectedServiceSystemModule.value; resetServiceSystemModuleEditor(module); clearOffering()
  if (workspaceSection.value === 'DETAIL') workspaceSection.value = 'SERVICES'
  await loadServiceSystemMappings()
}
function openOffering(item: ServiceCatalogItem): void { if (mutationBusy.value || !canLeaveDesigner()) return; clearDesigner(); workspaceSection.value = 'DETAIL'; void select(item) }

const selected = computed(() => offeringMetadata.value.find((item) => item.id === selectedId.value))
const selectedAdmin = computed(() => adminConfigs.value.find((item) => item.id === selectedId.value))
const canManageAnnouncements = computed(() => session.authorization?.roles.some((role) => role === 'SERVICE_MANAGER' || role === 'PLATFORM_ADMIN') ?? false)
const canManageForms = computed(() => session.authorization?.roles.some((role) => role === 'SERVICE_MANAGER' || role === 'PLATFORM_ADMIN') ?? false)
const canReadDesign = computed(() => session.authorization?.roles.some(role => ['SERVICE_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'].includes(role)) ?? false)
const selectedField = computed(() => selectedForm.value?.fields.find((field) => field.code === selectedFieldCode.value) ?? selectedForm.value?.fields[0])
const selectedAdminField = computed(() => selectedAdmin.value?.fields.find((field) => field.code === selectedFieldCode.value) ?? selectedAdmin.value?.fields[0])
const selectedServiceSystem = computed(() => serviceSystems.value.find((system) => system.systemCode === selectedServiceSystemCode.value))
const selectedServiceSystemModule = computed(() => serviceSystemModules.value.find((module) => module.moduleCode === selectedServiceSystemModuleCode.value))
const publishedCatalogOptions = computed(() => adminConfigs.value.filter((item) => item.lifecycleStatus === 'PUBLISHED').map(asServiceItem))
const typeLabel = (type: ServiceCatalogItem['ticketType']) => ({ INCIDENT: '故障报修', ACCESS_REQUEST: '账号权限', SERVICE_REQUEST: '服务请求', PROBLEM: '问题管理', CHANGE: '变更' })[type]
const fieldTypeLabel: Record<string, string> = { TEXT: '单行文本', TEXTAREA: '多行文本', LONG_TEXT: '长文本', NUMBER: '数字', DATE: '日期', DATETIME: '日期时间', BOOLEAN: '开关', SINGLE_SELECT: '下拉单选', MULTI_SELECT: '下拉多选', RADIO: '单选组', CHECKBOX_GROUP: '复选组', ERROR_CODE: '错误码', TAGS: '标签', CI_REFERENCE: '配置项引用', RICH_TEXT: '受控富文本' }
const configurableFieldTypes: ConfigurableFormFieldType[] = ['TEXT', 'LONG_TEXT', 'SINGLE_SELECT', 'MULTI_SELECT', 'DATETIME', 'BOOLEAN', 'TAGS', 'CI_REFERENCE', 'RICH_TEXT']
const conditionOperators: FormConditionOperator[] = ['EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN', 'HAS_VALUE', 'NO_VALUE']

function fieldValidation(field: FormField): string {
  const value = field.validation
  if (!value) return '无额外校验'
  const rules = [value.minLength !== undefined ? `最少 ${value.minLength} 字` : '', value.maxLength !== undefined ? `最多 ${value.maxLength} 字` : '', value.minimum !== undefined ? `最小值 ${value.minimum}` : '', value.maximum !== undefined ? `最大值 ${value.maximum}` : '', value.patternCode ? `规则 ${value.patternCode}` : ''].filter(Boolean)
  return rules.join('；') || '无额外校验'
}
function defaultValue(field: FormField): string { return field.type === 'BOOLEAN' ? '未下发（默认 false）' : '未配置' }
function displayCondition(field: FormField): string { return field.required ? '始终显示（必填）' : '始终显示' }
function selectField(field: FormField): void { selectedFieldCode.value = field.code }
function asServiceItem(value: ManagedFormConfiguration): ServiceCatalogItem { return { id: value.id, code: value.code, name: value.name, summary: value.summary, ticketType: value.ticketType, categoryCode: value.categoryCode, publishedVersion: value.formVersion, formSchemaHash: value.schemaHash, tags: [] } }
function asPublishedForm(value: ManagedFormConfiguration): PublishedServiceCatalogForm {
  return { serviceCatalogItem: asServiceItem(value), formVersion: value.formVersion, formSchemaHash: value.schemaHash, tagPolicy: value.tagPolicy, fields: value.fields.map((field) => ({ code: field.code, label: field.label, helpText: field.helpText, type: field.type as FormField['type'], required: field.required, defaultValue: field.defaultValue, visibleWhen: field.visibleWhen, requiredWhen: field.requiredWhen, displayOrder: field.displayOrder, dictionaryCode: field.dictionaryCode, validation: field.maxLength ? { maxLength: field.maxLength } : undefined, sensitivity: 'INTERNAL', masking: 'NONE' })) }
}
function cloneField(field: ManagedFormField): ManagedFormField { return { ...field, visibleWhen: field.visibleWhen.map((rule) => ({ ...rule, values: [...rule.values] })), requiredWhen: field.requiredWhen.map((rule) => ({ ...rule, values: [...rule.values] })) } }
function draftFrom(value: ManagedFormConfiguration): ManagedFormDraftInput { return { version: value.version, code: value.code, name: value.name, summary: value.summary ?? '', ticketType: value.ticketType, categoryCode: value.categoryCode, applicableOrganizationIds: [...value.applicableOrganizationIds], fields: value.fields.map(cloneField), tagPolicy: { ...value.tagPolicy, allowedStandardTagCodes: [...(value.tagPolicy.allowedStandardTagCodes ?? [])] }, reason: '' } }
async function loadAdmin(): Promise<void> {
  const identity = identityScope.current(), request = adminScope.next(), priorId = selectedId.value
  adminLoading.value = true; adminReady.value = false; managementError.value = ''
  try {
    const first = await serviceCatalogAdminApi.list()
    const all = [...first.items]
    for (let page = 2; page <= Math.ceil(first.total / first.pageSize); page++) {
      if (!alive(identity) || !adminScope.accepts(request)) return
      const next = await apiRequest<{items: ManagedFormConfiguration[]}>(`/admin/service-catalog/items?page=${page}&pageSize=${first.pageSize}`)
      all.push(...next.items)
    }
    if (!alive(identity) || !adminScope.accepts(request)) return
    adminConfigs.value = all; items.value = all.map(asServiceItem); adminReady.value = true
    const target = all.find(item => item.id === priorId)
    if (target && selectedId.value === priorId) selectAdmin(target)
  } catch (cause) { if (alive(identity) && adminScope.accepts(request)) managementError.value = cause instanceof ApiError ? cause.message : '服务库读取失败，暂不可保存关联。' }
  finally { if (alive(identity) && adminScope.accepts(request)) adminLoading.value = false }
}
function selectAdmin(value: ManagedFormConfiguration): void { formScope.next(); publicationRequestId.value = publicationRequests.get(value.id) ?? ''; selectedId.value = value.id; selectedForm.value = asPublishedForm(value); selectedFieldCode.value = value.fields[0]?.code ?? ''; managementNotice.value = ''; managementError.value = ''; void loadRoutingPolicies(value.id) }
const routingNodes: WorkflowNodeAssignmentPolicy['nodeKey'][] = ['accept', 'processing', 'user_feedback', 'closure']
const nodeLabel = (node: WorkflowNodeAssignmentPolicy['nodeKey']) => ({ accept: '受理', processing: '处理中', user_feedback: '待用户反馈', closure: '关闭确认' } as const)[node]
async function loadRoutingPolicies(id: string): Promise<void> {
  const identity = identityScope.current(), request = routingScope.next()
  routingLoading.value = true; routingError.value = ''; routingPolicies.value = []
  try { const loaded = await serviceCatalogAdminApi.listRoutingPolicies(id); if (!alive(identity) || !routingScope.accepts(request) || selectedId.value !== id) return; routingPolicies.value = routingNodes.map(nodeKey => loaded.find(policy => policy.nodeKey === nodeKey) ?? { serviceCatalogItemId: id, nodeKey, mode: 'SYSTEM_RANDOM', candidateRoles: ['ROLE_FIRST_LINE_SUPPORT'], version: 0, enabled: true }) }
  catch (cause) { if (alive(identity) && routingScope.accepts(request)) routingError.value = cause instanceof ApiError ? cause.message : '节点策略读取失败，禁止据此覆盖配置。' }
  finally { if (alive(identity) && routingScope.accepts(request)) routingLoading.value = false }
}
function policyFor(nodeKey: WorkflowNodeAssignmentPolicy['nodeKey']): WorkflowNodeAssignmentPolicy { return routingPolicies.value.find(policy => policy.nodeKey === nodeKey)! }
async function saveRoutingPolicy(policy: WorkflowNodeAssignmentPolicy): Promise<void> {
  if (!selectedAdmin.value || mutationBusy.value || routingError.value || routingLoading.value || !canManageForms.value) return
  const id = selectedAdmin.value.id, identity = identityScope.current(), request = routingScope.current()
  routingSaving.value = policy.nodeKey; routingError.value = ''
  try { const saved = await serviceCatalogAdminApi.saveRoutingPolicy(id, { ...policy, candidateRoles: [...policy.candidateRoles] }); if (!alive(identity) || !routingScope.accepts(request) || selectedId.value !== id) return; const index = routingPolicies.value.findIndex(item => item.nodeKey === saved.nodeKey); if (index >= 0) routingPolicies.value.splice(index, 1, saved); managementNotice.value = '节点路由策略已保存并写入审计。' }
  catch (cause) { if (alive(identity) && routingScope.accepts(request)) routingError.value = cause instanceof ApiError ? cause.message : '路由策略保存失败，请重载后核对。' }
  finally { if (alive(identity)) routingSaving.value = undefined }
}
function select(item: ServiceCatalogItem): Promise<void> | void { const managed = adminConfigs.value.find((value) => value.id === item.id); if (managed) { selectAdmin(managed); return } return selectPublished(item) }
async function selectPublished(item: ServiceCatalogItem): Promise<void> {
  const identity = identityScope.current(), request = formScope.next()
  selectedId.value = item.id; selectedForm.value = undefined; selectedFieldCode.value = ''; error.value = ''; loadingForm.value = true
  try { const result = await catalogApi.getPublishedForm(item.id); if (!alive(identity) || !formScope.accepts(request) || selectedId.value !== item.id) return; if (result.source !== 'api') throw new Error('No demo config'); selectedForm.value = result.data; selectedFieldCode.value = result.data.fields[0]?.code ?? '' }
  catch { if (alive(identity) && formScope.accepts(request)) error.value = '服务表单读取失败，未使用演示配置替代。' }
  finally { if (alive(identity) && formScope.accepts(request)) loadingForm.value = false }
}
function openCreate(): void {
  editorMode.value = 'CREATE'; managementError.value = ''; editor.value = { version: 0, code: '', name: '', summary: '', ticketType: 'INCIDENT', categoryCode: '', applicableOrganizationIds: [], fields: [{ code: 'description', label: '问题现象 / 服务说明', type: 'RICH_TEXT', required: true, helpText: '请结构化说明现象、影响范围、错误码和已尝试操作。', displayOrder: 1, visibleWhen: [], requiredWhen: [] }], tagPolicy: { allowStandardTags: true, allowFreeTags: false, maxTags: 10, allowedStandardTagCodes: [] }, reason: '' }; showEditor.value = true
}
function openUpdate(): void { if (!selectedAdmin.value) return; editorMode.value = 'UPDATE'; managementError.value = ''; editor.value = draftFrom(selectedAdmin.value); showEditor.value = true }
function addField(): void { const next = editor.value.fields.length + 1; editor.value.fields.push({ code: `field_${next}`, label: `业务字段 ${next}`, type: 'TEXT', required: false, displayOrder: next, visibleWhen: [], requiredWhen: [] }) }
function removeField(index: number): void { if (editor.value.fields.length <= 1) return; editor.value.fields.splice(index, 1); editor.value.fields.forEach((field, position) => { field.displayOrder = position + 1 }) }
function normalizeDraft(): ManagedFormDraftInput { return { ...editor.value, code: editor.value.code.trim().toUpperCase(), name: editor.value.name.trim(), summary: editor.value.summary?.trim() || undefined, categoryCode: editor.value.categoryCode.trim().toUpperCase(), applicableOrganizationIds: editor.value.applicableOrganizationIds.map((value) => value.trim()).filter(Boolean), fields: editor.value.fields.map((field, index) => ({ ...field, code: field.code.trim(), label: field.label.trim(), defaultValue: field.defaultValue?.trim() || undefined, helpText: field.helpText?.trim() || undefined, dictionaryCode: field.dictionaryCode?.trim().toUpperCase() || undefined, displayOrder: index + 1, visibleWhen: field.visibleWhen ?? [], requiredWhen: field.requiredWhen ?? [] })), tagPolicy: { ...editor.value.tagPolicy, allowedStandardTagCodes: editor.value.tagPolicy.allowedStandardTagCodes ?? [] }, reason: editor.value.reason.trim() } }
async function saveDraft(): Promise<void> {
  if (!canManageForms.value || mutationBusy.value) return
  const identity = identityScope.current(), mode = editorMode.value, id = selectedAdmin.value?.id, input = normalizeDraft()
  if (mode === 'UPDATE' && !id) return
  managementError.value = ''; savingDraft.value = true
  try {
    const result = mode === 'CREATE' ? await serviceCatalogAdminApi.create(input) : await serviceCatalogAdminApi.update(id!, input)
    if (!alive(identity)) return
    await loadAdmin(); if (!alive(identity)) return
    selectAdmin(result); workspaceSection.value = 'DETAIL'; showEditor.value = false
    managementNotice.value = mode === 'CREATE' ? '服务草稿已创建，尚未关联到任何新系统。请完成发布后，在关联已有服务中显式设置入口。' : '独立服务草稿已保存，表单及组织范围已由服务端校验。'
  } catch (cause) { if (alive(identity)) managementError.value = cause instanceof ApiError ? cause.message : '草稿保存失败。' }
  finally { if (alive(identity)) savingDraft.value = false }
}
function openAction(kind: 'PUBLISH' | 'APPROVE' | 'RETIRE' | 'ROLLBACK'): void { if (!selectedAdmin.value) return; actionKind.value = kind; managementError.value = ''; actionReason.value = ''; rollbackVersion.value = Math.max(1, selectedAdmin.value.formVersion - 1); showActionDialog.value = true }
async function executeAction(): Promise<void> {
  if (!selectedAdmin.value || !canManageForms.value || mutationBusy.value) return
  const identity = identityScope.current(), config = selectedAdmin.value, kind = actionKind.value, reason = actionReason.value.trim(), requestId = publicationRequestId.value, rollback = rollbackVersion.value ?? 1
  managementError.value = ''; runningAction.value = true
  try {
    let success = ''
    if (kind === 'PUBLISH') { const response = await serviceCatalogAdminApi.requestPublish(config.id, config.version, reason); if (!alive(identity)) return; publicationRequests.set(config.id, response.requestId); publicationRequestId.value = response.requestId; success = `已提交独立服务发布审批：${response.requestId}。需另一位具备权限的人员复核。` }
    if (kind === 'APPROVE') { await serviceCatalogAdminApi.approve(config.id, requestId, config.version); if (!alive(identity)) return; publicationRequests.delete(config.id); success = '服务发布审批通过，已产生不可变表单修订；这不等于系统上架或自定义流程发布。' }
    if (kind === 'RETIRE') { await serviceCatalogAdminApi.retire(config.id, config.version, reason); success = '服务已停用；历史工单版本快照不受影响。' }
    if (kind === 'ROLLBACK') { await serviceCatalogAdminApi.rollback(config.id, config.version, rollback, reason); success = '回滚草稿已创建，仍需校验、审批及发布。' }
    if (!alive(identity)) return
    showActionDialog.value = false; await loadAdmin(); if (alive(identity)) managementNotice.value = success
  } catch (cause) { if (alive(identity)) managementError.value = cause instanceof ApiError ? cause.message : '管理操作失败。' }
  finally { if (alive(identity)) runningAction.value = false }
}
function openAnnouncementForm(): void {
  announcementError.value = ''; announcementNotice.value = ''
  announcementForm.value = { title: '', body: '', audienceScope: 'ALL', targetOrganizationIamId: session.currentUser?.organizationIamOrganizationId ?? '', pinned: false, effectiveUntil: '' }
  showAnnouncementForm.value = true
}
async function submitAnnouncement(): Promise<void> {
  if (!canManageAnnouncements.value || mutationBusy.value) return
  const value = { ...announcementForm.value }, identity = identityScope.current(); announcementError.value = ''; announcementNotice.value = ''
  if (!value.title.trim() || !value.body.trim() || !value.effectiveUntil || (value.audienceScope === 'ORGANIZATION' && !value.targetOrganizationIamId.trim())) { announcementError.value = '请填写标题、正文、有效期和对应组织范围。'; return }
  announcementSubmitting.value = true
  try {
    const created = await announcementApi.create({ title: value.title.trim(), body: value.body.trim(), audienceScope: value.audienceScope, targetOrganizationIamId: value.audienceScope === 'ORGANIZATION' ? value.targetOrganizationIamId.trim() : undefined, pinned: value.pinned, effectiveUntil: new Date(value.effectiveUntil).toISOString() })
    if (!alive(identity)) return
    announcements.value = [created, ...announcements.value]; showAnnouncementForm.value = false; announcementNotice.value = '公告已发布并审计。'
  } catch (cause) { if (alive(identity)) announcementError.value = cause instanceof ApiError ? cause.message : '公告发布失败。' }
  finally { if (alive(identity)) announcementSubmitting.value = false }
}
function resetServiceSystemEditor(value?: ServiceSystem): void {
  serviceSystemEditor.value = value
    ? { version: value.version, systemCode: value.systemCode, systemName: value.systemName, ciId: value.ciId ?? '', ownerIamUserId: value.ownerIamUserId ?? '', owningOrganizationId: value.owningOrganizationId, lifecycleStatus: value.lifecycleStatus, reason: '' }
    : { version: 0, systemCode: '', systemName: '', ciId: '', ownerIamUserId: '', owningOrganizationId: session.currentUser?.organizationIamOrganizationId ?? '', lifecycleStatus: 'DRAFT', reason: '' }
}
function resetServiceSystemModuleEditor(value?: ServiceSystemModule): void {
  serviceSystemModuleEditor.value = value
    ? { version: value.version, moduleCode: value.moduleCode, moduleName: value.moduleName, modulePath: value.modulePath ?? '', active: value.active, sortOrder: value.sortOrder }
    : { version: 0, moduleCode: '', moduleName: '', modulePath: '', active: true, sortOrder: serviceSystemModules.value.length }
}
async function loadServiceSystems(selectCode?: string): Promise<void> {
  const identity = identityScope.current(), request = systemListScope.next(), selection = systemScope.current()
  serviceSystemsLoading.value = true; serviceSystemError.value = ''; systemListWarning.value = ''
  try {
    const page = canManageForms.value ? await serviceSystemAdminApi.list() : await serviceSystemApi.list()
    const all = [...page.items]
    if (canManageForms.value) for (let number = 2; number <= Math.ceil(page.total / page.pageSize); number++) {
      if (!alive(identity) || !systemListScope.accepts(request)) return
      const next = await apiRequest<{ items: Array<{ code: string; name: string; configurationItemId?: string; ownerIamUserId?: string; owningOrganizationId: string; status: ServiceSystem['lifecycleStatus']; version: number; changeReason?: string; publishedAt?: string }> }>(`/admin/service-systems?page=${number}&pageSize=${page.pageSize}`)
      all.push(...next.items.map(item => ({ systemCode: item.code, systemName: item.name, ciId: item.configurationItemId, ownerIamUserId: item.ownerIamUserId, owningOrganizationId: item.owningOrganizationId, lifecycleStatus: item.status, version: item.version, changeReason: item.changeReason, publishedAt: item.publishedAt })))
    }
    if (!alive(identity) || !systemListScope.accepts(request)) return
    serviceSystems.value = all
    if (page.total > all.length) systemListWarning.value = `系统列表尚未完整读取，请刷新核对。`
    const target = all.find(item => item.systemCode === (selectCode ?? selectedServiceSystemCode.value))
    if (target && systemScope.accepts(selection)) await selectServiceSystem(target, true)
  } catch (cause) { if (alive(identity) && systemListScope.accepts(request)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '系统列表读取失败。' }
  finally { if (alive(identity) && systemListScope.accepts(request)) serviceSystemsLoading.value = false }
}
async function selectServiceSystem(value: ServiceSystem, force = false): Promise<void> {
  if (mutationBusy.value && !force) return
  if (workspaceSection.value === 'DESIGN' && selectedServiceSystemCode.value === value.systemCode) return
  if (!canLeaveDesigner()) return
  if (!force && !allowMappingDiscard()) return
  clearDesigner()
  const identity = identityScope.current(), request = systemScope.next()
  mappingScope.next(); mappingReady.value = false; mappingLoading.value = false
  selectedServiceSystemCode.value = value.systemCode; selectedServiceSystemModuleCode.value = ''; loadedModuleCode.value = ''; systemMappingScope.value = 'SYSTEM'
  serviceSystemModules.value = []; serviceSystemMappings.value = []; systemLevelMappings.value = []; mappedCatalogIds.value = []; defaultMappedCatalogId.value = ''
  publishedScopeItems.value = []; publishedMetadataNotice.value = ''
  creatingSystem.value = false; resetServiceSystemEditor(value); resetServiceSystemModuleEditor(); clearOffering(); workspaceSection.value = 'SERVICES'
  serviceSystemError.value = ''; serviceSystemNotice.value = ''; modulesLoading.value = true
  try {
    const modules = canManageForms.value ? await serviceSystemAdminApi.listModules(value.systemCode) : await serviceSystemApi.listModules(value.systemCode)
    if (!alive(identity) || !systemScope.accepts(request)) return
    serviceSystemModules.value = modules; await loadServiceSystemMappings()
  } catch (cause) { if (alive(identity) && systemScope.accepts(request)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '模块读取失败，服务关联区保持不可写。' }
  finally { if (alive(identity) && systemScope.accepts(request)) modulesLoading.value = false }
}
async function loadServiceSystemMappings(): Promise<void> {
  if (!selectedServiceSystem.value) return
  const identity = identityScope.current(), request = mappingScope.next(), systemCode = selectedServiceSystem.value.systemCode
  const moduleCode = systemMappingScope.value === 'MODULE' ? selectedServiceSystemModuleCode.value : undefined
  mappingReady.value = false; mappingLoading.value = true; serviceSystemError.value = ''
  publishedScopeItems.value = []; publishedMetadataNotice.value = ''
  serviceSystemMappings.value = []; systemLevelMappings.value = []; mappedCatalogIds.value = []; defaultMappedCatalogId.value = ''
  if (systemMappingScope.value === 'MODULE' && !moduleCode) { mappingLoading.value = false; return }
  try {
    const reader = canManageForms.value ? serviceSystemAdminApi : serviceSystemApi
    const [base, direct] = await Promise.all([reader.listCatalogMappings(systemCode), moduleCode ? reader.listCatalogMappings(systemCode, moduleCode) : Promise.resolve(null)])
    if (!alive(identity) || !mappingScope.accepts(request)) return
    systemLevelMappings.value = base; serviceSystemMappings.value = direct ?? base
    loadedModuleCode.value = moduleCode ?? ''
    mappedCatalogIds.value = serviceSystemMappings.value.filter(item => item.active).map(item => item.serviceCatalogItemId)
    defaultMappedCatalogId.value = serviceSystemMappings.value.find(item => item.active && item.isDefault)?.serviceCatalogItemId ?? ''
    mappingReady.value = true
    // Seed services are published catalog entries, not managed draft configurations. Enrich display only.
    if (selectedServiceSystem.value?.lifecycleStatus === 'PUBLISHED') {
      try {
        const published = await servicePortalApi.catalogItems(systemCode, moduleCode)
        if (!alive(identity) || !mappingScope.accepts(request)) return
        publishedScopeItems.value = published
      } catch (cause) {
        if (!alive(identity) || !mappingScope.accepts(request)) return
        publishedMetadataNotice.value = cause instanceof ApiError && cause.status === 403
          ? '公开服务元数据不在当前身份的申请人可见范围内；已读取的后台配置保留，未据此降权或替换为空。'
          : '公开发布服务元数据暂不可用；已读取的后台配置保留。仅在公开目录存在的旧服务名称可能暂时无法补充。'
      }
    }
  } catch (cause) { if (alive(identity) && mappingScope.accepts(request)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '服务关联读取失败；未以空配置替代，保存已禁用。' }
  finally { if (alive(identity) && mappingScope.accepts(request)) mappingLoading.value = false }
}
async function saveServiceSystem(): Promise<void> {
  if (!canManageForms.value || mutationBusy.value) return
  const value = { ...serviceSystemEditor.value }, identity = identityScope.current(), isCreate = creatingSystem.value
  if (!isCreate && selectedServiceSystem.value?.lifecycleStatus !== 'DRAFT') return
  serviceSystemError.value = ''; serviceSystemNotice.value = ''
  if (!value.systemCode.trim() || !value.systemName.trim() || !value.owningOrganizationId.trim() || value.reason.trim().length < 4) { serviceSystemError.value = '请填写系统编码、名称、所属组织及至少 4 字的说明。'; return }
  serviceSystemSaving.value = true
  try {
    const input: ServiceSystemDraftInput = { ...value, systemCode: value.systemCode.trim().toUpperCase(), systemName: value.systemName.trim(), ciId: value.ciId?.trim() || undefined, ownerIamUserId: value.ownerIamUserId?.trim() || undefined, owningOrganizationId: value.owningOrganizationId.trim(), reason: value.reason.trim() }
    const saved = isCreate ? await serviceSystemAdminApi.create(input) : await serviceSystemAdminApi.update(value.systemCode, input)
    if (!alive(identity)) return
    await loadServiceSystems(saved.systemCode)
    if (alive(identity)) { workspaceSection.value = 'SETTINGS'; serviceSystemNotice.value = '系统信息已保存；上架/下架需单独操作，保存信息不会发布下属服务。' }
  } catch (cause) { if (alive(identity)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '系统保存失败。' }
  finally { if (alive(identity)) serviceSystemSaving.value = false }
}
async function changeSystemStatus(action: 'publish' | 'retire'): Promise<void> {
  if (!selectedServiceSystem.value || creatingSystem.value || !canManageForms.value || mutationBusy.value) return
  const system = selectedServiceSystem.value, reason = serviceSystemEditor.value.reason.trim(), identity = identityScope.current()
  if (reason.length < 4) { serviceSystemError.value = '请先在系统设置填写至少 4 字的变更说明。'; return }
  if (!window.confirm(`确认${action === 'publish' ? '上架' : '下架'}系统“${system.systemName}”？系统上架不会自动发布工单服务。`)) return
  serviceSystemSaving.value = true
  try {
    await apiRequest(`/admin/service-systems/${encodeURIComponent(system.systemCode)}/${action}`, { method: 'POST', headers: { 'If-Match': `"${system.version}"` }, body: { reason } })
    if (!alive(identity)) return
    await loadServiceSystems(system.systemCode)
    if (alive(identity)) { workspaceSection.value = 'SETTINGS'; serviceSystemNotice.value = '系统上架状态已由服务端更新并审计；各服务仍独立发布。' }
  } catch (cause) { if (alive(identity)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '系统状态变更失败，请重新读取核对。' }
  finally { if (alive(identity)) serviceSystemSaving.value = false }
}
async function saveServiceSystemModule(): Promise<void> {
  const system = selectedServiceSystem.value, value = { ...serviceSystemModuleEditor.value }, identity = identityScope.current()
  if (!system || system.lifecycleStatus !== 'DRAFT' || !canManageForms.value || mutationBusy.value || modulesLoading.value) return
  serviceSystemError.value = ''; serviceSystemNotice.value = ''
  if (!value.moduleCode.trim() || !value.moduleName.trim()) { serviceSystemError.value = '请填写模块编码和名称。'; return }
  serviceSystemSaving.value = true
  try {
    const input: ServiceSystemModuleInput = { ...value, moduleCode: value.moduleCode.trim().toUpperCase(), moduleName: value.moduleName.trim(), modulePath: value.modulePath?.trim() || undefined }
    const existing = serviceSystemModules.value.some(module => module.moduleCode === value.moduleCode)
    const saved = existing ? await serviceSystemAdminApi.updateModule(system.systemCode, value.moduleCode, input) : await serviceSystemAdminApi.createModule(system.systemCode, input)
    if (!alive(identity)) return
    await selectServiceSystem(system, true); if (!alive(identity)) return
    selectedServiceSystemModuleCode.value = saved.moduleCode; systemMappingScope.value = 'MODULE'; resetServiceSystemModuleEditor(saved); workspaceSection.value = 'MODULES'
    await loadServiceSystemMappings()
    if (alive(identity)) serviceSystemNotice.value = '模块已保存；映射仍需单独关联。'
  } catch (cause) { if (alive(identity)) serviceSystemError.value = cause instanceof ApiError ? cause.message : '模块保存失败。' }
  finally { if (alive(identity)) serviceSystemSaving.value = false }
}
async function saveServiceSystemMappings(): Promise<void> {
  if (!mappingSaveAllowed.value || !selectedServiceSystem.value) return
  const system = selectedServiceSystem.value, identity = identityScope.current(), scopeRequest = mappingScope.current()
  const moduleCode = systemMappingScope.value === 'MODULE' ? selectedServiceSystemModuleCode.value : undefined
  let changes: ReturnType<typeof mappingChanges>
  try { changes = mappingChanges(serviceSystemMappings.value, mappedCatalogIds.value, defaultMappedCatalogId.value) }
  catch (cause) { serviceSystemError.value = cause instanceof Error ? cause.message : '关联配置无效。'; return }
  if (!changes.length) { serviceSystemNotice.value = '关联配置没有变化。'; return }
  serviceSystemSaving.value = true; serviceSystemError.value = ''; serviceSystemNotice.value = ''
  try {
    // The existing API is per-row, not atomic. Apply explicit diffs sequentially and never claim rollback on partial failure.
    for (const mapping of changes) {
      if (!alive(identity) || !mappingScope.accepts(scopeRequest)) return
      await serviceSystemAdminApi.saveCatalogMappings(system.systemCode, { version: system.version, moduleCode, mappings: [mapping] })
    }
    if (!alive(identity) || !mappingScope.accepts(scopeRequest)) return
    await loadServiceSystemMappings()
    if (alive(identity) && mappingReady.value) serviceSystemNotice.value = '服务关联已保存并重新核对；这不会发布服务或部署流程。'
  } catch (cause) {
    if (alive(identity)) { mappingReady.value = false; serviceSystemError.value = `部分关联可能已保存，请先重新读取核对，禁止直接重试覆盖。 ${cause instanceof ApiError ? cause.message : ''}` }
  } finally { if (alive(identity)) serviceSystemSaving.value = false }
}
async function reloadIdentity(): Promise<void> {
  const nextSubject = session.currentUser ? `${session.currentUser.iamUserId}\u0000${session.currentUser.organizationIamOrganizationId}` : ''
  const sameSubject = Boolean(nextSubject && loadedSubject === nextSubject)
  const preserveDesigner = preserveDesignerDuringIdentityRefresh(workspaceSection.value === 'DESIGN', sameSubject, designerState.value)
  const identity = identityScope.next()
  if (!preserveDesigner) {
    if (workspaceSection.value === 'DESIGN' && designerState.value.uncertain && loadedSubject && nextSubject !== loadedSubject) window.alert('身份已经变化，未确认的设计创建不能由新身份重试。请使用原身份核对设计列表，避免重复创建。')
    clearDesigner()
  }
  systemScope.next(); systemListScope.next(); mappingScope.next(); formScope.next(); adminScope.next(); routingScope.next()
  items.value = []; adminConfigs.value = []; serviceSystems.value = []; serviceSystemModules.value = []; serviceSystemMappings.value = []; systemLevelMappings.value = []
  publishedScopeItems.value = []; publishedMetadataNotice.value = ''
  if (!preserveDesigner) {
    selectedServiceSystemCode.value = ''; selectedServiceSystemModuleCode.value = ''; loadedModuleCode.value = ''; mappedCatalogIds.value = []; defaultMappedCatalogId.value = ''
    clearOffering(); creatingSystem.value = false; workspaceSection.value = 'SERVICES'
  }
  adminReady.value = false; mappingReady.value = false; publicationRequests.clear()
  showEditor.value = false; showActionDialog.value = false; showAnnouncementForm.value = false; announcements.value = []
  editor.value = { version: 0, code: '', name: '', summary: '', ticketType: 'INCIDENT', categoryCode: '', applicableOrganizationIds: [], fields: [], tagPolicy: { allowStandardTags: true, allowFreeTags: false, maxTags: 10 }, reason: '' }
  resetServiceSystemEditor(); resetServiceSystemModuleEditor(); systemSearch.value = ''; serviceSearch.value = ''
  error.value = ''; managementError.value = ''; managementNotice.value = ''; serviceSystemError.value = ''; serviceSystemNotice.value = ''; announcementError.value = ''; announcementNotice.value = ''
  savingDraft.value = false; runningAction.value = false; serviceSystemSaving.value = false; announcementSubmitting.value = false; routingSaving.value = undefined
  serviceSystemsLoading.value = false; adminLoading.value = false; modulesLoading.value = false; mappingLoading.value = false
  if (session.loading || !session.currentUser) return
  loadedSubject = nextSubject
  if (canManageForms.value) await Promise.all([loadAdmin(), loadServiceSystems()])
  else {
    await loadServiceSystems()
    try { const result = await catalogApi.listPublishedItems(); if (alive(identity) && result.source === 'api') { items.value = result.data.items; adminReady.value = true } }
    catch { if (alive(identity)) error.value = '服务库读取失败。' }
  }
  if (alive(identity) && canManageAnnouncements.value) {
    try { const result = await announcementApi.list(20); if (alive(identity)) announcements.value = result }
    catch { if (alive(identity)) announcementError.value = '公告读取失败，请稍后重试。' }
  }
}
watch(() => JSON.stringify([session.loading, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId, session.authorization]), () => { void reloadIdentity() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; identityScope.next() })
onBeforeRouteLeave(() => !mutationBusy.value && canLeaveDesigner())
</script>

<template>
  <div class="page-heading form-admin-heading"><div><span class="eyebrow">统一配置入口</span><h2>系统服务配置</h2><p>新建或选择系统 → 配置工单服务 → 在当前系统内设计该服务的表单与流程。</p></div><div class="heading-status"><button v-if="canManageForms" class="button button--primary" type="button" :disabled="mutationBusy || designerState.busy" @click="newSystem">＋ 新建系统</button></div></div>
  <div class="system-publishing-boundary"><span><b>系统上架</b>控制系统入口</span><i>≠</i><span><b>服务发布</b>产生独立兼容表单修订</span><i>≠</i><span><b>BPMN 草稿冻结</b>仅为设计快照</span><p>每个服务未来可绑定独立流程发布包；当前自定义 BPMN 与多表单草稿尚不能发布运行。</p></div>
  <section class="panel system-first-panel" :inert="mutationBusy || designerState.busy">
    <div class="system-first-head"><h3>1. 选择业务系统</h3><input v-model="systemSearch" placeholder="搜索系统名称或编码" aria-label="搜索业务系统" /><button class="button button--secondary" type="button" :disabled="serviceSystemsLoading" @click="loadServiceSystems()">刷新系统</button></div>
    <p v-if="serviceSystemsLoading" class="compact-loading">正在读取当前组织范围的系统…</p><p v-if="systemListWarning" class="form-alert">{{ systemListWarning }}</p>
    <div class="system-selector-list"><button v-for="system in filteredSystems" :key="system.systemCode" type="button" :class="{ 'is-selected': selectedServiceSystemCode === system.systemCode }" @click="selectServiceSystem(system)"><span class="system-selector-icon">{{ system.systemName.slice(0, 1) }}</span><span><b>{{ system.systemName }}</b><small>{{ system.systemCode }} · {{ system.owningOrganizationId }}</small></span><em>{{ system.lifecycleStatus === 'PUBLISHED' ? '已上架' : system.lifecycleStatus === 'RETIRED' ? '已下架' : '草稿' }}</em></button></div>
    <p v-if="!serviceSystemsLoading && !filteredSystems.length" class="compact-empty">{{ serviceSystemError ? '系统暂不可用，请重试。' : systemSearch ? '没有匹配的系统。' : '当前范围没有系统，请先新建系统草稿。' }}</p>
  </section>
  <p v-if="serviceSystemError" class="form-alert form-alert--error" role="alert">{{ serviceSystemError }} <button v-if="selectedServiceSystem && !mutationBusy" class="link-button" type="button" @click="selectServiceSystem(selectedServiceSystem)">重新读取系统上下文</button></p>
  <p v-if="serviceSystemNotice" class="form-alert form-alert--success" role="status">{{ serviceSystemNotice }}</p>
  <p v-if="managementError && workspaceSection !== 'DETAIL'" class="form-alert form-alert--error">{{ managementError }} <button class="link-button" type="button" :disabled="mutationBusy || adminLoading" @click="loadAdmin">重读服务库</button></p>
  <nav class="system-workspace-tabs" aria-label="系统服务管理分区" :inert="mutationBusy || designerState.busy"><button type="button" :class="{ active: workspaceSection === 'SERVICES' }" @click="setWorkspace('SERVICES')">工单服务</button><template v-if="canManageForms"><button type="button" :class="{ active: workspaceSection === 'SETTINGS' }" :disabled="!selectedServiceSystem && !creatingSystem" @click="openSystemSettings">系统设置</button><button type="button" :class="{ active: workspaceSection === 'MODULES' }" :disabled="!selectedServiceSystem" @click="setWorkspace('MODULES')">模块设置</button><button type="button" :class="{ active: workspaceSection === 'MAPPINGS' }" :disabled="!selectedServiceSystem" @click="setWorkspace('MAPPINGS')">关联已有服务</button></template><details class="workspace-secondary-tools"><summary>更多工具</summary><div><button type="button" :class="{ active: workspaceSection === 'LIBRARY' }" @click="setWorkspace('LIBRARY')">独立兼容服务库</button><button v-if="canManageAnnouncements" type="button" :class="{ active: workspaceSection === 'ANNOUNCEMENTS' }" @click="setWorkspace('ANNOUNCEMENTS')">公告管理</button></div></details><span v-if="selectedServiceSystem">{{ selectedServiceSystem.systemName }}</span></nav>
  <section v-if="workspaceSection === 'SETTINGS' && selectedServiceSystem && !creatingSystem && canReadDesign" class="saved-system-next"><div><b>系统已保存：{{ selectedServiceSystem.systemName }}</b><span>下一步可在此系统内新建工单设计，或返回工单服务选择已有服务继续配置。</span></div><button class="button button--primary" type="button" :disabled="mutationBusy" @click="openDesigner()">{{ canManageForms ? '下一步：新建工单设计' : '查看系统工单设计' }}</button><button class="button button--secondary" type="button" :disabled="mutationBusy" @click="setWorkspace('SERVICES')">查看工单服务</button></section>
  <section v-if="workspaceSection === 'DESIGN' && designerTarget" class="embedded-system-designer">
    <header class="embedded-designer-context"><div><span>当前系统 · {{ designerTarget.systemName }}</span><b>{{ designerTarget.serviceName || '系统级工单设计（尚未绑定具体服务）' }}</b><small>{{ designerTarget.systemCode }} · {{ designerTarget.organizationId }}{{ designerState.uncertain ? ' · 创建结果待核对' : designerState.busy ? ' · 正在处理，请稍候' : designerState.dirty ? ' · 有未保存修改' : '' }}</small></div><button class="button button--secondary" type="button" :disabled="designerState.busy" @click="setWorkspace('SERVICES')">返回当前系统工单服务</button></header>
    <EmbeddedDesignStudio ref="embeddedDesigner" :key="designerKey" v-bind="designerTarget" v-on="designerListeners" />
  </section>
  <div v-if="selectedServiceSystem && ['SERVICES', 'MAPPINGS', 'MODULES'].includes(workspaceSection)" class="system-module-filter" :inert="mutationBusy"><label for="system-module-context">服务范围</label><select id="system-module-context" v-model="selectedServiceSystemModuleCode" :disabled="modulesLoading" @change="changeModule"><option value="">系统级服务</option><option v-for="module in serviceSystemModules" :key="module.moduleCode" :value="module.moduleCode">{{ module.moduleName }}{{ module.active ? '' : '（已停用）' }}</option></select><span>{{ systemMappingScope === 'MODULE' ? '模块有启用关联时优先，否则继承系统级入口。' : '系统级入口可包含多个同类型、不同业务用途的工单服务。' }}</span></div>
  <section v-if="workspaceSection === 'SERVICES'" class="panel system-offerings-panel" :inert="mutationBusy">
    <p v-if="publishedMetadataNotice" class="form-alert" role="status">{{ publishedMetadataNotice }}</p>
    <div class="panel-header"><div><h3>2. {{ selectedServiceSystem ? selectedServiceSystem.systemName + ' · 工单服务' : '请选择一个系统' }}</h3><p v-if="selectedServiceSystem">{{ mappingReady && inheritedServices ? '当前模块未配置启用关联，以下为继承的系统级服务。' : '只列出此范围的真实启用关联，不按工单技术类型合并。' }}</p></div><button v-if="selectedServiceSystem && !creatingSystem && canReadDesign" class="button button--primary" type="button" :disabled="mutationBusy" @click="openDesigner()">{{ canManageForms ? '＋ 新建工单设计' : '查看系统工单设计' }}</button><input v-if="selectedServiceSystem" v-model="serviceSearch" placeholder="搜索服务名称或编码" aria-label="搜索当前系统服务" /></div>
    <div v-if="!selectedServiceSystem" class="system-start-guide"><b>先建立系统，再配置它的工单服务</b><p>已有系统请在上方选择。新系统保存成功后，可直接在本页继续设计工单表单与流程；不需要跳转其他配置页面。</p><button v-if="canManageForms" class="button button--primary" type="button" @click="newSystem">第一步：新建系统</button></div>
    <p v-else-if="adminLoading || modulesLoading || mappingLoading" class="compact-loading">正在读取服务与版本…</p>
    <p v-else-if="!mappingReady || !adminReady" class="form-alert form-alert--error">服务列表尚未完整读取，无法判定为空。请重试系统上下文或服务库。</p>
    <template v-else><div class="system-offering-grid"><article v-for="{ item, mapping } in filteredOfferings" :key="mapping.serviceCatalogItemId" class="system-offering-card"><template v-if="item"><header><span class="tag tag--blue">{{ typeLabel(item.ticketType) }}</span><span class="tag tag--muted">{{ statusLabel(item.id) }}</span><span v-if="mapping.isDefault" class="offering-default">默认入口</span></header><h4>{{ item.name }}</h4><p>{{ item.summary || '尚未提供服务说明。' }}</p><dl><div><dt>服务编码</dt><dd>{{ item.code }}</dd></div><div><dt>独立表单</dt><dd>{{ item.publishedVersion ? '修订 v' + item.publishedVersion : '尚未发布' }}</dd></div></dl><footer><span>{{ adminConfigs.some(config => config.id === item.id) ? '当前：兼容运行配置' : '已发布旧服务 · 只读' }}</span><button class="button button--secondary" type="button" @click="openOffering(item)">{{ adminConfigs.some(config => config.id === item.id) ? '兼容表单配置' : '查看发布表单' }}</button><button v-if="canReadDesign" class="button button--primary" type="button" @click="openDesigner(item)">{{ canManageForms ? '配置表单与流程' : '查看表单与流程' }}</button></footer></template><template v-else><h4>服务元数据不可用</h4><p>{{ mapping.serviceCatalogItemId }}</p><small>关联仍保留；可能因权限或配置变化不可读取，不据此自动删除。</small></template></article></div><p v-if="!filteredOfferings.length" class="compact-empty">{{ serviceSearch ? '没有匹配的服务。' : '该范围尚无启用服务关联。请在“独立服务库”维护服务后，显式关联已有服务。' }}</p></template>
  </section>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在 API 确实不可用时展示，不代表生产配置或已发布版本。</p><p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <section v-if="workspaceSection === 'LIBRARY'" class="panel config-catalog-panel form-version-panel" :inert="mutationBusy"><div class="panel-header"><div><h3>独立服务库</h3><p>显示当前权限内的所有服务，不表示归属于所选系统。新建仅产生独立服务草稿；发布后需另行关联。</p></div><button v-if="canManageForms" class="button button--primary" type="button" :disabled="adminLoading" @click="openCreate">＋ 新建独立服务</button></div><input v-model="serviceSearch" class="library-search" placeholder="搜索服务名称或编码" aria-label="搜索独立服务库" /><p v-if="adminLoading" class="compact-loading">正在读取服务库…</p><div v-else class="table-scroll"><table><thead><tr><th>工单服务</th><th>技术类型</th><th>独立表单修订</th><th>服务状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in libraryItems" :key="item.id"><td><b>{{ item.name }}</b><small class="table-subtext">{{ item.code }}</small></td><td>{{ typeLabel(item.ticketType) }}</td><td>{{ item.publishedVersion ? 'v' + item.publishedVersion : '尚未发布' }}</td><td>{{ statusLabel(item.id) }}</td><td><button class="link-button" type="button" @click="openOffering(item)">独立配置</button></td></tr><tr v-if="!libraryItems.length && adminReady"><td colspan="5" class="table-empty">没有匹配的独立服务。</td></tr></tbody></table></div></section>
  <div v-if="workspaceSection === 'DETAIL'" class="service-detail-crumb"><button type="button" class="link-button" :disabled="mutationBusy" @click="setWorkspace(selectedServiceSystem ? 'SERVICES' : 'LIBRARY')">← 返回{{ selectedServiceSystem ? '系统服务' : '独立服务库' }}</button><span>3. 独立服务配置 · {{ selected?.name }}</span><small>关联入口不改变服务自身的表单与发布治理。</small></div>
  <div v-if="workspaceSection === 'DETAIL' && selected && selectedServiceSystem && canReadDesign" class="service-design-entry"><div><b>本服务的表单与流程设计</b><p>{{ isDesignableService(selected.id) ? '在当前系统下内嵌编辑，不跳转独立配置页。设计草稿不会改变现有兼容运行版本。' : '该服务未关联到当前系统范围。请先通过关联已有服务配置真实关系，不能仅凭浏览器选择建立归属。' }}</p></div><button class="button button--primary" type="button" :disabled="mutationBusy || !isDesignableService(selected.id)" @click="openDesigner(selected)">{{ canManageForms ? '配置表单与流程' : '查看表单与流程' }}</button></div>
  <p v-if="workspaceSection === 'DETAIL' && selected && !selectedAdmin" class="form-alert" role="status">此服务来自真实公开发布目录（含平台既有服务），当前没有对应的后台管理草稿。这里只读查看已发布表单，不授予编辑、发布或路由配置权限。</p>
  <template v-if="workspaceSection === 'DETAIL' && selected"><section class="config-summary form-version-summary"><div><span class="eyebrow">{{ selectedAdmin ? `当前状态：${selectedAdmin.lifecycleStatus}` : '当前生产版本' }}</span><b>{{ selected.name }} · 表单 v{{ selected.publishedVersion }}</b><span>{{ selected.summary ?? '当前目录未提供摘要。' }}</span></div><div class="tag-row"><span class="tag tag--blue">{{ typeLabel(selected.ticketType) }}</span><span class="tag tag--muted">Schema {{ selected.formSchemaHash.slice(0, 10) }}…</span></div></section><p v-if="loadingForm" class="compact-loading">正在读取已发布表单…</p>
    <template v-else-if="selectedForm"><section class="form-admin-toolbar"><div><b>表单版本工作台</b><span>{{ canManageForms && selectedAdmin ? '使用后台 API 读取与修改；每次写入均带版本条件、CSRF 和幂等键。' : '生产版本只读；所有配置变更须走草稿、校验、审批、发布及审计闭环。' }}</span></div><div v-if="canManageForms && selectedAdmin" class="toolbar-actions"><button v-if="selectedAdmin.lifecycleStatus === 'DRAFT' || selectedAdmin.lifecycleStatus === 'REJECTED'" class="button button--secondary" type="button" @click="openUpdate">编辑草稿</button><button v-if="selectedAdmin.lifecycleStatus === 'DRAFT'" class="button button--primary" type="button" @click="openAction('PUBLISH')">提交发布审批</button><button v-if="selectedAdmin.lifecycleStatus === 'PENDING_REVIEW' && publicationRequestId" class="button button--primary" type="button" @click="openAction('APPROVE')">复核发布</button><button v-if="selectedAdmin.lifecycleStatus === 'PUBLISHED'" class="button button--secondary" type="button" @click="openAction('RETIRE')">停用</button></div></section><p v-if="managementNotice" class="form-alert form-alert--success">{{ managementNotice }}</p><p v-if="managementError" class="form-alert form-alert--error">{{ managementError }}</p>
      <section v-if="canManageForms && selectedAdmin" class="panel routing-policy-panel"><div class="panel-header"><div><h3>节点路由与一线分派</h3><p>提交人不选择处理人。系统按目录和节点策略从 IAM 活跃角色池分派；上一节点指定仅对处理人开放。</p></div><span class="readonly-badge">{{ routingLoading ? '读取中…' : '受控配置' }}</span></div><p v-if="routingError" class="form-alert form-alert--error">{{ routingError }}</p><div v-if="!routingLoading && !routingError && routingPolicies.length === routingNodes.length" class="routing-policy-grid"><article v-for="node in routingNodes" :key="node"><b>{{ nodeLabel(node) }}</b><label>分派方式<select v-model="policyFor(node).mode"><option value="SYSTEM_RANDOM">系统随机分派</option><option value="SHARED_QUEUE">进入共享待办（可抢单）</option><option :disabled="node === 'accept'" value="PREVIOUS_HANDLER_SELECTS">上一节点指定</option></select></label><label>候选角色<select :value="policyFor(node).candidateRoles[0]" @change="policyFor(node).candidateRoles = [String(($event.target as HTMLSelectElement).value)]"><option value="ROLE_FIRST_LINE_SUPPORT">一线支持</option><option value="ROLE_SECOND_LINE_SUPPORT">二线支持</option><option value="ROLE_SERVICE_MANAGER">服务经理</option></select></label><label class="checkbox-field"><input v-model="policyFor(node).enabled" type="checkbox" />启用</label><button class="button button--secondary" type="button" :disabled="routingSaving === node" @click="saveRoutingPolicy(policyFor(node))">{{ routingSaving === node ? '保存中…' : '保存节点策略' }}</button></article></div></section>
      <div class="form-admin-layout"><section class="panel form-field-table-panel"><div class="panel-header"><div><h3>字段清单</h3><p>显示顺序按已发布版本锁定。选择字段可查看安全属性和生效规则。</p></div><span class="readonly-badge">{{ selectedForm.fields.length }} 个业务字段</span></div><div class="table-scroll"><table class="field-definition-table"><thead><tr><th>#</th><th>字段</th><th>类型</th><th>必填</th><th>字典</th><th>显示条件</th></tr></thead><tbody><tr v-for="field in selectedForm.fields" :key="field.code" :class="{ 'field-row--selected': selectedField?.code === field.code }" @click="selectField(field)"><td>{{ field.displayOrder }}</td><td><b>{{ field.label }}</b><span class="table-subtext">{{ field.code }}</span></td><td>{{ fieldTypeLabel[field.type] ?? field.type }}</td><td><span class="field-required" :class="{ 'field-required--off': !field.required }">{{ field.required ? '必填' : '选填' }}</span></td><td>{{ field.dictionaryCode ?? '—' }}</td><td>{{ displayCondition(field) }}</td></tr></tbody></table></div><div class="field-system-strip"><b>系统字段（不可配置）</b><span>申请人、申请部门、IAM 身份快照、工单编号、状态、处理人、流程实例、审计字段由平台维护，不能新增、删除、排序或被浏览器提交改写。</span></div></section>
        <aside class="form-admin-side"><section class="panel field-detail-panel"><div class="panel-header"><div><h3>字段配置详情</h3><p>以下为服务端实际下发的定义；未下发的能力不会推定为已配置。</p></div><span class="readonly-badge">{{ selectedAdmin ? '后台配置' : '只读' }}</span></div><template v-if="selectedField"><dl class="field-definition"><div><dt>字段名称</dt><dd>{{ selectedField.label }}</dd></div><div><dt>字段编码</dt><dd class="mono-text">{{ selectedField.code }}</dd></div><div><dt>字段类型</dt><dd>{{ fieldTypeLabel[selectedField.type] ?? selectedField.type }}</dd></div><div><dt>是否必填</dt><dd>{{ selectedField.required ? '是' : '否' }}</dd></div><div><dt>默认值</dt><dd>{{ selectedAdminField?.defaultValue || defaultValue(selectedField) }}</dd></div><div><dt>帮助提示</dt><dd>{{ selectedField.helpText || '未配置' }}</dd></div><div><dt>字典绑定</dt><dd class="mono-text">{{ selectedField.dictionaryCode ?? '未绑定' }}</dd></div><div><dt>输入校验</dt><dd>{{ selectedAdminField?.maxLength ? `最多 ${selectedAdminField.maxLength} 字` : fieldValidation(selectedField) }}</dd></div><div><dt>显示条件</dt><dd>{{ selectedAdminField?.visibleWhen?.length ? `${selectedAdminField.visibleWhen.length} 条已配置条件` : displayCondition(selectedField) }}</dd></div><div><dt>必填条件</dt><dd>{{ selectedAdminField?.requiredWhen?.length ? `${selectedAdminField.requiredWhen.length} 条已配置条件` : '未配置' }}</dd></div></dl><div class="field-safety"><b>安全属性</b><p>服务端会按字段定义重新校验类型、长度、字典值、条件引用和组织数据范围。富文本仅允许编码为 <code>description</code> 的受控字段。</p></div></template><p v-else class="compact-empty">当前版本没有可展示的业务字段。</p></section>
          <section class="panel form-capability-panel"><div class="panel-header"><div><h3>可配置能力边界</h3><p>普通字段与受控富文本均由后端版本契约限制。</p></div></div><ul><li><b>普通字段</b><span>文本、长文本、日期时间、开关、单选、多选、标签、配置项引用。</span></li><li><b>受控富文本</b><span>仅允许 <code>description</code>，复用服务端 HTML 白名单、附件扫描、受管图片和对象鉴权。</span></li><li><b>联动规则</b><span>默认值、帮助提示、显示条件、字典和校验规则会随版本冻结。</span></li></ul></section></aside></div>
      <section class="panel form-governance-panel"><div class="panel-header"><div><h3>发布治理与版本回滚</h3><p>版本发布后冻结字段定义、字典引用和表单哈希；历史工单保存提交时快照。</p></div><button v-if="canManageForms && selectedAdmin" class="button button--secondary" type="button" @click="openAction('ROLLBACK')">创建回滚草稿</button></div><div class="governance-steps"><article><b>1. 草稿编辑</b><span>新增/删除/排序字段，维护必填、默认值、帮助、字典、条件和校验。</span></article><article><b>2. 服务端校验</b><span>保存草稿时校验字段编码、字典、条件引用、富文本和组织数据范围。</span></article><article><b>3. 双人复核发布</b><span>申请人不能审批自己的发布请求，审批后产生不可变生产版本。</span></article><article><b>4. 受控回滚</b><span>创建基于历史版本的新草稿，不覆盖原版本和历史工单快照。</span></article></div></section>
      <section class="panel config-policy-panel"><div class="panel-header"><div><h3>标签与字段策略</h3><p>目录标签和字段可见性均由服务端重新校验，前端只呈现当前发布配置。</p></div></div><div class="tag-row config-tags"><span v-for="tag in selected.tags" :key="tag.code" class="tag tag--blue">{{ tag.name }}</span><span v-if="!selected.tags?.length" class="table-subtext">未配置标准标签</span></div><div class="policy-summary"><div><b>标准标签</b><span>{{ selectedForm.tagPolicy.allowStandardTags ? '允许' : '不允许' }}</span></div><div><b>自定义标签</b><span>{{ selectedForm.tagPolicy.allowFreeTags ? '允许' : '不允许' }}</span></div><div><b>标签上限</b><span>{{ selectedForm.tagPolicy.maxTags }} 个</span></div><div><b>规则案例</b><span>仅返回当前身份可见的脱敏建议</span></div></div></section>
    </template>
  </template>
  <section v-if="canManageForms && ['SETTINGS', 'MODULES', 'MAPPINGS'].includes(workspaceSection) && (selectedServiceSystem || creatingSystem)" class="panel service-system-registry-panel" :data-section="workspaceSection" :inert="mutationBusy">
    <div class="panel-header"><div><h3>{{ workspaceSection === 'SETTINGS' ? creatingSystem ? '新建系统草稿' : '系统基础设置' : workspaceSection === 'MODULES' ? '模块设置' : '显式关联已有服务' }}</h3><p>系统、服务与流程设计各自治理。这里不会自动发布服务，也不会部署 BPMN 草稿。</p></div></div>
    <p v-if="!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT'" class="form-alert">当前系统不是草稿，基础信息、模块与关联只读。现有后端暂不支持已上架系统的新修订编辑；不要通过下架系统绕过发布治理。</p>
    <p v-if="serviceSystemError" class="form-alert form-alert--error">{{ serviceSystemError }}</p><p v-if="serviceSystemNotice" class="form-alert form-alert--success">{{ serviceSystemNotice }}</p>
    <div class="service-system-layout">
      <section class="service-system-list legacy-system-list"><div class="service-system-list__head"><b>已登记系统</b><button class="button button--secondary" type="button" @click="resetServiceSystemEditor()">新建系统</button></div><button v-for="system in serviceSystems" :key="system.systemCode" class="service-system-list__item" :class="{ 'is-selected': selectedServiceSystemCode === system.systemCode }" type="button" @click="selectServiceSystem(system)"><span><b>{{ system.systemName }}</b><small>{{ system.systemCode }} · {{ system.owningOrganizationId }}</small></span><em :class="`config-status--${system.lifecycleStatus.toLowerCase()}`">{{ system.lifecycleStatus === 'PUBLISHED' ? '已发布' : system.lifecycleStatus === 'RETIRED' ? '已退役' : '草稿' }}</em></button><p v-if="!serviceSystems.length && !serviceSystemsLoading" class="compact-empty">暂无服务系统。请新建草稿后由服务端按权限和组织范围校验。</p></section>
      <div class="service-system-workspace">
        <form v-if="workspaceSection === 'SETTINGS'" class="service-system-form" @submit.prevent="saveServiceSystem"><div class="service-system-form__heading"><b>{{ creatingSystem ? '新建系统草稿' : '编辑系统信息' }}</b><span>系统编码创建后不可变；每次保存均使用版本条件并写入审计。</span></div><div class="editor-grid"><label class="field"><span>系统编码 <b>*</b></span><input v-model.trim="serviceSystemEditor.systemCode" :disabled="!creatingSystem" maxlength="64" placeholder="例如 ERP" /></label><label class="field"><span>系统名称 <b>*</b></span><input v-model.trim="serviceSystemEditor.systemName" :disabled="!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT'" maxlength="200" placeholder="例如 ERP 业务系统" /></label><label class="field"><span>所属组织 IAM ID <b>*</b></span><input v-model.trim="serviceSystemEditor.owningOrganizationId" :disabled="!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT'" maxlength="128" placeholder="例如 ORG-IT" /></label><label class="field"><span>状态 <b>*</b></span><select v-model="serviceSystemEditor.lifecycleStatus" disabled aria-label="系统上架状态（只读）"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="RETIRED">已退役</option></select></label><label class="field"><span>关联 CMDB CI</span><input v-model.trim="serviceSystemEditor.ciId" :disabled="!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT'" maxlength="128" placeholder="只读 CI 编码（可选）" /></label><label class="field"><span>系统负责人 IAM ID</span><input v-model.trim="serviceSystemEditor.ownerIamUserId" :disabled="!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT'" maxlength="128" placeholder="例如 iam-user-id（可选）" /></label><label class="field editor-wide"><span>变更说明 <b>*</b></span><input v-model.trim="serviceSystemEditor.reason" maxlength="500" placeholder="至少 4 个字符，将写入审计。" /></label></div><div class="service-system-actions"><button v-if="!creatingSystem && selectedServiceSystem?.lifecycleStatus === 'DRAFT'" class="button button--secondary" type="button" @click="changeSystemStatus('publish')">单独上架系统</button><button v-if="!creatingSystem && selectedServiceSystem?.lifecycleStatus === 'PUBLISHED'" class="button button--secondary" type="button" @click="changeSystemStatus('retire')">下架系统</button><button class="button button--primary" type="submit" :disabled="serviceSystemSaving || (!creatingSystem && selectedServiceSystem?.lifecycleStatus !== 'DRAFT')">{{ serviceSystemSaving ? '保存中…' : '保存系统信息' }}</button></div></form>
        <template v-if="selectedServiceSystem">
          <div v-if="workspaceSection === 'MODULES'" class="service-system-subsection"><div class="service-system-form__heading"><b>受影响模块 / 页面</b><span>停用模块不会出现在新工单的模块选择器中；历史快照不受影响。</span></div><div class="service-system-module-layout"><div class="service-system-module-list"><button v-for="module in serviceSystemModules" :key="module.moduleCode" type="button" :class="{ 'is-selected': selectedServiceSystemModuleCode === module.moduleCode }" @click="selectedServiceSystemModuleCode = module.moduleCode; changeModule()"><b>{{ module.moduleName }}</b><small>{{ module.moduleCode }}{{ module.active ? '' : ' · 已停用' }}</small></button><button type="button" @click="selectedServiceSystemModuleCode = ''; resetServiceSystemModuleEditor()">+ 新增模块</button></div><form class="service-system-module-form" :inert="selectedServiceSystem?.lifecycleStatus !== 'DRAFT' || modulesLoading" @submit.prevent="saveServiceSystemModule"><label class="field"><span>模块编码 <b>*</b></span><input v-model.trim="serviceSystemModuleEditor.moduleCode" :disabled="Boolean(selectedServiceSystemModule)" maxlength="64" placeholder="例如 ORDER_QUERY" /></label><label class="field"><span>模块名称 <b>*</b></span><input v-model.trim="serviceSystemModuleEditor.moduleName" maxlength="200" placeholder="例如 采购订单查询" /></label><label class="field"><span>页面 / 路径</span><input v-model.trim="serviceSystemModuleEditor.modulePath" maxlength="500" placeholder="例如 /order/query" /></label><label class="field"><span>排序</span><input v-model.number="serviceSystemModuleEditor.sortOrder" type="number" min="0" max="999999" /></label><label class="checkbox-field"><input v-model="serviceSystemModuleEditor.active" type="checkbox" />在新工单中启用</label><button class="button button--secondary" type="submit" :disabled="serviceSystemSaving">保存模块</button></form></div></div>
          <div v-if="workspaceSection === 'MAPPINGS'" class="service-system-subsection"><div class="service-system-form__heading"><b>当前范围的直接关联</b><span>模块级映射优先于系统级映射；前端仅收敛选项，提交时由服务端重新校验。</span></div><div class="mapping-scope"><b>{{ systemMappingScope === 'MODULE' ? selectedServiceSystemModule?.moduleName + ' · 模块直接关联' : '系统级直接关联' }}</b><span>请通过上方服务范围切换。未勾选即移除此范围的关联；继承展示不会自动写入模块配置。</span></div><div class="mapping-editor" :inert="!mappingReady || mappingLoading || !adminReady || selectedServiceSystem?.lifecycleStatus !== 'DRAFT' || (systemMappingScope === 'MODULE' && !selectedServiceSystemModule?.active)"><el-checkbox-group v-model="mappedCatalogIds"><el-checkbox v-for="catalog in publishedCatalogOptions" :key="catalog.id" :value="catalog.id">{{ catalog.name }} <small>{{ catalog.code }}</small></el-checkbox></el-checkbox-group><label class="field"><span>默认服务目录</span><select v-model="defaultMappedCatalogId"><option value="">不指定默认目录</option><option v-for="catalog in publishedCatalogOptions.filter((item) => mappedCatalogIds.includes(item.id))" :key="catalog.id" :value="catalog.id">{{ catalog.name }}</option></select></label><button class="button button--secondary" type="button" :disabled="!mappingSaveAllowed" @click="saveServiceSystemMappings">保存目录映射</button></div></div>
        </template>
      </div>
    </div>
  </section>
  <div v-if="showEditor" class="modal-backdrop" @mousedown.self="showEditor = false"><section class="action-modal form-editor-modal" role="dialog" aria-modal="true" :aria-label="editorMode === 'CREATE' ? '新建表单草稿' : '编辑表单草稿'"><div class="modal-heading"><div><span class="eyebrow">{{ editorMode === 'CREATE' ? '新建草稿' : '编辑草稿' }}</span><h3>{{ editorMode === 'CREATE' ? '创建服务表单草稿' : '更新服务表单草稿' }}</h3><p>保存前会由服务端校验字段编码、系统字段、字典、条件引用、富文本及组织数据范围。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="showEditor = false">×</button></div><form class="action-form form-editor-form" @submit.prevent="saveDraft"><div class="editor-grid"><label class="field"><span>目录编码 <b>*</b></span><input v-model.trim="editor.code" :disabled="editorMode === 'UPDATE'" maxlength="64" placeholder="例如 INC_BROWSER_HELP" /></label><label class="field"><span>目录名称 <b>*</b></span><input v-model.trim="editor.name" maxlength="200" placeholder="例如 浏览器使用支持" /></label><label class="field"><span>工单类型 <b>*</b></span><select v-model="editor.ticketType"><option value="INCIDENT">故障报修</option><option value="ACCESS_REQUEST">账号权限</option><option value="SERVICE_REQUEST">服务请求</option><option value="PROBLEM">问题管理</option><option value="CHANGE">变更</option></select></label><label class="field"><span>分类编码 <b>*</b></span><input v-model.trim="editor.categoryCode" maxlength="64" placeholder="例如 DESKTOP" /></label><label class="field editor-wide"><span>适用组织 IAM ID <b>*</b></span><input :value="editor.applicableOrganizationIds.join(', ')" maxlength="5000" placeholder="以英文逗号分隔，例如 ORG-IT, ORG-HQ" @input="editor.applicableOrganizationIds = ($event.target as HTMLInputElement).value.split(',').map((value) => value.trim()).filter(Boolean)" /><small>服务经理只能配置自身组织数据范围内的目录；平台管理员可配置全量组织。</small></label><label class="field editor-wide"><span>服务说明</span><input v-model.trim="editor.summary" maxlength="500" placeholder="面向提交人的简短说明" /></label></div><section class="editor-section"><div class="editor-section-heading"><div><b>业务字段</b><span>字段显示顺序由列表顺序决定；系统字段无法加入。</span></div><button class="button button--secondary" type="button" @click="addField">添加字段</button></div><article v-for="(field, index) in editor.fields" :key="`${field.code}-${index}`" class="editor-field-card"><div class="editor-field-head"><b>#{{ index + 1 }} {{ field.label || '未命名字段' }}</b><button class="text-danger-button" type="button" :disabled="editor.fields.length <= 1" @click="removeField(index)">移除</button></div><div class="editor-grid editor-grid--field"><label class="field"><span>字段编码 <b>*</b></span><input v-model.trim="field.code" maxlength="64" placeholder="例如 affected_system" /></label><label class="field"><span>字段名称 <b>*</b></span><input v-model.trim="field.label" maxlength="80" placeholder="例如 影响系统" /></label><label class="field"><span>字段类型 <b>*</b></span><select v-model="field.type"><option v-for="type in configurableFieldTypes" :key="type" :value="type" :disabled="type === 'RICH_TEXT' && field.code !== 'description'">{{ fieldTypeLabel[type] }}</option></select></label><label class="field"><span>最大长度</span><input v-model.number="field.maxLength" type="number" min="1" max="4000" placeholder="按需填写" /></label><label class="field"><span>默认值</span><input v-model.trim="field.defaultValue" maxlength="4000" placeholder="可选" /></label><label class="field"><span>字典编码</span><input v-model.trim="field.dictionaryCode" maxlength="63" placeholder="仅单选 / 多选" /></label><label class="field editor-wide"><span>帮助提示</span><input v-model.trim="field.helpText" maxlength="300" placeholder="对提交人的填写说明" /></label><label class="checkbox-field"><input v-model="field.required" type="checkbox" />始终必填</label></div><details class="condition-editor"><summary>显示 / 必填条件（最多各 10 条）</summary><div class="condition-grid"><div><b>显示条件</b><template v-for="(condition, conditionIndex) in field.visibleWhen" :key="`visible-${conditionIndex}`"><div class="condition-row"><input v-model.trim="condition.fieldCode" maxlength="64" placeholder="依赖字段编码" /><select v-model="condition.operator"><option v-for="operator in conditionOperators" :key="operator" :value="operator">{{ operator }}</option></select><input :value="condition.values.join(',')" maxlength="10000" placeholder="值，逗号分隔" @input="condition.values = ($event.target as HTMLInputElement).value.split(',').filter(Boolean)" /><button type="button" @click="field.visibleWhen.splice(conditionIndex, 1)">×</button></div></template><button class="link-button" type="button" :disabled="field.visibleWhen.length >= 10" @click="field.visibleWhen.push({ fieldCode: '', operator: 'EQUALS', values: [] })">+ 添加显示条件</button></div><div><b>必填条件</b><template v-for="(condition, conditionIndex) in field.requiredWhen" :key="`required-${conditionIndex}`"><div class="condition-row"><input v-model.trim="condition.fieldCode" maxlength="64" placeholder="依赖字段编码" /><select v-model="condition.operator"><option v-for="operator in conditionOperators" :key="operator" :value="operator">{{ operator }}</option></select><input :value="condition.values.join(',')" maxlength="10000" placeholder="值，逗号分隔" @input="condition.values = ($event.target as HTMLInputElement).value.split(',').filter(Boolean)" /><button type="button" @click="field.requiredWhen.splice(conditionIndex, 1)">×</button></div></template><button class="link-button" type="button" :disabled="field.requiredWhen.length >= 10" @click="field.requiredWhen.push({ fieldCode: '', operator: 'EQUALS', values: [] })">+ 添加必填条件</button></div></div></details></article></section><section class="editor-section"><div class="editor-section-heading"><div><b>标签策略</b><span>标准标签白名单使用逗号分隔的已发布标签编码。</span></div></div><div class="editor-grid"><label class="checkbox-field"><input v-model="editor.tagPolicy.allowStandardTags" type="checkbox" />允许标准标签</label><label class="checkbox-field"><input v-model="editor.tagPolicy.allowFreeTags" type="checkbox" />允许自由标签</label><label class="field"><span>标签上限</span><input v-model.number="editor.tagPolicy.maxTags" type="number" min="0" max="20" /></label><label class="field"><span>标准标签白名单</span><input :value="editor.tagPolicy.allowedStandardTagCodes?.join(', ')" placeholder="TAG-XXX, TAG-YYY" @input="editor.tagPolicy.allowedStandardTagCodes = ($event.target as HTMLInputElement).value.split(',').map((value) => value.trim()).filter(Boolean)" /></label></div></section><label class="field"><span>本次变更说明 <b>*</b></span><textarea v-model.trim="editor.reason" rows="3" maxlength="500" placeholder="说明变更原因，至少 4 个字符；将写入审计。" /></label><p v-if="managementError" class="form-alert form-alert--error">{{ managementError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="savingDraft" @click="showEditor = false">取消</button><button class="button button--primary" type="submit" :disabled="savingDraft">{{ savingDraft ? '保存中…' : '保存草稿并校验' }}</button></div></form></section></div>
  <div v-if="showActionDialog" class="modal-backdrop" @mousedown.self="showActionDialog = false"><section class="action-modal" role="dialog" aria-modal="true" aria-label="表单生命周期操作"><div class="modal-heading"><div><span class="eyebrow">{{ actionKind }}</span><h3>{{ actionKind === 'PUBLISH' ? '提交发布审批' : actionKind === 'APPROVE' ? '复核发布' : actionKind === 'RETIRE' ? '停用表单' : '创建回滚草稿' }}</h3><p>{{ actionKind === 'APPROVE' ? '发布申请必须由非申请人复核；服务端会拒绝自审。' : '操作会使用当前配置版本作为并发条件；如已被其他管理员更新，服务端会返回冲突。' }}</p></div><button class="modal-close" type="button" aria-label="关闭" @click="showActionDialog = false">×</button></div><form class="action-form" @submit.prevent="executeAction"><label v-if="actionKind === 'ROLLBACK'" class="field"><span>回滚来源表单版本 <b>*</b></span><input v-model.number="rollbackVersion" type="number" min="1" :max="Math.max(1, (selectedAdmin?.formVersion ?? 1) - 1)" /></label><label v-if="actionKind !== 'APPROVE'" class="field"><span>操作原因 <b>*</b></span><textarea v-model.trim="actionReason" rows="3" maxlength="500" placeholder="至少 4 个字符，将写入审计。" /></label><p v-if="actionKind === 'APPROVE' && !publicationRequestId" class="form-alert form-alert--error">当前浏览器会话没有可复核的发布请求号；请由申请人提交发布审批后再复核。</p><p v-if="managementError" class="form-alert form-alert--error">{{ managementError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="runningAction" @click="showActionDialog = false">取消</button><button class="button button--primary" type="submit" :disabled="runningAction || (actionKind === 'APPROVE' && !publicationRequestId)">{{ runningAction ? '处理中…' : '确认执行' }}</button></div></form></section></div>
  <section v-if="canManageAnnouncements && workspaceSection === 'ANNOUNCEMENTS'" class="panel announcement-admin-panel"><div class="panel-header"><div><h3>公告管理</h3><p>只显示当前管理员可见的有效公告；发布范围、有效期和权限均由服务端校验。</p></div><button class="button button--primary" type="button" @click="openAnnouncementForm">发布公告</button></div><p v-if="announcementNotice" class="form-alert form-alert--success">{{ announcementNotice }}</p><div class="table-scroll"><table><thead><tr><th>公告</th><th>阅读范围</th><th>有效至</th><th>状态</th></tr></thead><tbody><tr v-for="item in announcements" :key="item.id"><td><b>{{ item.title }}</b><small class="table-subtext">{{ item.body }}</small></td><td>{{ item.audienceScope === 'ALL' ? '全员' : '指定组织' }}</td><td>{{ new Date(item.effectiveUntil).toLocaleString('zh-CN', { hour12: false }) }}</td><td><span class="tag" :class="item.pinned ? 'tag--orange' : 'tag--muted'">{{ item.pinned ? '置顶' : '有效' }}</span></td></tr><tr v-if="!announcements.length"><td colspan="4" class="table-empty">当前范围内暂无有效公告。</td></tr></tbody></table></div></section>
  <div v-if="showAnnouncementForm" class="modal-backdrop" @mousedown.self="showAnnouncementForm = false"><section class="action-modal" role="dialog" aria-modal="true" aria-label="发布公告"><div class="modal-heading"><div><span class="eyebrow">公告管理</span><h3>发布公告</h3><p>服务端会以当前 IAM 身份写入发布人和审计记录；不接收浏览器传入的发布人。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="showAnnouncementForm = false">×</button></div><form class="action-form" @submit.prevent="submitAnnouncement"><label class="field"><span>公告标题 <b>*</b></span><input v-model.trim="announcementForm.title" maxlength="200" placeholder="例如：周末服务窗口调整" /></label><label class="field"><span>公告正文 <b>*</b></span><textarea v-model.trim="announcementForm.body" maxlength="4000" rows="4" placeholder="填写服务影响、时间和用户需要采取的操作。请勿填写密码、令牌或敏感配置。" /></label><label class="field"><span>阅读范围 <b>*</b></span><select v-model="announcementForm.audienceScope"><option value="ALL">全员</option><option value="ORGANIZATION">指定组织</option></select></label><label v-if="announcementForm.audienceScope === 'ORGANIZATION'" class="field"><span>组织 IAM ID <b>*</b></span><input v-model.trim="announcementForm.targetOrganizationIamId" maxlength="128" placeholder="例如 ORG-LOCAL-IT" /><small>服务端不接受组织名称，并在发布时校验数据范围。</small></label><label class="field"><span>有效至 <b>*</b></span><input v-model="announcementForm.effectiveUntil" type="datetime-local" /></label><label class="checkbox-field"><input v-model="announcementForm.pinned" type="checkbox" />置顶显示</label><p v-if="announcementError" class="form-alert form-alert--error">{{ announcementError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="announcementSubmitting" @click="showAnnouncementForm = false">取消</button><button class="button button--primary" type="submit" :disabled="announcementSubmitting">{{ announcementSubmitting ? '发布中…' : '确认发布' }}</button></div></form></section></div>
</template>

<style scoped>
.workspace-secondary-tools { position:relative; margin-left:auto; font-size:11px; color:#798a9e; }.workspace-secondary-tools summary { cursor:pointer; padding:9px; }.workspace-secondary-tools > div { display:flex; flex-wrap:wrap; gap:6px; margin-top:7px; padding:9px; border:1px solid #dce5ef; border-radius:5px; background:#f8fafc; }.workspace-secondary-tools:not([open]) + span { margin-left:0; }
.saved-system-next,.service-design-entry,.embedded-designer-context { display:flex; gap:12px; align-items:center; flex-wrap:wrap; border:1px solid #cddfee; background:#f2f8ff; border-radius:6px; padding:14px 16px; margin:14px 0; }.saved-system-next > div,.service-design-entry > div { flex:1; min-width:220px; display:grid; gap:6px; }.saved-system-next b,.service-design-entry b { color:#375d85; font-size:13px; }.saved-system-next span,.service-design-entry p { margin:0; color:#7b8da4; font-size:11px; line-height:1.65; }.embedded-system-designer { min-width:0; }.embedded-designer-context > div { display:grid; flex:1; gap:5px; min-width:0; }.embedded-designer-context span { color:#6d89a9; font-size:11px; }.embedded-designer-context b { color:#315981; font-size:15px; }.embedded-designer-context small { color:#8c9bb0; font-size:11px; overflow-wrap:anywhere; }.system-start-guide b { display:block; font-size:15px; color:#547799; }.system-start-guide p { margin:10px auto 18px; max-width:680px; }.system-offering-card footer .button--primary { background:#326cb9; border-color:#326cb9; color:#fff; }
@media(max-width:720px) { .workspace-secondary-tools { margin-left:0; }.saved-system-next .button,.service-design-entry .button,.embedded-designer-context > button { width:100%; justify-content:center; } }
.system-publishing-boundary { display:flex; flex-wrap:wrap; gap:8px 14px; align-items:center; margin-bottom:16px; padding:12px 16px; border:1px solid #dce6f1; background:#f7faff; border-radius:6px; color:#76899e; font-size:11px; }
.system-publishing-boundary b { color:#466b94; margin-right:5px; }.system-publishing-boundary i { color:#a3b1c1; font-style:normal; }.system-publishing-boundary p { flex-basis:100%; margin:0; color:#8b7a50; font-size:11px; }
.system-first-panel { padding:16px; }.system-first-head { display:flex; gap:12px; align-items:center; margin-bottom:14px; }.system-first-head h3 { margin:0 auto 0 0; color:#3d5d80; font-size:14px; }
.system-first-head input,.system-offerings-panel .panel-header input,.library-search,.system-module-filter select { min-width:0; padding:8px 10px; border:1px solid #d6e1ee; background:#fff; border-radius:5px; color:#425c79; font-size:12px; }.library-search { margin-bottom:14px; min-width:240px; }
.system-selector-list { display:grid; grid-template-columns:repeat(auto-fill,minmax(235px,1fr)); gap:10px; }
.system-selector-list > button { display:flex; gap:10px; align-items:center; padding:13px 12px; border:1px solid #e0e7ef; border-radius:6px; background:#fff; color:#496785; text-align:left; cursor:pointer; }.system-selector-list > button:hover,.system-selector-list > button.is-selected { background:#eef6ff; border-color:#679bdd; box-shadow:0 1px 4px #e6edf7; }
.system-selector-icon { display:grid; place-items:center; width:36px; height:36px; flex-shrink:0; border-radius:8px; background:#e7f0fd; color:#3a70b5; font-size:16px; font-weight:700; }.system-selector-list button > span:not(.system-selector-icon) { display:grid; gap:5px; min-width:0; }.system-selector-list b { font-size:13px; }.system-selector-list small { font-size:10px; color:#8696a8; overflow-wrap:anywhere; }.system-selector-list em { font-style:normal; font-size:10px; margin-left:auto; white-space:nowrap; color:#7b8fa6; }
.system-workspace-tabs { display:flex; flex-wrap:wrap; gap:7px; align-items:center; margin:16px 0 12px; }.system-workspace-tabs button { padding:9px 14px; border:1px solid #dbe4ef; background:#fff; color:#57718c; font-size:12px; border-radius:5px; cursor:pointer; }.system-workspace-tabs button.active { background:#326cb9; border-color:#326cb9; color:#fff; }.system-workspace-tabs button:disabled { opacity:.45; cursor:not-allowed; }.system-workspace-tabs > span { margin-left:auto; font-size:12px; color:#6582a2; }
.system-module-filter { display:flex; gap:10px; align-items:center; flex-wrap:wrap; padding:10px 14px; margin-bottom:12px; background:#f0f5fb; border:1px solid #e0e8f2; border-radius:5px; font-size:12px; color:#557293; }.system-module-filter span { font-size:11px; color:#7c8fa6; }
.system-offerings-panel { padding:16px; }.system-offering-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(285px,1fr)); gap:14px; }.system-offering-card { min-width:0; border:1px solid #dce6f1; border-radius:6px; padding:16px; background:#fff; }.system-offering-card > header { display:flex; flex-wrap:wrap; gap:7px; align-items:center; }.offering-default { margin-left:auto; color:#ad823c; font-size:10px; }.system-offering-card h4 { margin:16px 0 7px; font-size:15px; color:#36587e; }.system-offering-card > p { font-size:12px; line-height:1.65; color:#8493a6; min-height:39px; margin:0 0 14px; }.system-offering-card dl { display:flex; gap:20px; padding-top:12px; border-top:1px solid #eff2f6; }.system-offering-card dt { color:#8a9aab; font-size:10px; margin-bottom:6px; }.system-offering-card dd { color:#557392; font-size:11px; margin:0; overflow-wrap:anywhere; }.system-offering-card footer { display:flex; align-items:center; flex-wrap:wrap; gap:9px; margin-top:15px; }.system-offering-card footer span { font-size:10px; color:#899bb1; }.system-offering-card footer button { margin-left:auto; font-size:11px; }.system-offering-card > small { font-size:11px; color:#947446; }.system-start-guide { padding:36px 16px; text-align:center; color:#8092a7; font-size:13px; line-height:1.8; }
.service-detail-crumb { display:flex; flex-wrap:wrap; align-items:center; gap:12px; margin:16px 0; font-size:13px; color:#4e7096; }.service-detail-crumb small { margin-left:auto; color:#8496ac; font-size:11px; }
.service-system-registry-panel .legacy-system-list { display:none; }.service-system-registry-panel .service-system-layout { grid-template-columns:minmax(0,1fr); margin-top:0; }.service-system-registry-panel .service-system-form { max-width:100%; }.service-system-registry-panel .service-system-actions { gap:8px; }.service-system-registry-panel .mapping-editor { grid-template-columns:1fr; align-items:start; }.service-system-registry-panel .mapping-editor > .button { justify-self:start; }.service-system-registry-panel .mapping-editor .field { max-width:320px; }.service-system-registry-panel .service-system-form input:disabled { background:#f4f7fb; color:#8393a6; }
@media(max-width:720px) { .system-first-head { flex-wrap:wrap; }.system-first-head h3 { flex-basis:100%; }.system-first-head input { flex:1; }.system-selector-list,.system-offering-grid { grid-template-columns:1fr; }.system-workspace-tabs > span { flex-basis:100%; margin:0; }.system-offerings-panel .panel-header { flex-direction:column; align-items:stretch; }.system-offering-card footer button { width:100%; margin-left:0; } }

.routing-policy-panel { margin-bottom: 14px; }.routing-policy-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.routing-policy-grid article { display: grid; gap: 7px; padding: 10px; border: 1px solid #e1eaf2; border-radius: 4px; background: #fbfdff; }.routing-policy-grid b { color: #375a78; font-size: 12px; }.routing-policy-grid label { display: grid; gap: 3px; color: #70849a; font-size: 10px; }.routing-policy-grid select { min-width: 0; padding: 6px; border: 1px solid #d6e2ee; border-radius: 3px; color: #405e7c; background: #fff; font-size: 11px; }.routing-policy-grid .checkbox-field { display: flex; align-items: center; gap: 5px; }.routing-policy-grid .button { justify-content: center; min-height: 29px; padding: 5px 8px; font-size: 11px; } @media (max-width: 1020px) { .routing-policy-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } } @media (max-width: 720px) { .routing-policy-grid { grid-template-columns: 1fr; } }
.service-system-registry-panel { margin-top: 14px; padding: 14px 16px; }.service-system-layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 14px; margin-top: 12px; }.service-system-list { display: grid; align-content: start; gap: 6px; padding: 10px; border: 1px solid #dfe8f1; border-radius: 5px; background: #f9fbfd; }.service-system-list__head { display: flex; align-items: center; justify-content: space-between; gap: 7px; padding: 1px 1px 7px; border-bottom: 1px solid #e5ebf1; color: #3f5d79; font-size: 12px; }.service-system-list__head .button { min-height: 28px; padding: 4px 8px; font-size: 11px; }.service-system-list__item { display: flex; align-items: center; justify-content: space-between; gap: 8px; width: 100%; padding: 8px; border: 1px solid transparent; border-radius: 5px; color: #48627c; background: transparent; text-align: left; cursor: pointer; }.service-system-list__item:hover, .service-system-list__item.is-selected { border-color: #cbdff3; background: #eff7ff; }.service-system-list__item span { display: grid; min-width: 0; gap: 3px; }.service-system-list__item b { overflow: hidden; color: #3c5a77; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.service-system-list__item small { overflow: hidden; color: #8090a1; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.service-system-list__item em { flex: 0 0 auto; padding: 2px 5px; border-radius: 9px; font-size: 9px; font-style: normal; }.service-system-workspace { display: grid; align-content: start; gap: 13px; }.service-system-form, .service-system-subsection { padding: 12px; border: 1px solid #dfe8f1; border-radius: 5px; background: #fff; }.service-system-form { display: grid; gap: 10px; }.service-system-form__heading { display: grid; gap: 3px; margin-bottom: 9px; }.service-system-form__heading b { color: #3e5d7b; font-size: 12px; }.service-system-form__heading span { color: #7d8fa1; font-size: 10px; line-height: 1.5; }.service-system-form .field, .service-system-module-form .field, .mapping-editor .field { display: grid; gap: 4px; color: #667e95; font-size: 10px; }.service-system-form input, .service-system-form select, .service-system-module-form input, .mapping-editor select { min-height: 31px; padding: 6px 8px; border: 1px solid #d7e2ed; border-radius: 5px; color: #425e79; background: #fff; font-size: 11px; outline: 0; }.service-system-form input:focus, .service-system-form select:focus, .service-system-module-form input:focus, .mapping-editor select:focus { border-color: #82b4e5; box-shadow: 0 0 0 2px rgb(48 125 211 / 9%); }.service-system-actions { display: flex; justify-content: flex-end; }.service-system-module-layout { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 10px; }.service-system-module-list { display: grid; align-content: start; gap: 5px; padding-right: 10px; border-right: 1px solid #e5ebf1; }.service-system-module-list button { display: grid; gap: 3px; padding: 7px 8px; border: 1px solid transparent; border-radius: 5px; color: #526e89; background: transparent; text-align: left; cursor: pointer; }.service-system-module-list button:hover, .service-system-module-list button.is-selected { border-color: #cbdff3; background: #eff7ff; }.service-system-module-list b { color: #47627e; font-size: 11px; }.service-system-module-list small { color: #8292a3; font-size: 10px; }.service-system-module-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px 12px; align-content: start; }.service-system-module-form .checkbox-field { display: flex; align-items: center; gap: 6px; color: #617a93; font-size: 11px; }.service-system-module-form .button { justify-self: end; min-height: 30px; }.mapping-scope { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 9px; color: #637d96; font-size: 11px; }.mapping-scope label { display: flex; align-items: center; gap: 4px; }.mapping-scope span { color: #8091a2; font-size: 10px; }.mapping-editor { display: grid; grid-template-columns: minmax(0, 1fr) 190px auto; align-items: end; gap: 10px; }.mapping-editor :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 7px 13px; padding: 9px; border: 1px solid #dfe8f1; border-radius: 5px; background: #fbfdff; }.mapping-editor :deep(.el-checkbox) { margin-right: 0; color: #526e89; font-size: 11px; }.mapping-editor :deep(.el-checkbox small) { margin-left: 3px; color: #8796a6; font-size: 10px; }.mapping-editor .button { min-height: 31px; white-space: nowrap; } @media (max-width: 1020px) { .service-system-layout { grid-template-columns: 1fr; }.service-system-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }.service-system-list__head { grid-column: 1 / -1; }.mapping-editor { grid-template-columns: 1fr 180px; }.mapping-editor .button { justify-self: end; } } @media (max-width: 720px) { .service-system-list, .service-system-module-layout, .service-system-module-form, .mapping-editor { grid-template-columns: 1fr; }.service-system-module-list { padding: 0 0 8px; border-right: 0; border-bottom: 1px solid #e5ebf1; }.mapping-editor .button { justify-self: stretch; } }
.form-admin-heading { margin-bottom: 14px; }.heading-status, .toolbar-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 7px; }.form-version-panel { margin-bottom: 14px; }.form-version-summary { margin: 0 0 12px; }.form-version-summary > div:first-child { gap: 3px; }.form-version-summary .eyebrow { color: #66819d; font-size: 10px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }.form-admin-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 12px; padding: 11px 14px; border: 1px solid #d8e5f2; border-radius: 4px; background: #f7fbff; }.form-admin-toolbar > div:first-child { display: grid; gap: 4px; }.form-admin-toolbar b { color: #31516f; font-size: 13px; }.form-admin-toolbar span { color: #71869b; font-size: 11px; }.form-admin-layout { display: grid; grid-template-columns: minmax(0, 1.52fr) minmax(288px, .84fr); gap: 14px; align-items: start; }.form-field-table-panel, .field-detail-panel, .form-capability-panel, .form-governance-panel, .config-policy-panel { padding: 14px 16px; }.field-definition-table tbody tr { cursor: pointer; }.field-definition-table tbody tr:hover, .field-row--selected { background: #f2f8ff; }.field-row--selected { box-shadow: inset 2px 0 #2874db; }.field-required { display: inline-block; padding: 2px 6px; border-radius: 10px; color: #ac3737; background: #fff0f0; font-size: 10px; font-weight: 600; }.field-required--off { color: #718196; background: #f0f3f6; }.field-system-strip { display: grid; grid-template-columns: 112px minmax(0, 1fr); gap: 10px; margin-top: 12px; padding: 10px 11px; border: 1px solid #e4ebf3; border-radius: 4px; color: #718399; background: #f8fafc; font-size: 11px; line-height: 1.55; }.field-system-strip b { color: #4a6681; }.form-admin-side { display: grid; gap: 14px; }.field-definition { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 13px; margin: 0; }.field-definition div { min-width: 0; }.field-definition dt { margin-bottom: 4px; color: #8191a4; font-size: 10px; }.field-definition dd { overflow-wrap: anywhere; margin: 0; color: #405a74; font-size: 11px; line-height: 1.45; }.field-safety { margin-top: 13px; padding: 9px 10px; border-left: 3px solid #4c8bd4; color: #607b96; background: #f4f9ff; font-size: 11px; line-height: 1.55; }.field-safety b { color: #39617f; }.field-safety p { margin: 3px 0 0; }.form-capability-panel ul { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }.form-capability-panel li { display: grid; gap: 3px; padding-bottom: 9px; border-bottom: 1px solid #edf1f6; }.form-capability-panel li:last-child { padding-bottom: 0; border-bottom: 0; }.form-capability-panel b { color: #45627f; font-size: 12px; }.form-capability-panel span { color: #76899e; font-size: 11px; line-height: 1.55; }.form-governance-panel, .config-policy-panel { margin-top: 14px; }.governance-steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-top: 4px; }.governance-steps article { display: grid; gap: 5px; padding: 10px 11px; border: 1px solid #e5ecf3; border-radius: 4px; background: #fbfcfe; }.governance-steps b { color: #42627f; font-size: 11px; }.governance-steps span { color: #76899d; font-size: 10px; line-height: 1.55; }.policy-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }.policy-summary div { display: grid; gap: 4px; padding: 9px 10px; border-radius: 4px; background: #f7f9fc; }.policy-summary b { color: #668098; font-size: 10px; }.policy-summary span { color: #405b77; font-size: 11px; }.form-editor-modal { width: min(1080px, calc(100vw - 40px)); max-height: calc(100vh - 38px); overflow-y: auto; }.form-editor-form { gap: 15px; }.editor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; }.editor-wide { grid-column: 1 / -1; }.editor-section { padding-top: 12px; border-top: 1px solid #e7edf4; }.editor-section-heading, .editor-field-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.editor-section-heading { margin-bottom: 10px; }.editor-section-heading > div { display: grid; gap: 3px; }.editor-section-heading b, .editor-field-head b { color: #3c5a76; font-size: 12px; }.editor-section-heading span { color: #8190a1; font-size: 10px; }.editor-field-card { margin-top: 9px; padding: 11px; border: 1px solid #e2eaf2; border-radius: 4px; background: #fbfcfe; }.editor-field-head { margin-bottom: 9px; }.editor-grid--field { grid-template-columns: repeat(3, minmax(0, 1fr)); }.text-danger-button, .link-button { border: 0; background: transparent; font-size: 11px; cursor: pointer; }.text-danger-button { color: #b94b4b; }.link-button { margin-top: 6px; padding: 0; color: #176dc1; }.text-danger-button:disabled, .link-button:disabled { color: #9eabb8; cursor: not-allowed; }.condition-editor { margin-top: 10px; padding-top: 8px; border-top: 1px dashed #dfe7ef; }.condition-editor summary { color: #587491; font-size: 11px; cursor: pointer; }.condition-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; margin-top: 9px; }.condition-grid > div { display: grid; gap: 5px; }.condition-grid b { color: #647e98; font-size: 10px; }.condition-row { display: grid; grid-template-columns: minmax(0, 1fr) 92px minmax(0, 1fr) 20px; gap: 4px; }.condition-row input, .condition-row select { min-width: 0; padding: 6px; border: 1px solid #d7e1eb; border-radius: 3px; color: #425d78; font-size: 10px; }.condition-row button { border: 0; color: #b84a4a; background: transparent; cursor: pointer; } @media (max-width: 1020px) { .form-admin-layout { grid-template-columns: 1fr; }.form-admin-side { grid-template-columns: 1fr 1fr; }.governance-steps, .policy-summary { grid-template-columns: 1fr 1fr; }.editor-grid--field { grid-template-columns: 1fr 1fr; } } @media (max-width: 720px) { .form-admin-toolbar, .form-version-summary { align-items: flex-start; flex-direction: column; }.toolbar-actions { justify-content: flex-start; }.field-system-strip { grid-template-columns: 1fr; }.form-admin-side { grid-template-columns: 1fr; }.field-definition, .editor-grid, .editor-grid--field, .condition-grid { grid-template-columns: 1fr; }.governance-steps, .policy-summary { grid-template-columns: 1fr; } }
</style>
