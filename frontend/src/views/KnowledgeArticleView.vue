<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { knowledgeApi, type KnowledgeArticle, type KnowledgeAttachmentScanState } from '@/api/knowledge'

const route = useRoute()
const article = ref<KnowledgeArticle>()
const source = ref<'api' | 'demo'>('api')
const loading = ref(true)
const error = ref('')
const scanLabel: Record<KnowledgeAttachmentScanState, string> = { RECEIVED: '已接收', SCANNING: '扫描中', SCAN_PASSED: '扫描通过', QUARANTINED: '已隔离', REJECTED: '扫描拒绝', SCAN_UNAVAILABLE: '扫描不可用' }
const canReadAttachments = computed(() => article.value?.attachments.filter((item) => item.downloadable && item.scanState === 'SCAN_PASSED') ?? [])
function size(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB` }
async function load(): Promise<void> { loading.value = true; error.value = ''; try { const result = await knowledgeApi.get(String(route.params.articleId)); article.value = result.data; source.value = result.source } catch (cause) { error.value = cause instanceof ApiError ? cause.message : '无法打开该知识，可能已退役或不在当前授权范围。'; article.value = undefined } finally { loading.value = false } }
onMounted(load); watch(() => route.params.articleId, load)
</script>

<template>
  <div class="detail-nav"><RouterLink to="/knowledge">← 返回知识库</RouterLink></div><div v-if="loading" class="panel compact-loading">正在读取知识内容…</div><div v-else-if="error" class="panel empty-state"><span class="empty-icon">!</span><h3>无法打开知识</h3><p>{{ error }}</p></div><template v-else-if="article"><div class="page-heading detail-heading"><div><div class="eyebrow">{{ article.id }} · v{{ article.version }}</div><h2>{{ article.title }}</h2><div class="tag-row"><span v-for="tag in article.tags" :key="tag.code ?? tag.name" class="tag tag--muted">{{ tag.name }}</span><span v-if="article.sourceType" class="tag tag--blue">{{ article.sourceType === 'RESOLVED_CASE' ? '已解决案例沉淀' : article.sourceType === 'IMPORTED' ? '受控导入' : '人工维护' }}</span></div></div></div><p v-if="source === 'demo'" class="demo-notice">开发演示数据：不可用于判断生产环境的授权、审核或扫描结果。</p><div class="knowledge-detail-layout"><article class="panel knowledge-content"><div class="panel-header"><div><h3>处理指引</h3><p>内容已按服务端当前授权范围返回；请勿在处理记录中填写凭据或令牌。</p></div></div><p>{{ article.content }}</p></article><aside class="knowledge-side"><section class="panel"><div class="panel-header"><div><h3>关联范围</h3></div></div><dl class="knowledge-definition"><div><dt>发布状态</dt><dd>已发布</dd></div><div><dt>关联服务目录</dt><dd>{{ article.serviceCatalogItems?.map((item) => item.name).join('、') ?? article.relatedServiceCatalogItemIds?.join('、') ?? '—' }}</dd></div><div v-if="article.relatedCases?.length"><dt>关联案例</dt><dd>{{ article.relatedCases.map((item) => item.title).join('、') }}</dd></div><div><dt>发布时间</dt><dd>{{ article.publishedAt.slice(0, 16).replace('T', ' ') }}</dd></div></dl></section><section class="panel"><div class="panel-header"><div><h3>受控附件</h3><p>仅展示服务端返回的扫描通过附件。</p></div></div><ul class="knowledge-attachment-list"><li v-for="file in article.attachments" :key="file.id"><div><b>{{ file.displayFileName }}</b><small>{{ size(file.sizeBytes) }} · {{ file.detectedMediaType }}</small><span :class="`scan-state scan-state--${file.scanState.toLowerCase()}`">{{ scanLabel[file.scanState] }}</span></div><span class="attachment-blocked">下载入口待服务端开放</span></li></ul><p v-if="!canReadAttachments.length" class="scan-notice">当前没有可下载附件；知识正文不展示隔离或未通过扫描的文件。</p></section></aside></div></template>
</template>
