import { apiRequest } from '@/api/client'

export type ControlId = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'multiselect' | 'boolean' | 'richtext' | 'tags' | 'ci' | 'attachment' | 'iam' | 'user' | 'section'
export interface StudioField {
  id: string; code: string; label: string; control: ControlId; controlVersion: number
  required: boolean; sensitive: boolean; helpText: string
  options: Array<{ value: string; label: string }>
  dictionaryCode?: string
}
export interface StudioFormRevision {
  formId: string; code: string; name: string; revision: number; status: 'DRAFT' | 'FROZEN'; fields: StudioField[]
}
export interface NodeFormBinding {
  nodeId: string; formId: string; formRevision: number; displayOrder: number
  mode: 'EDIT' | 'READ_ONLY'; requiredOnComplete: boolean
}
export interface StudioDraftInput {
  version: number; name: string; organizationId: string; bpmnXml: string
  forms: StudioFormRevision[]; nodeBindings: NodeFormBinding[]; reason: string
  systemCode?: string | null; serviceCatalogItemId?: string | null
}
export interface StudioDraft extends StudioDraftInput {
  id: string; executionMode: 'DRAFT_ONLY'; updatedAt: string
}
export interface StudioDraftSummary { id: string; name: string; organizationId: string; version: number; executionMode: 'DRAFT_ONLY'; updatedAt: string; systemCode?: string | null; serviceCatalogItemId?: string | null }
export interface WorkflowDiagram {
  processKey: string; processDefinitionId: string | null; version: number | null
  bpmnXml: string | null; layoutSource: 'AUTHORED' | 'GENERATED' | 'NONE'
  activeNodeIds: string[]; completedNodeIds: string[]
  availability: 'AVAILABLE' | 'UNAVAILABLE_LEGACY'
}
export const designerApi = {
  list: () => apiRequest<{ items: StudioDraftSummary[] }>('/admin/design-studio/drafts'),
  get: (id: string) => apiRequest<StudioDraft>(`/admin/design-studio/drafts/${encodeURIComponent(id)}`),
  create: (input: StudioDraftInput, key = crypto.randomUUID()) => apiRequest<StudioDraft>('/admin/design-studio/drafts', { method: 'POST', body: input, headers: { 'Idempotency-Key': key } }),
  save: (id: string, input: StudioDraftInput) => apiRequest<StudioDraft>(`/admin/design-studio/drafts/${encodeURIComponent(id)}`, { method: 'PUT', body: input, headers: { 'If-Match': `"${input.version}"` } }),
  lifecycleDiagram: () => apiRequest<WorkflowDiagram>('/workflow/ticket-lifecycle/diagram'),
  ticketDiagram: (id: string) => apiRequest<WorkflowDiagram>(`/tickets/${encodeURIComponent(id)}/workflow/diagram`),
}
