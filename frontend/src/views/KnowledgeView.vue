<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiError } from '@/api/client'
import { displayTime } from '@/utils/displayTime'
import { knowledgeApi, type KnowledgeArticleSummary, type KnowledgeWorkbenchSection } from '@/api/knowledge'
import { catalogApi, type ServiceCatalogItem } from '@/api/catalog'
import { useSessionStore } from '@/stores/session'

type Section = 'REPOSITORY' | 'FAVORITES' | KnowledgeWorkbenchSection
const section = ref<Section>('REPOSITORY')
const entries = ref<KnowledgeArticleSummary[]>([])
const repository = ref<KnowledgeArticleSummary[]>([])
const loading = ref(false)
const error = ref('')
const query = ref('')
const selectedCategory = ref('')
const draftDialog = ref(false)
const saving = ref(false)
const editing = ref<{ id: string; version: number }>()
const catalogs = ref<ServiceCatalogItem[]>([])
const catalogLoading = ref(false)
const session = useSessionStore()
let loadGeneration = 0
const draft = reactive({ title: '', categoryCode: 'DOCUMENT', serviceCatalogItemIds: [] as string[], tags: '', content: '' })
const baseSections: Array<{ key: Section; label: string; note: string; review?: boolean }> = [
  { key: 'REPOSITORY', label: '知识仓库', note: '已发布且当前账号有权阅读' },
  { key: 'FAVORITES', label: '我的收藏', note: '个人收藏的已发布知识' },
  { key: 'MY_DRAFTS', label: '我的草稿', note: '尚未提交审核，可继续处理' },
  { key: 'MY_SUBMISSIONS', label: '我发起的', note: '本人保存或提交的知识' },
  { key: 'PENDING_REVIEW', label: '我的待审', note: '当前权限范围内待审核知识', review: true },
  { key: 'REVIEWED', label: '已审知识', note: '由本人完成审核的知识', review: true },
]
const canReview = computed(() => session.authorization?.roles.some((role) => role === 'SERVICE_MANAGER' || role === 'PLATFORM_ADMIN') ?? false)
const sections = computed(() => baseSections.filter((item) => !item.review || canReview.value))
const categories = computed(() => [...new Set(repository.value.map((item) => item.category).filter((item): item is string => Boolean(item)))].sort())
const visible = computed(() => selectedCategory.value && section.value === 'REPOSITORY' ? entries.value.filter((item) => item.category === selectedCategory.value) : entries.value)
function message(cause: unknown, fallback: string): string { return cause instanceof ApiError ? cause.message : fallback }
function statusLabel(status?: string): string { return ({ DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', SUPERSEDED: '历史版本', REJECTED: '已驳回', ARCHIVED: '已归档', IMPORTED: '待处理', MIGRATION_PENDING: '迁移待定' } as Record<string, string>)[status ?? ''] ?? status ?? '未知' }

async function load(next: Section = section.value): Promise<void> {
  const request = ++loadGeneration
  if (!sections.value.some((item) => item.key === next)) next = 'REPOSITORY'
  section.value = next; loading.value = true; error.value = ''; selectedCategory.value = ''
  try { let result: KnowledgeArticleSummary[]; if (next === 'REPOSITORY') { const response = await knowledgeApi.list({ q: query.value.trim() || undefined }); result = response.data.items } else if (next === 'FAVORITES') result = await knowledgeApi.favorites(); else result = await knowledgeApi.workbench(next); if (request !== loadGeneration) return; entries.value = result; if (next === 'REPOSITORY') repository.value = result }
  catch (cause) { if (request !== loadGeneration) return; entries.value = []; error.value = message(cause, '知识数据暂不可用，请稍后重试。') } finally { if (request === loadGeneration) loading.value = false }
}
async function toggleFavorite(item: KnowledgeArticleSummary): Promise<void> { try { item.favorite = await knowledgeApi.setFavorite(item.id, !item.favorite); ElMessage.success(item.favorite ? '已加入个人收藏' : '已取消收藏'); if (section.value === 'FAVORITES' && !item.favorite) entries.value = entries.value.filter((entry) => entry.id !== item.id) } catch (cause) { ElMessage.error(message(cause, '收藏操作未完成')) } }
function resetDraft(): void { editing.value = undefined; Object.assign(draft, { title: '', categoryCode: 'DOCUMENT', serviceCatalogItemIds: [], tags: '', content: '' }) }
async function ensureCatalogs(): Promise<void> { if (catalogs.value.length || catalogLoading.value) return; catalogLoading.value = true; try { const result = await catalogApi.listPublishedItems(); if (result.source !== 'api') throw new ApiError('无法读取当前账号的已授权服务', 503); catalogs.value = result.data.items } finally { catalogLoading.value = false } }
async function openNew(): Promise<void> { resetDraft(); try { await ensureCatalogs(); draftDialog.value = true } catch (cause) { ElMessage.error(message(cause, '无法读取可关联服务')) } }
async function editDraft(item: KnowledgeArticleSummary): Promise<void> { try { const [value] = await Promise.all([knowledgeApi.getWorkspace(item.id), ensureCatalogs()]); editing.value={id:item.id,version:value.version};Object.assign(draft,{title:value.title,categoryCode:value.category||'DOCUMENT',serviceCatalogItemIds:[...value.serviceCatalogItemIds],tags:value.tags.map((tag)=>tag.name).join(', '),content:value.content||''});draftDialog.value=true } catch(cause){ElMessage.error(message(cause,'无法重新读取草稿'))} }
async function saveDraft(submitNow: boolean): Promise<void> { const selectedCatalogs=[...new Set(draft.serviceCatalogItemIds)];const tags=[...new Set(draft.tags.split(',').map((item)=>item.trim()).filter(Boolean).map((item)=>item.startsWith('#')?item:`#${item}`))];if(!draft.title.trim()||!/^[A-Z][A-Z0-9_-]{1,63}$/.test(draft.categoryCode)||!selectedCatalogs.length||!draft.content.trim()){ElMessage.warning('请完整填写标题、知识类型、关联服务和正文');return}saving.value=true;try{const input={title:draft.title.trim(),categoryCode:draft.categoryCode,serviceCatalogItemIds:selectedCatalogs,tags,content:draft.content};let saved:KnowledgeArticleSummary;try{saved=editing.value?await knowledgeApi.updateDraft(editing.value.id,editing.value.version,input):await knowledgeApi.createDraft(input)}catch(cause){ElMessage.error(message(cause,cause instanceof ApiError&&cause.status===409?'草稿已被其他页面修改，请保留当前内容并重新加载后比较。':'知识草稿未保存'));return}editing.value={id:saved.id,version:saved.version};if(submitNow){try{await knowledgeApi.submitDraft(saved.id)}catch(cause){ElMessage.warning(`草稿 v${saved.version} 已保存，但提交审核失败：${message(cause,'请稍后重试')}`);return}}draftDialog.value=false;resetDraft();ElMessage.success(submitNow?'知识已提交审核':'知识草稿已保存');await load(submitNow?'MY_SUBMISSIONS':'MY_DRAFTS')}finally{saving.value=false}}
async function submit(item: KnowledgeArticleSummary): Promise<void> { try { await knowledgeApi.submitDraft(item.id); ElMessage.success('已提交审核'); await load() } catch (cause) { ElMessage.error(message(cause, '提交审核失败')) } }
async function removeDraft(item: KnowledgeArticleSummary): Promise<void> { try { await ElMessageBox.confirm(`确认删除草稿“${item.title}”？`, '删除草稿', { type: 'warning' }); await knowledgeApi.deleteDraft(item.id); entries.value = entries.value.filter((entry) => entry.id !== item.id); ElMessage.success('草稿已删除') } catch (cause) { if (cause !== 'cancel') ElMessage.error(message(cause, '草稿删除失败')) } }
async function publish(item: KnowledgeArticleSummary): Promise<void> { if (!item.currentVersionId) return; try { await ElMessageBox.confirm(`确认审核通过并发布“${item.title}”？`, '知识审核', { type: 'warning' }); await knowledgeApi.publish(item.id, item.currentVersionId); ElMessage.success('审核通过，知识已发布'); await load() } catch (cause) { if (cause !== 'cancel') ElMessage.error(message(cause, '审核操作失败')) } }
onMounted(() => load())
</script>

<template>
  <div class="page-heading knowledge-heading"><div><span class="eyebrow">知识管理</span><h2>知识仓库</h2><p>沉淀共性问题和已解决案例；检索结果仅包含当前账号有权阅读的已发布知识。</p></div><el-button type="primary" @click="openNew">新增知识</el-button></div>
  <div class="knowledge-shell"><aside class="panel knowledge-nav"><b>知识管理</b><button v-for="item in sections" :key="item.key" type="button" :class="{ active: section === item.key }" @click="load(item.key)"><span>{{ item.label }}</span><small>{{ item.note }}</small></button><div class="scope-note"><b>本次未实现</b><p>知识专题、分享、纠错、积分和热榜暂无真实服务端能力，因此不显示虚假入口或数字。</p></div></aside>
    <main class="knowledge-main"><section class="panel knowledge-toolbar"><form v-if="section === 'REPOSITORY'" @submit.prevent="load('REPOSITORY')"><el-input v-model="query" clearable maxlength="100" placeholder="按标题、分类或标签检索" /><el-button type="primary" native-type="submit" :loading="loading">搜索</el-button><el-button @click="query = ''; load('REPOSITORY')">重置</el-button></form><div><b>{{ sections.find((item) => item.key === section)?.label }}</b><span>{{ sections.find((item) => item.key === section)?.note }}</span></div></section><p v-if="error" class="form-alert form-alert--error">{{ error }}</p><div v-if="section === 'REPOSITORY' && categories.length" class="knowledge-category-strip"><button type="button" :class="{ active: !selectedCategory }" @click="selectedCategory = ''">全部</button><button v-for="category in categories" :key="category" type="button" :class="{ active: selectedCategory === category }" @click="selectedCategory = category">{{ category }}</button></div>
<section v-loading="loading" class="knowledge-grid"><article v-for="item in visible" :key="item.id" class="panel knowledge-entry"><div class="knowledge-entry__top"><span>{{ item.category || '知识文档' }}</span><span>{{ statusLabel(item.publicationStatus) }}</span></div><h3><RouterLink :to="`/knowledge/${encodeURIComponent(item.id)}`">{{ item.title }}</RouterLink></h3><p>{{ item.sourceTicketId ? `来源工单 ${item.sourceTicketId}` : `归属组织 ${item.owningOrganizationId}` }}</p><div class="tag-row"><span v-for="tag in item.tags" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div><footer><span>v{{ item.version }} · 更新于 {{ displayTime(item.updatedAt) }}</span><div><button v-if="item.publicationStatus === 'PUBLISHED'" type="button" @click="toggleFavorite(item)">{{ item.favorite ? '取消收藏' : '收藏' }}</button><button v-if="item.publicationStatus === 'DRAFT'" type="button" @click="editDraft(item)">编辑</button><button v-if="item.publicationStatus === 'DRAFT'" type="button" @click="submit(item)">提交审核</button><button v-if="section === 'MY_DRAFTS'" class="danger" type="button" @click="removeDraft(item)">删除</button><button v-if="section === 'PENDING_REVIEW'" type="button" @click="publish(item)">审核通过</button><RouterLink :to="`/knowledge/${encodeURIComponent(item.id)}`">查看正文</RouterLink></div></footer></article><div v-if="!loading && !visible.length" class="panel empty-state"><h3>暂无知识</h3><p>当前分区没有符合条件且有权查看的记录。</p></div></section>
    </main></div>
  <el-dialog v-model="draftDialog" :title="editing ? '编辑知识草稿' : '新增知识'" width="680px" destroy-on-close><el-form label-position="top"><div class="draft-grid"><el-form-item label="知识标题"><el-input v-model="draft.title" maxlength="200" show-word-limit /></el-form-item><el-form-item label="知识类型"><el-select v-model="draft.categoryCode"><el-option label="文档类" value="DOCUMENT" /><el-option label="问答类" value="Q_AND_A" /><el-option label="经典案例类" value="CLASSIC_CASE" /></el-select></el-form-item></div><el-form-item label="关联服务"><el-select v-model="draft.serviceCatalogItemIds" multiple filterable collapse-tags collapse-tags-tooltip :loading="catalogLoading" placeholder="搜索并选择当前账号可用服务" style="width:100%"><el-option v-for="catalog in catalogs" :key="catalog.id" :label="`${catalog.name}（${catalog.code}）`" :value="catalog.id" /></el-select></el-form-item><el-form-item label="标签"><el-input v-model="draft.tags" placeholder="例如 ERP, 查询超时" /></el-form-item><el-form-item label="知识正文"><el-input v-model="draft.content" type="textarea" :rows="10" maxlength="100000" show-word-limit placeholder="填写可复用、已脱敏的处理步骤；不要写入密码或令牌。" /></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="draftDialog = false">取消</el-button><el-button :loading="saving" @click="saveDraft(false)">保存草稿</el-button><el-button type="primary" :loading="saving" @click="saveDraft(true)">保存并提交审核</el-button></template></el-dialog>
</template>

<style scoped>
.knowledge-heading{align-items:flex-end}.knowledge-shell{display:grid;grid-template-columns:230px minmax(0,1fr);gap:16px}.knowledge-nav{align-self:start;padding:12px}.knowledge-nav>b{display:block;padding:8px 10px;color:#263f57}.knowledge-nav button{display:grid;width:100%;gap:3px;padding:10px;border:0;border-radius:5px;color:#445d74;background:transparent;text-align:left;cursor:pointer}.knowledge-nav button:hover,.knowledge-nav button.active{color:#1767ad;background:#eaf4ff}.knowledge-nav small{color:#8192a3;font-size:10px}.scope-note{margin-top:12px;padding:12px;border-top:1px solid #e8edf2;color:#74889a;font-size:11px;line-height:1.6}.scope-note p{margin:4px 0 0}.knowledge-main{min-width:0}.knowledge-toolbar{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:14px}.knowledge-toolbar form{display:flex;flex:1;max-width:650px;gap:8px}.knowledge-toolbar>div{display:grid;gap:3px;text-align:right}.knowledge-toolbar span{color:#7e8f9f;font-size:11px}.knowledge-category-strip{display:flex;gap:6px;padding:12px 0;overflow:auto}.knowledge-category-strip button{padding:6px 12px;border:1px solid #dbe6ef;border-radius:4px;color:#526a80;background:#fff}.knowledge-category-strip button.active{border-color:#3983c5;color:#1767ad;background:#eef7ff}.knowledge-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;min-height:180px}.knowledge-entry{display:grid;gap:10px;padding:16px}.knowledge-entry__top{display:flex;justify-content:space-between;color:#3f82b7;font-size:11px}.knowledge-entry h3{margin:0;font-size:15px}.knowledge-entry>p{margin:0;color:#75899b;font-size:12px}.knowledge-entry footer{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-top:10px;border-top:1px solid #edf1f5;color:#8897a5;font-size:10px}.knowledge-entry footer>div{display:flex;gap:9px;align-items:center}.knowledge-entry footer button{padding:0;border:0;color:#2672b5;background:none;cursor:pointer}.knowledge-entry footer button.danger{color:#c84848}.draft-grid{display:grid;grid-template-columns:2fr 1fr;gap:12px}@media(max-width:900px){.knowledge-shell{grid-template-columns:1fr}.knowledge-nav{display:flex;overflow:auto}.knowledge-nav>b,.scope-note{display:none}.knowledge-nav button{min-width:130px}.knowledge-grid{grid-template-columns:1fr}.knowledge-entry footer{align-items:flex-start;flex-direction:column}.draft-grid{grid-template-columns:1fr}}
</style>
