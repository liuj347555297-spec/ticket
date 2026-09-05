import { ApiError, apiRequest } from '@/api/client'

export type KnowledgePublicationStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'SUPERSEDED' | 'REJECTED' | 'ARCHIVED' | 'MIGRATION_PENDING'
export type KnowledgeSourceType = 'MANUAL' | 'RESOLVED_CASE' | 'IMPORTED'
export type KnowledgeAttachmentScanState = 'RECEIVED' | 'SCANNING' | 'SCAN_PASSED' | 'QUARANTINED' | 'REJECTED' | 'SCAN_UNAVAILABLE'

export interface KnowledgeTag { code?: string; name: string; kind?: 'STANDARD' | 'FREE' }
export interface KnowledgeCatalogReference { id: string; name: string }
export interface KnowledgeCaseReference { id: string; title: string }
export interface KnowledgeAttachment {
  id: string
  displayFileName: string
  detectedMediaType: string
  sizeBytes: number
  scanState: KnowledgeAttachmentScanState
  /** The API is authoritative: this does not itself grant download access. */
  downloadable?: boolean
}
export interface KnowledgeArticleSummary {
  id: string
  title: string
  owningOrganizationId: string
  serviceCatalogItemIds: string[]
  summary?: string
  /** Optional presentation metadata until the shared contract adds classification/source. */
  category?: string
  tags: KnowledgeTag[]
  version: number
  publicationStatus?: KnowledgePublicationStatus
  sourceType?: KnowledgeSourceType
  updatedAt?: string
  serviceCatalogItems?: KnowledgeCatalogReference[]
  favorite?: boolean
  creatorIamUserId?: string
  createdAt?: string
  sourceTicketId?: string
  currentVersionId?: string
}
export interface KnowledgeArticle extends KnowledgeArticleSummary {
  /** Undefined means the source is not previewable in-browser (for example a PDF). */
  content?: string
  publishedAt: string
  relatedServiceCatalogItemIds?: string[]
  relatedCases?: KnowledgeCaseReference[]
  attachments: KnowledgeAttachment[]
}
export interface KnowledgeArticlePage { items: KnowledgeArticleSummary[]; page: number; pageSize: number; total: number }
export interface KnowledgeQuery { q?: string; category?: string; tag?: string; page?: number; pageSize?: number }
export interface KnowledgeFeedbackSummary { helpfulCount: number; notHelpfulCount: number; total: number }
export type KnowledgeFeedbackValue = 'HELPFUL' | 'NOT_HELPFUL'
export interface KnowledgeReviewCandidate { documentId: string; title: string; reasonCode: 'REVIEW_DUE' | 'LOW_HELPFULNESS'; reviewDueAt?: string; reviewOwnerIamUserId?: string; helpfulCount: number; notHelpfulCount: number }

export type KnowledgeImportStatus = 'RECEIVED' | 'SCANNING' | 'QUARANTINED' | 'REJECTED' | 'PARSING' | 'DRAFT_CREATED' | 'FAILED'
export interface KnowledgeImportRecord {
  id: string
  title: string
  owningOrganizationId: string
  serviceCatalogItemIds: string[]
  tags: KnowledgeTag[]
  status: KnowledgeImportStatus
  requestedAt: string
  requester: string
  reasonCode?: string
  draftArticleId?: string
  draftVersion?: number
  auditEventId: string
}
export interface KnowledgeImportRequest { title: string; categoryCode: string; serviceCatalogItemIds: string[]; tags: KnowledgeTag[]; file: File }
export interface KnowledgeResult<T> { data: T; source: 'api' | 'demo' }
export interface KnowledgeVersion { id: string; versionNumber: number; detectedMediaType: string; sizeBytes: number; status: KnowledgePublicationStatus; reviewerIamUserId?: string; createdAt: string; publishedAt?: string }
export type KnowledgeWorkbenchSection = 'MY_DRAFTS' | 'MY_SUBMISSIONS' | 'PENDING_REVIEW' | 'REVIEWED'
export interface KnowledgeDraftInput { title: string; categoryCode: string; tags: string[]; serviceCatalogItemIds: string[]; content: string }

function isUnavailable(error: unknown): boolean { return error instanceof TypeError || (error instanceof ApiError && error.status === 503) }
const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
interface KnowledgeDocumentWire { id: string; title: string; categoryCode: string; tags: string[]; owningOrganizationId: string; serviceCatalogItemIds: string[]; status: KnowledgePublicationStatus; currentVersionId: string; currentVersionNumber?: number; creatorIamUserId?: string; createdAt?: string; updatedAt: string; sourceTicketId?: string; favorite?: boolean }

function fromDocument(document: KnowledgeDocumentWire): KnowledgeArticle {
  return {
    id: document.id, title: document.title, owningOrganizationId: document.owningOrganizationId, serviceCatalogItemIds: document.serviceCatalogItemIds, category: document.categoryCode,
    tags: document.tags.map((name) => ({ name, kind: 'STANDARD' as const })), version: document.currentVersionNumber ?? 1,
    publicationStatus: document.status, updatedAt: document.updatedAt, publishedAt: document.updatedAt,
    sourceType: 'IMPORTED', summary: '受控导入知识文档；仅已发布内容可被当前身份读取。',
    attachments: [], serviceCatalogItems: document.serviceCatalogItemIds.map((id) => ({ id, name: id })), relatedServiceCatalogItemIds: document.serviceCatalogItemIds, relatedCases: [],
    favorite: document.favorite, creatorIamUserId: document.creatorIamUserId, createdAt: document.createdAt, sourceTicketId: document.sourceTicketId, currentVersionId: document.currentVersionId,
  }
}

/** Knowledge files are never opened, parsed, previewed or scanned by the browser. */
async function submitImport(request: KnowledgeImportRequest): Promise<KnowledgeDocumentWire> {
  const csrfToken = document.cookie.split('; ').find((item) => item.startsWith('XSRF-TOKEN='))?.split('=')[1]
  const form = new FormData()
  form.set('title', request.title); form.set('categoryCode', request.categoryCode)
  request.serviceCatalogItemIds.forEach((id) => form.append('serviceCatalogItemIds', id))
  request.tags.forEach((tag) => form.append('tags', tag.name))
  form.set('file', request.file)
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? '/api/v1'}/knowledge/documents/imports`, { method: 'POST', credentials: 'same-origin', headers: { Accept: 'application/json', ...(csrfToken ? { 'X-CSRF-TOKEN': decodeURIComponent(csrfToken) } : {}) }, body: form })
  if (!response.ok) { const payload = await response.json().catch(() => undefined) as { message?: string; code?: string } | undefined; throw new ApiError(payload?.message ?? `导入请求失败（${response.status}）`, response.status, payload?.code) }
  return response.json() as Promise<KnowledgeDocumentWire>
}

export const knowledgeApi = {
  async list(query: KnowledgeQuery = {}): Promise<KnowledgeResult<KnowledgeArticlePage>> { const params = new URLSearchParams(); if (query.q) params.set('q', query.q); if (query.category) params.set('category', query.category); if (query.tag) params.set('tag', query.tag); const documents = await apiRequest<KnowledgeDocumentWire[]>(`/knowledge/documents${params.size ? `?${params}` : ''}`); const items = documents.map(fromDocument); return { data: { items, page: 1, pageSize: items.length || 20, total: items.length }, source: 'api' } },
  async get(articleId: string): Promise<KnowledgeResult<KnowledgeArticle>> { const document = await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}`); const article = fromDocument(document); try { const preview = await apiRequest<{ content: string; versionId: string; versionNumber: number }>(`/knowledge/documents/${encodeURIComponent(articleId)}/content`); article.content = preview.content; article.version = preview.versionNumber } catch (previewError) { if (!(previewError instanceof ApiError) || previewError.status !== 400) throw previewError } return { data: article, source: 'api' } },
  async getWorkspace(articleId: string): Promise<KnowledgeArticle> { const document=await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/workspace/${encodeURIComponent(articleId)}`);const article=fromDocument(document);const preview=await apiRequest<{ content:string;versionNumber:number }>(`/knowledge/documents/${encodeURIComponent(articleId)}/content`);article.content=preview.content;article.version=preview.versionNumber;return article },
  async createImport(request: KnowledgeImportRequest): Promise<KnowledgeResult<KnowledgeImportRecord>> { try { const document = await submitImport(request); return { data: { id: document.id, title: document.title, owningOrganizationId: document.owningOrganizationId, serviceCatalogItemIds: document.serviceCatalogItemIds, tags: request.tags, status: document.status === 'PENDING_REVIEW' ? 'DRAFT_CREATED' : 'REJECTED', requestedAt: document.updatedAt, requester: '当前操作人', draftArticleId: document.id, draftVersion: 1, auditEventId: document.id }, source: 'api' } } catch (error) { if (canUseDemoFallback && isUnavailable(error)) throw new ApiError('开发演示不接收或扫描本地文件；请连接服务端后提交导入。', 503, 'DEMO_UPLOAD_BLOCKED'); throw error } },
  async feedback(articleId: string, value: KnowledgeFeedbackValue, reasonCode?: string): Promise<KnowledgeFeedbackSummary> {
    return apiRequest<KnowledgeFeedbackSummary>(`/knowledge/documents/${encodeURIComponent(articleId)}/feedback`, {
      method: 'POST', body: { value, ...(reasonCode ? { reasonCode } : {}) },
    })
  },
  async feedbackSummary(articleId: string): Promise<KnowledgeFeedbackSummary> {
    return apiRequest<KnowledgeFeedbackSummary>(`/knowledge/documents/${encodeURIComponent(articleId)}/feedback`)
  },
  async reviewCandidates(): Promise<KnowledgeReviewCandidate[]> {
    return apiRequest<KnowledgeReviewCandidate[]>('/knowledge/documents/reviews/candidates')
  },
  async completeReview(articleId: string): Promise<KnowledgeDocumentWire> {
    return apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}/reviews/complete`, { method: 'POST' })
  },
  async favorites(): Promise<KnowledgeArticleSummary[]> { return (await apiRequest<KnowledgeDocumentWire[]>('/knowledge/documents/favorites')).map(fromDocument) },
  async setFavorite(articleId: string, value: boolean): Promise<boolean> { const result = await apiRequest<{ favorite: boolean }>(`/knowledge/documents/${encodeURIComponent(articleId)}/favorite`, { method: value ? 'PUT' : 'DELETE' }); return result.favorite },
  async workbench(section: KnowledgeWorkbenchSection): Promise<KnowledgeArticleSummary[]> { return (await apiRequest<KnowledgeDocumentWire[]>(`/knowledge/documents/workbench?section=${encodeURIComponent(section)}`)).map(fromDocument) },
  async versions(articleId: string): Promise<KnowledgeVersion[]> { return apiRequest<KnowledgeVersion[]>(`/knowledge/documents/${encodeURIComponent(articleId)}/versions`) },
  async createDraft(input: KnowledgeDraftInput): Promise<KnowledgeArticleSummary> { return fromDocument(await apiRequest<KnowledgeDocumentWire>('/knowledge/documents/drafts', { method: 'POST', body: input })) },
  async updateDraft(articleId: string, version: number, input: KnowledgeDraftInput): Promise<KnowledgeArticleSummary> { return fromDocument(await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}/draft`, { method: 'PUT', headers: { 'If-Match': `"${version}"` }, body: input })) },
  async submitDraft(articleId: string): Promise<KnowledgeArticleSummary> { return fromDocument(await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}/submit`, { method: 'POST' })) },
  async deleteDraft(articleId: string): Promise<void> { await apiRequest<void>(`/knowledge/documents/${encodeURIComponent(articleId)}/draft`, { method: 'DELETE' }) },
  async publish(articleId: string, versionId: string): Promise<KnowledgeArticleSummary> { return fromDocument(await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}/publish?versionId=${encodeURIComponent(versionId)}`, { method: 'POST' })) },
}
