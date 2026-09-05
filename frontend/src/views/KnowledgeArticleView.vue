<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/client'
import { displayTime } from '@/utils/displayTime'
import { knowledgeApi, type KnowledgeArticle, type KnowledgeFeedbackSummary, type KnowledgeVersion } from '@/api/knowledge'

const route = useRoute()
const article = ref<KnowledgeArticle>()
const versions = ref<KnowledgeVersion[]>([])
const feedback = ref<KnowledgeFeedbackSummary>()
const loading = ref(true)
const error = ref('')
const published = computed(() => article.value?.publicationStatus === 'PUBLISHED')
function message(cause: unknown, fallback: string): string { return cause instanceof ApiError ? cause.message : fallback }
function statusLabel(status?: string): string { return ({ DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', SUPERSEDED: '历史版本', REJECTED: '已驳回', ARCHIVED: '已归档' } as Record<string, string>)[status ?? ''] ?? status ?? '—' }
async function load(): Promise<void> { loading.value = true; error.value = ''; try { const id=String(route.params.articleId);try{article.value=(await knowledgeApi.get(id)).data}catch(publicError){if(!(publicError instanceof ApiError)||publicError.status!==404)throw publicError;article.value=await knowledgeApi.getWorkspace(id)} versions.value=await knowledgeApi.versions(id); feedback.value=published.value?await knowledgeApi.feedbackSummary(id):undefined } catch (cause) { article.value=undefined; versions.value=[]; error.value=message(cause,'无法打开该知识，可能不在当前授权范围。') } finally { loading.value=false } }
async function toggleFavorite(): Promise<void> { if(!article.value||!published.value)return; try{article.value.favorite=await knowledgeApi.setFavorite(article.value.id,!article.value.favorite);ElMessage.success(article.value.favorite?'已加入个人收藏':'已取消收藏')}catch(cause){ElMessage.error(message(cause,'收藏操作未完成'))} }
async function vote(value:'HELPFUL'|'NOT_HELPFUL'):Promise<void>{if(!article.value||!published.value)return;try{feedback.value=await knowledgeApi.feedback(article.value.id,value,value==='NOT_HELPFUL'?'NEEDS_REVIEW':undefined);ElMessage.success('评价已记录')}catch(cause){ElMessage.error(message(cause,'评价未提交'))}}
onMounted(load);watch(()=>route.params.articleId,load)
</script>

<template>
  <div class="detail-nav"><RouterLink to="/knowledge">← 返回知识仓库</RouterLink></div>
  <div v-if="loading" class="panel compact-loading">正在读取知识正文…</div>
  <div v-else-if="error" class="panel empty-state"><h3>无法打开知识</h3><p>{{ error }}</p></div>
  <template v-else-if="article"><header class="page-heading knowledge-article-heading"><div><span class="eyebrow">{{ article.category }} · {{ statusLabel(article.publicationStatus) }} · v{{ article.version }}</span><h2>{{ article.title }}</h2><div class="tag-row"><span v-for="tag in article.tags" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></div><el-button v-if="published" @click="toggleFavorite">{{ article.favorite ? '★ 已收藏' : '☆ 收藏' }}</el-button></header>
<div class="knowledge-article-layout"><main><article class="panel knowledge-body"><p class="knowledge-body__meta">创建人 {{ article.creatorIamUserId || '—' }} · 创建于 {{ displayTime(article.createdAt) }} · 更新于 {{ displayTime(article.updatedAt) }}</p><div v-if="article.content" class="knowledge-plain-content">{{ article.content }}</div><p v-else class="scan-notice">该版本没有可在浏览器展示的纯文本正文。受控导入附件不会被伪造为正文。</p><section v-if="published" class="knowledge-feedback"><div><b>这篇知识是否有帮助？</b><span v-if="feedback">{{ feedback.helpfulCount }} 个“有帮助” · {{ feedback.notHelpfulCount }} 个“需复审”</span></div><el-button size="small" @click="vote('HELPFUL')">有帮助</el-button><el-button size="small" @click="vote('NOT_HELPFUL')">需复审</el-button></section></article>
      <section class="panel version-panel"><div class="panel-header"><div><h3>历史版本</h3><p>仅显示当前账号有权查看的版本元数据；历史正文和隔离附件不越权开放。</p></div></div><el-table :data="versions" empty-text="暂无可见版本"><el-table-column prop="versionNumber" label="版本" width="80"><template #default="scope">v{{ scope.row.versionNumber }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column><el-table-column prop="detectedMediaType" label="内容类型" /><el-table-column prop="reviewerIamUserId" label="审核人" /><el-table-column label="发布时间"><template #default="scope">{{ scope.row.publishedAt?.slice(0,16).replace('T',' ') || '—' }}</template></el-table-column></el-table></section>
    </main><aside><section class="panel"><h3>知识信息</h3><dl class="knowledge-definition"><div><dt>归属组织</dt><dd>{{ article.owningOrganizationId }}</dd></div><div><dt>关联服务</dt><dd>{{ article.serviceCatalogItemIds.join('、') }}</dd></div><div><dt>来源</dt><dd><RouterLink v-if="article.sourceTicketId" :to="`/tickets/${encodeURIComponent(article.sourceTicketId)}`">工单 {{ article.sourceTicketId }}</RouterLink><span v-else>人工维护或受控导入</span></dd></div><div><dt>当前状态</dt><dd>{{ statusLabel(article.publicationStatus) }}</dd></div></dl></section><section class="panel capability-note"><h3>能力说明</h3><p>当前支持正文、来源工单、历史版本、个人收藏和结构化有用性评价。</p><p>分享、纠错、知识专题、最近访客与评论尚未实现，不展示虚假按钮或统计。</p></section></aside></div>
  </template>
</template>

<style scoped>
.knowledge-article-heading{align-items:flex-end}.knowledge-article-layout{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:16px}.knowledge-article-layout>main,.knowledge-article-layout>aside{display:grid;align-content:start;gap:16px}.knowledge-body{padding:22px}.knowledge-body__meta{margin:0 0 20px;padding-bottom:12px;border-bottom:1px solid #e8edf2;color:#7b8d9f;font-size:11px}.knowledge-plain-content{min-height:240px;color:#344e65;font-size:14px;line-height:1.95;white-space:pre-wrap;overflow-wrap:anywhere}.knowledge-feedback{display:flex;align-items:center;gap:8px;margin-top:24px;padding-top:16px;border-top:1px solid #e8edf2}.knowledge-feedback>div{display:grid;flex:1;gap:4px}.knowledge-feedback span{color:#7d8e9e;font-size:11px}.version-panel{padding:16px}.knowledge-article-layout aside>.panel{padding:16px}.knowledge-article-layout aside h3{margin:0 0 12px}.capability-note p{color:#6f8497;font-size:12px;line-height:1.65}@media(max-width:900px){.knowledge-article-layout{grid-template-columns:1fr}}
</style>
