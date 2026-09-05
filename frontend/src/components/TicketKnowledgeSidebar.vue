<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { knowledgeApi, type KnowledgeArticleSummary } from '@/api/knowledge'

const props = defineProps<{ ticketId: string; catalogId: string }>()
const emit = defineEmits<{ reference: [value: { id: string; title: string; url: string }] }>()
const query = ref('')
const entries = ref<KnowledgeArticleSummary[]>([])
const state = ref<'LOADING' | 'READY' | 'ERROR'>('LOADING')
const demo = ref(false)
let generation = 0
let disposed = false
const visibleEntries = computed(() => [...entries.value].sort((a, b) => Number(b.serviceCatalogItemIds.includes(props.catalogId)) - Number(a.serviceCatalogItemIds.includes(props.catalogId))).slice(0, 6))
async function search(): Promise<void> {
  const request = ++generation
  state.value = 'LOADING'
  entries.value = []
  try {
    const result = await knowledgeApi.list({ q: query.value.trim() })
    if (disposed || request !== generation) return
    entries.value = result.data.items.filter((item) => item.publicationStatus === 'PUBLISHED')
    demo.value = result.source === 'demo'
    state.value = 'READY'
  } catch { if (!disposed && request === generation) state.value = 'ERROR' }
}
function reference(entry: KnowledgeArticleSummary): void { emit('reference', { id: entry.id, title: entry.title, url: `/knowledge/${encodeURIComponent(entry.id)}` }) }
watch(() => [props.ticketId, props.catalogId], () => { query.value = ''; void search() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; generation++ })
</script>

<template>
  <section class="panel detail-panel processing-knowledge" aria-label="处理参考知识">
    <div class="panel-header"><h3>知识库</h3><RouterLink to="/knowledge" target="_blank" rel="noopener noreferrer">查看全部 ↗</RouterLink></div>
    <form class="processing-knowledge-search" @submit.prevent="search"><label for="processing-knowledge-q" class="knowledge-search-label">搜索排查指引</label><div><input id="processing-knowledge-q" v-model="query" maxlength="100" type="search" placeholder="输入关键词查找知识" /><button type="submit" :disabled="state === 'LOADING'">搜索</button></div></form>
    <p v-if="state === 'LOADING'" role="status">正在读取可见知识…</p>
    <div v-else-if="state === 'ERROR'" role="status"><p>知识暂不可用，不影响工单处理。</p><button class="button button--secondary" type="button" @click="search">重试加载</button></div>
    <template v-else>
      <p v-if="demo" class="demo-notice">演示知识，不代表当前实时匹配结果。</p>
      <ul v-if="visibleEntries.length"><li v-for="entry in visibleEntries" :key="entry.id"><div><RouterLink :to="`/knowledge/${encodeURIComponent(entry.id)}`" target="_blank" rel="noopener noreferrer">{{ entry.title }} ↗</RouterLink><button type="button" @click="reference(entry)">引用</button></div><small v-if="entry.serviceCatalogItemIds.includes(catalogId)">适用于当前服务目录</small></li></ul>
      <p v-else role="status">当前范围内没有匹配知识，可换个关键词。</p>
    </template>
    <small class="processing-knowledge-hint">仅检索已授权的发布条目；在新标签查看，不带走当前处理内容。</small>
  </section>
</template>

<style scoped>
.processing-knowledge { border-top: 3px solid #2874ba; }
.processing-knowledge-search > div { display: flex; gap: 5px; }
.knowledge-search-label { display: block; margin-bottom: 5px; color: #637d92; font-size: 11px; }
.processing-knowledge-search input { min-width: 0; width: 100%; border: 1px solid #d8e4ed; border-radius: 3px; padding: 7px; background: #f7fbfc; font-size: 12px; }
.processing-knowledge-search button { flex: 0 0 auto; white-space: nowrap; padding: 5px 8px; border: 1px solid #b9d2e9; border-radius: 3px; color: #24659b; background: #f1f7fc; font-size: 12px; }
.processing-knowledge ul { display: grid; gap: 12px; list-style: none; padding: 0; margin: 16px 0; }
.processing-knowledge li a { font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.processing-knowledge li > div { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.processing-knowledge li button { flex: 0 0 auto; padding: 2px 7px; border: 1px solid #b9d2e9; border-radius: 3px; color: #24659b; background: #f1f7fc; cursor: pointer; font-size: 11px; }
.processing-knowledge li small { display: block; color: #7c91a5; margin-top: 3px; font-size: 10px; }
.processing-knowledge p, .processing-knowledge-hint { color: #768b9e; font-size: 11px; line-height: 1.6; }
.processing-knowledge-hint { display: block; border-top: 1px solid #edf1f5; padding-top: 10px; }
</style>
