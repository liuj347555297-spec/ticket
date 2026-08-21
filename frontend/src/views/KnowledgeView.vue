<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { knowledgeApi, type KnowledgeArticleSummary, type KnowledgeImportRecord, type KnowledgePublicationStatus } from '@/api/knowledge'

const articles = ref<KnowledgeArticleSummary[]>([])
const imports = ref<KnowledgeImportRecord[]>([])
const mode = ref<'read' | 'admin'>('read')
const source = ref<'api' | 'demo'>('api')
const query = ref('')
const loading = ref(false)
const error = ref('')
const showImport = ref(false)
const importForm = ref({ title: '', targetOrganizationIamId: '', catalogIds: '', tagNames: '', file: null as File | null })
const importError = ref('')
const importNotice = ref('')
const submitting = ref(false)

const statusLabel: Record<KnowledgePublicationStatus, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', SUPERSEDED: '已替代', REJECTED: '已驳回', ARCHIVED: '已归档' }
const blockedImports = computed(() => imports.value.filter((item) => item.status !== 'DRAFT_CREATED').length)

async function loadArticles(): Promise<void> { loading.value = true; error.value = ''; try { const result = await knowledgeApi.list({ q: query.value.trim() || undefined }); articles.value = result.data.items; source.value = result.source } catch (cause) { error.value = cause instanceof ApiError ? cause.message : '知识库暂不可用，请稍后重试。' } finally { loading.value = false } }
function openAdmin(): void { mode.value = 'admin'; error.value = '' }
function chooseFile(event: Event): void { const input = event.target as HTMLInputElement; importForm.value.file = input.files?.[0] ?? null }
async function submitImport(): Promise<void> { importError.value = ''; importNotice.value = ''; const value = importForm.value; if (!value.title.trim() || !value.targetOrganizationIamId.trim() || !value.file) { importError.value = '请填写标题、目标组织 IAM ID 并选择一个待上传文件。'; return }
  submitting.value = true
  try { const tags = value.tagNames.split(',').map((item) => item.trim()).filter(Boolean).map((name) => ({ code: name.slice(1).toUpperCase().replace(/[^A-Z0-9_]/g, '_').slice(0, 63) || 'TAG', name: name.startsWith('#') ? name : `#${name}`, kind: 'STANDARD' as const })); const result = await knowledgeApi.createImport({ title: value.title.trim(), targetOrganizationIamId: value.targetOrganizationIamId.trim(), serviceCatalogItemIds: value.catalogIds.split(',').map((item) => item.trim()).filter(Boolean), tags, file: value.file }); imports.value = [result.data, ...imports.value]; showImport.value = false; importNotice.value = '服务端已受理导入；文件会先隔离并完成扫描，扫描通过前不能下载、解析或发布。' } catch (cause) { importError.value = cause instanceof ApiError ? cause.message : '导入请求未完成。' } finally { submitting.value = false }
}
onMounted(loadArticles)
</script>

<template>
  <div class="page-heading"><div><h2>知识库</h2><p>只展示已按当前 IAM 数据范围授权的内容；案例匹配与知识阅读不改变工单状态。</p></div><div class="knowledge-tabs"><button :class="{ active: mode === 'read' }" type="button" @click="mode = 'read'; loadArticles()">检索与阅读</button><button :class="{ active: mode === 'admin' }" type="button" @click="openAdmin">导入与审核</button></div></div>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅用于界面预览；不表示知识可见范围、审核状态或文件扫描结果。</p><p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <template v-if="mode === 'read'"><section class="panel knowledge-search"><form @submit.prevent="loadArticles"><label class="field"><span>关键词 / 标签 / 分类</span><input v-model.trim="query" maxlength="100" placeholder="例如 #核协E+、查询超时、VPN" /></label><button class="button button--primary" type="submit" :disabled="loading">{{ loading ? '检索中…' : '检索' }}</button></form><p>支持标题、摘要、分类和标签检索；不执行 AI 问答或自动处置。</p></section>
    <section class="knowledge-card-list"><article v-for="article in articles" :key="article.id" class="panel knowledge-card"><div><div class="knowledge-card__meta"><span>{{ article.category ?? '知识文档' }}</span><span>v{{ article.version }}</span><span :class="`knowledge-status knowledge-status--${article.publicationStatus?.toLowerCase() ?? 'published'}`">{{ article.publicationStatus ? statusLabel[article.publicationStatus] : '已发布' }}</span><span v-if="article.sourceType">{{ article.sourceType === 'RESOLVED_CASE' ? '已解决案例沉淀' : article.sourceType === 'IMPORTED' ? '受控导入' : '人工维护' }}</span></div><h3><RouterLink :to="`/knowledge/${article.id}`">{{ article.title }}</RouterLink></h3><p>{{ article.summary ?? '服务端未返回摘要。' }}</p><div class="tag-row"><span v-for="tag in article.tags" :key="tag.code ?? tag.name" class="tag tag--muted">{{ tag.name }}</span><span v-for="catalog in article.serviceCatalogItems ?? []" :key="catalog.id" class="tag tag--blue">{{ catalog.name }}</span></div></div><RouterLink class="button button--secondary button--compact" :to="`/knowledge/${article.id}`">阅读</RouterLink></article><p v-if="!loading && !articles.length" class="panel empty-state">未找到当前权限范围内的已发布知识。</p></section>
  </template>
  <template v-else><section class="panel knowledge-admin-summary"><div><b>导入与审核</b><span>上传后由服务端完成类型校验、隔离与恶意文件扫描；本页不解析、预览或扫描文件。</span></div><button class="button button--primary" type="button" @click="showImport = true">导入知识文档</button></section><p v-if="importNotice" class="form-alert form-alert--success">{{ importNotice }}</p>
    <section class="panel"><div class="panel-header"><div><h3>本次会话提交的导入任务</h3><p>任务状态来自服务端受理结果；详细审核队列后端接口开放后再接入。</p></div><span class="readonly-badge">{{ blockedImports }} 项未完成</span></div><div class="table-scroll"><table><thead><tr><th>标题 / 任务</th><th>目标组织 / 关联目录</th><th>标签</th><th>导入状态</th><th>失败原因</th></tr></thead><tbody><tr v-for="record in imports" :key="record.id"><td><b>{{ record.title }}</b><span class="table-subtext">{{ record.id }} · {{ record.requestedAt.slice(0, 16).replace('T', ' ') }}</span></td><td><span>{{ record.targetOrganizationIamId }}</span><span class="table-subtext">{{ record.serviceCatalogItemIds.join('、') || '未关联' }}</span></td><td>{{ record.tags.map((tag) => tag.name).join(' ') || '无标签' }}</td><td><span class="knowledge-status knowledge-status--pending_review">{{ record.status }}</span></td><td>{{ record.reasonCode ?? '—' }}</td></tr><tr v-if="!imports.length"><td colspan="5" class="table-empty">当前会话还没有导入任务。</td></tr></tbody></table></div><p class="scan-notice">未通过或未完成扫描的文件已隔离，禁止下载、解析、发布、索引和用于案例匹配。</p></section>
    <div v-if="showImport" class="modal-backdrop" @mousedown.self="showImport = false"><section class="action-modal" role="dialog" aria-modal="true" aria-label="导入知识文档"><div class="modal-heading"><div><span class="eyebrow">知识库管理</span><h3>导入知识文档</h3><p>文件直传服务端隔离区；浏览器不读取或解析文件内容。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="showImport = false">×</button></div><form class="action-form" @submit.prevent="submitImport"><label class="field"><span>标题 <b>*</b></span><input v-model.trim="importForm.title" maxlength="200" /></label><label class="field"><span>目标组织 IAM ID <b>*</b></span><input v-model.trim="importForm.targetOrganizationIamId" maxlength="128" placeholder="例如 ORG-HQ-OPS" /><small>服务端会按当前管理员的管理范围再次校验。</small></label><label class="field"><span>关联服务目录 ID</span><input v-model.trim="importForm.catalogIds" maxlength="500" placeholder="逗号分隔，例如 SC-ERP-PERFORMANCE" /></label><label class="field"><span>展示标签</span><input v-model.trim="importForm.tagNames" maxlength="500" placeholder="逗号分隔，例如 #ERP,#查询超时" /><small>标签编码由前端作展示转换，服务端仍校验格式和可见范围。</small></label><label class="field"><span>待上传文件 <b>*</b></span><input type="file" @change="chooseFile" /><small>选择不表示已上传、已扫描或可发布；不支持文件预览。</small></label><p v-if="importError" class="form-alert form-alert--error">{{ importError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="submitting" @click="showImport = false">取消</button><button class="button button--primary" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交至隔离区' }}</button></div></form></section></div>
  </template>
</template>
