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
interface KnowledgeDocumentWire { id: string; title: string; categoryCode: string; tags: string[]; owningOrganizationId: string; serviceCatalogItemIds: string[]; status: KnowledgePublicationStatus; currentVersionId: string; updatedAt: string }

const demoArticles: KnowledgeArticle[] = [
  { id: 'KB-ERP-TIMEOUT', title: 'ERP 查询超时排查指引', owningOrganizationId: 'ORG-LOCAL-IT', serviceCatalogItemIds: ['SC-ERP-PERFORMANCE'], summary: '先确认影响范围、查询条件和近期变更，再按受控步骤核查。', category: '业务系统 / 性能', tags: [{ code: 'TAG-ERP', name: '#ERP' }, { code: 'TAG-QUERY-TIMEOUT', name: '#查询超时' }], version: 4, publicationStatus: 'PUBLISHED', sourceType: 'MANUAL', updatedAt: '2026-08-20T10:22:00+08:00', publishedAt: '2026-08-20T10:22:00+08:00', serviceCatalogItems: [{ id: 'SC-ERP-PERFORMANCE', name: '业务系统 - 页面性能问题' }], relatedCases: [{ id: 'CASE-2026-018', title: '采购订单列表查询慢' }], relatedServiceCatalogItemIds: ['SC-ERP-PERFORMANCE'], content: '适用范围：已授权的 ERP 页面性能问题。\n\n1. 通过监控确认影响范围；2. 记录错误码与发生时间；3. 核对近期发布和查询条件；4. 未恢复时按服务目录继续建单。', attachments: [{ id: 'KBA-ERP-001', displayFileName: 'ERP性能排查清单.pdf', detectedMediaType: 'application/pdf', sizeBytes: 184320, scanState: 'SCAN_PASSED', downloadable: true }] },
  { id: 'KB-NET-VPN', title: 'VPN 连通性自查清单', owningOrganizationId: 'ORG-LOCAL-IT', serviceCatalogItemIds: ['SC-NETWORK-FAULT'], summary: '检查网络连接、客户端状态和受控配置，未恢复时提交网络故障工单。', category: '网络服务', tags: [{ code: 'TAG-VPN', name: '#VPN' }, { code: 'TAG-NETWORK-FAULT', name: '#网络故障' }], version: 2, publicationStatus: 'PUBLISHED', sourceType: 'RESOLVED_CASE', updatedAt: '2026-08-19T15:10:00+08:00', publishedAt: '2026-08-19T15:10:00+08:00', serviceCatalogItems: [{ id: 'SC-NETWORK-FAULT', name: '网络服务 - 连通性故障' }], relatedCases: [{ id: 'CASE-2026-011', title: '分支机构 VPN 间歇断连' }], relatedServiceCatalogItemIds: ['SC-NETWORK-FAULT'], content: '请先确认网络已连接、VPN 客户端为最新受控版本，并记录提示信息。不要在知识库中粘贴密码、令牌或完整访问地址。', attachments: [] },
]

function isUnavailable(error: unknown): boolean { return error instanceof TypeError || (error instanceof ApiError && error.status === 503) }
const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
function fallback<T>(error: unknown, data: T): KnowledgeResult<T> { if (!canUseDemoFallback || !isUnavailable(error)) throw error; return { data, source: 'demo' } }
function fromDocument(document: KnowledgeDocumentWire): KnowledgeArticle {
  return {
    id: document.id, title: document.title, owningOrganizationId: document.owningOrganizationId, serviceCatalogItemIds: document.serviceCatalogItemIds, category: document.categoryCode,
    tags: document.tags.map((name) => ({ name, kind: 'STANDARD' as const })), version: 1,
    publicationStatus: document.status, updatedAt: document.updatedAt, publishedAt: document.updatedAt,
    sourceType: 'IMPORTED', summary: '受控导入知识文档；仅已发布内容可被当前身份读取。',
    attachments: [], serviceCatalogItems: document.serviceCatalogItemIds.map((id) => ({ id, name: id })), relatedServiceCatalogItemIds: document.serviceCatalogItemIds, relatedCases: [],
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
  async list(query: KnowledgeQuery = {}): Promise<KnowledgeResult<KnowledgeArticlePage>> { try { const documents = await apiRequest<KnowledgeDocumentWire[]>('/knowledge/documents'); const text = `${query.q ?? ''} ${query.tag ?? ''} ${query.category ?? ''}`.trim().toLocaleLowerCase(); const items = documents.map(fromDocument).filter((item) => !text || `${item.title} ${item.summary} ${item.category} ${item.tags.map((tag) => tag.name).join(' ')}`.toLocaleLowerCase().includes(text)); return { data: { items, page: 1, pageSize: 20, total: items.length }, source: 'api' } } catch (error) { const text = `${query.q ?? ''} ${query.tag ?? ''} ${query.category ?? ''}`.trim().toLocaleLowerCase(); const items = demoArticles.filter((item) => !text || `${item.title} ${item.summary} ${item.category} ${item.tags.map((tag) => tag.name).join(' ')}`.toLocaleLowerCase().includes(text)); return fallback(error, { items, page: 1, pageSize: 20, total: items.length }) } },
  async get(articleId: string): Promise<KnowledgeResult<KnowledgeArticle>> { try { const document = await apiRequest<KnowledgeDocumentWire>(`/knowledge/documents/${encodeURIComponent(articleId)}`); const article = fromDocument(document); try { const preview = await apiRequest<{ content: string; versionId: string; versionNumber: number }>(`/knowledge/documents/${encodeURIComponent(articleId)}/content`); article.content = preview.content; article.version = preview.versionNumber } catch (previewError) { if (!(previewError instanceof ApiError) || previewError.status !== 400) throw previewError } return { data: article, source: 'api' } } catch (error) { const article = demoArticles.find((item) => item.id === articleId); if (!article) throw error; return fallback(error, article) } },
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
}
