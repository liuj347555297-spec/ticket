import { ApiError, apiRequest } from '@/api/client'
import type { TicketTag, TicketType } from '@/api/tickets'

export type FormFieldType = 'TEXT' | 'TEXTAREA' | 'LONG_TEXT' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'RADIO' | 'CHECKBOX_GROUP' | 'ERROR_CODE' | 'TAGS' | 'CI_REFERENCE' | 'RICH_TEXT'
export type FieldSensitivity = 'INTERNAL' | 'SENSITIVE'
export type FieldMasking = 'NONE' | 'PARTIAL' | 'FULL'

export interface StandardTag { code: string; name: string; lifecycleStatus: 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'RETIRED' | 'REJECTED' }
export interface ServiceCatalogItem { id: string; code: string; name: string; summary?: string; ticketType: TicketType; categoryCode: string; publishedVersion: number; formSchemaHash: string; tags?: StandardTag[] }
export interface ServiceCatalogItemPage { items: ServiceCatalogItem[]; page: number; pageSize: number; total: number }
export type FormConditionOperator = 'EQUALS' | 'NOT_EQUALS' | 'IN' | 'NOT_IN' | 'HAS_VALUE' | 'NO_VALUE'
export interface FormCondition { fieldCode: string; operator: FormConditionOperator; values: string[] }
export interface FormField { code: string; label: string; helpText?: string; defaultValue?: string; type: FormFieldType; required: boolean; displayOrder: number; dictionaryCode?: string; visibleWhen?: FormCondition[]; requiredWhen?: FormCondition[]; validation?: { minLength?: number; maxLength?: number; minimum?: number; maximum?: number; patternCode?: string }; sensitivity: FieldSensitivity; masking: FieldMasking; allowRuleMatching?: boolean }
export interface TagPolicy { allowStandardTags: boolean; allowFreeTags: boolean; maxTags: number; allowedStandardTagCodes?: string[] }
export interface PublishedServiceCatalogForm { serviceCatalogItem: ServiceCatalogItem; formVersion: number; formSchemaHash: string; fields: FormField[]; tagPolicy: TagPolicy; publishedAt?: string }
export interface DictionaryEntry { code: string; label: string; parentCode?: string; displayOrder?: number }
export interface DictionaryEntryPage { items: DictionaryEntry[]; formVersion: number }
export interface RuleMatchRequest { serviceCatalogItemId: string; formVersion: number; title?: string; description?: string; structuredFields: Record<string, string | boolean | string[]>; tags?: TicketTag[]; relatedConfigurationItemIds?: string[] }
export interface RuleMatch { ruleCode: string; matchedFacts: string[]; suggestion: { kind: 'KNOWLEDGE_ARTICLE' | 'RESOLVED_CASE'; referenceId?: string; title: string; summary: string; action: 'READ_AND_TRY' | 'CONTINUE_CREATE' } }
export interface RuleMatchResponse { ruleEngine: 'DETERMINISTIC_RULES'; matches: RuleMatch[] }
export interface CatalogResult<T> { data: T; source: 'api' | 'demo' }

/** Back-office contract. These types intentionally mirror ServiceCatalogAdminController, not the requester form contract. */
export type FormConfigurationStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'RETIRED' | 'REJECTED'
export type ConfigurableFormFieldType = 'TEXT' | 'LONG_TEXT' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'DATETIME' | 'BOOLEAN' | 'TAGS' | 'CI_REFERENCE' | 'RICH_TEXT'
export interface ManagedFormField { code: string; label: string; type: ConfigurableFormFieldType; required: boolean; defaultValue?: string; helpText?: string; maxLength?: number; dictionaryCode?: string; displayOrder: number; visibleWhen: FormCondition[]; requiredWhen: FormCondition[] }
export interface ManagedFormConfiguration { id: string; code: string; name: string; summary?: string; ticketType: TicketType; categoryCode: string; applicableOrganizationIds: string[]; fields: ManagedFormField[]; tagPolicy: TagPolicy; lifecycleStatus: FormConfigurationStatus; version: number; formVersion: number; schemaHash: string; changeReason?: string }
export interface ManagedFormConfigurationPage { items: ManagedFormConfiguration[]; page: number; pageSize: number; total: number }
export interface ManagedFormDraftInput { version: number; code: string; name: string; summary?: string; ticketType: TicketType; categoryCode: string; applicableOrganizationIds: string[]; fields: ManagedFormField[]; tagPolicy: TagPolicy; reason: string }
export interface FormPublicationResponse { requestId: string; status: FormConfigurationStatus; requestedVersion: number }
export type NodeAssignmentMode = 'SYSTEM_RANDOM' | 'PREVIOUS_HANDLER_SELECTS' | 'SHARED_QUEUE'
export interface WorkflowNodeAssignmentPolicy { serviceCatalogItemId: string; nodeKey: 'accept' | 'processing' | 'user_feedback' | 'closure'; mode: NodeAssignmentMode; candidateRoles: string[]; version: number; enabled: boolean }

const hash = '7a616396c2c4bb34e0174fa5b18ba30435fd30cd2c6ef1872da44276709f2b9a'
const demoItems: ServiceCatalogItem[] = [
  { id: 'SC-ERP-PERFORMANCE', code: 'INC_ERP_PERF', name: '业务系统 - 页面性能问题', summary: '页面加载慢、查询超时或操作卡顿。', ticketType: 'INCIDENT', categoryCode: 'BUSINESS_SYSTEM', publishedVersion: 3, formSchemaHash: hash, tags: [{ code: 'TAG-PAGE-SLOW', name: '#页面卡顿', lifecycleStatus: 'PUBLISHED' }, { code: 'TAG-ERP', name: '#ERP', lifecycleStatus: 'PUBLISHED' }, { code: 'TAG-NUCLEAR-E', name: '#核协E+', lifecycleStatus: 'PUBLISHED' }, { code: 'TAG-QUERY-TIMEOUT', name: '#查询超时', lifecycleStatus: 'PUBLISHED' }] },
  { id: 'SC-NETWORK-FAULT', code: 'INC_NET_CONN', name: '网络服务 - 连通性故障', summary: '无法访问、网络中断、VPN 异常。', ticketType: 'INCIDENT', categoryCode: 'NETWORK', publishedVersion: 2, formSchemaHash: hash, tags: [{ code: 'TAG-NETWORK-FAULT', name: '#网络故障', lifecycleStatus: 'PUBLISHED' }, { code: 'TAG-VPN', name: '#VPN', lifecycleStatus: 'PUBLISHED' }] },
  { id: 'SC-FIN-ACCESS', code: 'REQ_ACCESS_ROLE', name: '账号与权限 - 角色申请', summary: '按目录触发审批及最小权限校验。', ticketType: 'ACCESS_REQUEST', categoryCode: 'ACCESS', publishedVersion: 5, formSchemaHash: hash, tags: [{ code: 'TAG-ACCESS', name: '#账号权限', lifecycleStatus: 'PUBLISHED' }, { code: 'TAG-ROLE', name: '#角色申请', lifecycleStatus: 'PUBLISHED' }] },
]
const demoForms: Record<string, PublishedServiceCatalogForm> = {
  'SC-ERP-PERFORMANCE': { serviceCatalogItem: demoItems[0], formVersion: 3, formSchemaHash: hash, tagPolicy: { allowStandardTags: true, allowFreeTags: true, maxTags: 20 }, fields: [
    { code: 'affected_system', label: '影响系统', type: 'SINGLE_SELECT', required: true, displayOrder: 1, dictionaryCode: 'AFFECTED_SYSTEM', sensitivity: 'INTERNAL', masking: 'NONE', allowRuleMatching: true },
    { code: 'affected_page', label: '受影响页面 / 模块', type: 'TEXT', required: false, displayOrder: 2, validation: { maxLength: 200 }, sensitivity: 'INTERNAL', masking: 'NONE', allowRuleMatching: true },
    { code: 'occurrence', label: '发生情况', type: 'SINGLE_SELECT', required: true, displayOrder: 3, dictionaryCode: 'OCCURRENCE', sensitivity: 'INTERNAL', masking: 'NONE', allowRuleMatching: true },
    { code: 'impact_scope', label: '影响范围', type: 'SINGLE_SELECT', required: true, displayOrder: 4, dictionaryCode: 'IMPACT_SCOPE', sensitivity: 'INTERNAL', masking: 'NONE', allowRuleMatching: true },
    { code: 'error_code', label: '错误码', type: 'ERROR_CODE', required: false, displayOrder: 5, validation: { maxLength: 100 }, sensitivity: 'INTERNAL', masking: 'NONE', allowRuleMatching: true },
  ] },
  'SC-NETWORK-FAULT': { serviceCatalogItem: demoItems[1], formVersion: 2, formSchemaHash: hash, tagPolicy: { allowStandardTags: true, allowFreeTags: true, maxTags: 20 }, fields: [
    { code: 'network_area', label: '网络区域', type: 'SINGLE_SELECT', required: true, displayOrder: 1, dictionaryCode: 'NETWORK_AREA', sensitivity: 'INTERNAL', masking: 'NONE' },
    { code: 'target_address', label: '目标系统 / 地址', type: 'TEXT', required: false, displayOrder: 2, validation: { maxLength: 200 }, sensitivity: 'INTERNAL', masking: 'NONE' },
    { code: 'failure_mode', label: '故障表现', type: 'MULTI_SELECT', required: true, displayOrder: 3, dictionaryCode: 'FAILURE_MODE', sensitivity: 'INTERNAL', masking: 'NONE' },
  ] },
  'SC-FIN-ACCESS': { serviceCatalogItem: demoItems[2], formVersion: 5, formSchemaHash: hash, tagPolicy: { allowStandardTags: true, allowFreeTags: false, maxTags: 10 }, fields: [
    { code: 'target_system', label: '申请系统', type: 'SINGLE_SELECT', required: true, displayOrder: 1, dictionaryCode: 'AFFECTED_SYSTEM', sensitivity: 'INTERNAL', masking: 'NONE' },
    { code: 'requested_role', label: '申请角色', type: 'TEXT', required: true, displayOrder: 2, validation: { maxLength: 100 }, sensitivity: 'INTERNAL', masking: 'NONE' },
    { code: 'valid_until', label: '权限截止时间', type: 'DATETIME', required: false, displayOrder: 3, sensitivity: 'INTERNAL', masking: 'NONE' },
  ] },
}
const demoDictionaries: Record<string, DictionaryEntry[]> = {
  AFFECTED_SYSTEM: [{ code: 'ERP', label: 'ERP' }, { code: 'FINANCE', label: '财务共享' }, { code: 'NUCLEAR_E', label: '核协E+' }, { code: 'OTHER', label: '其他' }],
  OCCURRENCE: [{ code: 'PERSISTENT', label: '持续发生' }, { code: 'INTERMITTENT', label: '间歇发生' }, { code: 'FIRST', label: '首次发生' }],
  IMPACT_SCOPE: [{ code: 'SELF', label: '仅本人' }, { code: 'TEAM', label: '本部门 / 小范围' }, { code: 'MULTI_ORG', label: '多部门' }],
  NETWORK_AREA: [{ code: 'OFFICE', label: '办公网' }, { code: 'VPN', label: 'VPN' }, { code: 'WIRELESS', label: '无线网络' }],
  FAILURE_MODE: [{ code: 'UNREACHABLE', label: '无法访问' }, { code: 'SLOW', label: '访问缓慢' }, { code: 'DISCONNECT', label: '频繁断连' }],
}
function isUnavailable(error: unknown): boolean { return error instanceof TypeError || (error instanceof ApiError && error.status === 503) }
function demoMatch(input: RuleMatchRequest): RuleMatchResponse {
  const text = `${input.title ?? ''} ${input.description ?? ''} ${input.tags?.map((tag) => tag.name).join(' ') ?? ''} ${Object.values(input.structuredFields).flat().join(' ')}`.toLowerCase()
  const matches: RuleMatch[] = []
  if (input.serviceCatalogItemId === 'SC-ERP-PERFORMANCE' && (text.includes('超时') || text.includes('error'))) matches.push({ ruleCode: 'MATCH-ERP-TIMEOUT', matchedFacts: ['已匹配：目录、错误码或查询超时关键词'], suggestion: { kind: 'KNOWLEDGE_ARTICLE', referenceId: 'KB-ERP-TIMEOUT', title: 'ERP 查询超时排查指引', summary: '检查发生范围、查询条件与近期变更后，可继续提交工单。', action: 'READ_AND_TRY' } })
  if (input.serviceCatalogItemId === 'SC-NETWORK-FAULT' && (text.includes('vpn') || text.includes('#vpn'))) matches.push({ ruleCode: 'MATCH-NET-VPN', matchedFacts: ['已匹配：网络目录与 VPN 相关描述'], suggestion: { kind: 'KNOWLEDGE_ARTICLE', referenceId: 'KB-NET-VPN', title: 'VPN 连通性自查清单', summary: '请按指引自查；未恢复时可继续提交。', action: 'READ_AND_TRY' } })
  return { ruleEngine: 'DETERMINISTIC_RULES', matches }
}
const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
function fallback<T>(error: unknown, data: T): CatalogResult<T> { if (!canUseDemoFallback || !isUnavailable(error)) throw error; return { data, source: 'demo' } }

/** These calls follow the public, user-visible catalog contract. Server-side scope checks stay authoritative. */
export const catalogApi = {
  async listPublishedItems(): Promise<CatalogResult<ServiceCatalogItemPage>> { try { return { data: await apiRequest<ServiceCatalogItemPage>('/service-catalog/items?page=1&pageSize=100'), source: 'api' } } catch (error) { return fallback(error, { items: demoItems, page: 1, pageSize: 100, total: demoItems.length }) } },
  async getPublishedForm(itemId: string): Promise<CatalogResult<PublishedServiceCatalogForm>> { try { return { data: await apiRequest<PublishedServiceCatalogForm>(`/service-catalog/items/${encodeURIComponent(itemId)}/form`), source: 'api' } } catch (error) { const form = demoForms[itemId]; if (!form) throw error; return fallback(error, form) } },
  async listDictionaryEntries(dictionaryCode: string, serviceCatalogItemId: string, formVersion: number, fieldCode: string): Promise<CatalogResult<DictionaryEntryPage>> { const params = new URLSearchParams({ serviceCatalogItemId, formVersion: String(formVersion), fieldCode }); try { return { data: await apiRequest<DictionaryEntryPage>(`/service-catalog/dictionaries/${encodeURIComponent(dictionaryCode)}/entries?${params.toString()}`), source: 'api' } } catch (error) { return fallback(error, { items: demoDictionaries[dictionaryCode] ?? [], formVersion: demoForms[serviceCatalogItemId]?.formVersion ?? 1 }) } },
  async matchRules(request: RuleMatchRequest): Promise<CatalogResult<RuleMatchResponse>> { try { return { data: await apiRequest<RuleMatchResponse>('/service-catalog/rule-matches', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: request }), source: 'api' } } catch (error) { return fallback(error, demoMatch(request)) } },
}

/** Administrative mutations use a new idempotency key and carry both the body version and If-Match version. */
const administrationHeaders = (version?: number): Record<string, string> => ({
  'Idempotency-Key': crypto.randomUUID(),
  ...(version === undefined ? {} : { 'If-Match': `"${version}"` }),
})

export const serviceCatalogAdminApi = {
  list(status?: FormConfigurationStatus): Promise<ManagedFormConfigurationPage> {
    const params = new URLSearchParams({ page: '1', pageSize: '100' })
    if (status) params.set('status', status)
    return apiRequest<ManagedFormConfigurationPage>(`/admin/service-catalog/items?${params.toString()}`)
  },
  get(id: string): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>(`/admin/service-catalog/items/${encodeURIComponent(id)}`) },
  create(input: ManagedFormDraftInput): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>('/admin/service-catalog/items', { method: 'POST', headers: administrationHeaders(), body: input }) },
  update(id: string, input: ManagedFormDraftInput): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>(`/admin/service-catalog/items/${encodeURIComponent(id)}`, { method: 'PUT', headers: administrationHeaders(input.version), body: input }) },
  requestPublish(id: string, version: number, reason: string): Promise<FormPublicationResponse> { return apiRequest<FormPublicationResponse>(`/admin/service-catalog/items/${encodeURIComponent(id)}/publish-requests`, { method: 'POST', headers: administrationHeaders(version), body: { version, reason } }) },
  approve(id: string, requestId: string, version: number): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>(`/admin/service-catalog/items/${encodeURIComponent(id)}/publish-requests/${encodeURIComponent(requestId)}/approve`, { method: 'POST', headers: administrationHeaders(version) }) },
  retire(id: string, version: number, reason: string): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>(`/admin/service-catalog/items/${encodeURIComponent(id)}/retire`, { method: 'POST', headers: administrationHeaders(version), body: { version, reason } }) },
  rollback(id: string, version: number, sourceFormVersion: number, reason: string): Promise<ManagedFormConfiguration> { return apiRequest<ManagedFormConfiguration>(`/admin/service-catalog/items/${encodeURIComponent(id)}/rollback`, { method: 'POST', headers: administrationHeaders(version), body: { sourceFormVersion, reason } }) },
  listRoutingPolicies(id: string): Promise<WorkflowNodeAssignmentPolicy[]> { return apiRequest<WorkflowNodeAssignmentPolicy[]>(`/admin/service-catalog/items/${encodeURIComponent(id)}/workflow-node-policies`) },
  saveRoutingPolicy(id: string, policy: WorkflowNodeAssignmentPolicy): Promise<WorkflowNodeAssignmentPolicy> { return apiRequest<WorkflowNodeAssignmentPolicy>(`/admin/service-catalog/items/${encodeURIComponent(id)}/workflow-node-policies/${policy.nodeKey}`, { method: 'PUT', headers: administrationHeaders(policy.version), body: { mode: policy.mode, candidateRoles: policy.candidateRoles, enabled: policy.enabled } }) },
}
