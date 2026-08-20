<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { catalogApi, type PublishedServiceCatalogForm, type ServiceCatalogItem } from '@/api/catalog'

const items = ref<ServiceCatalogItem[]>([]), selectedId = ref(''), selectedForm = ref<PublishedServiceCatalogForm>()
const source = ref<'api' | 'demo'>('api'), error = ref(''), loadingForm = ref(false)
const selected = computed(() => items.value.find((item) => item.id === selectedId.value))
const typeLabel = (type: ServiceCatalogItem['ticketType']) => ({ INCIDENT: '故障报修', ACCESS_REQUEST: '账号权限', SERVICE_REQUEST: '服务请求', PROBLEM: '问题管理', CHANGE: '变更' })[type]
async function select(item: ServiceCatalogItem): Promise<void> {
  selectedId.value = item.id; selectedForm.value = undefined; error.value = ''; loadingForm.value = true
  try { const result = await catalogApi.getPublishedForm(item.id); selectedForm.value = result.data; if (result.source === 'demo') source.value = 'demo' }
  catch { error.value = '该目录表单暂不可用，请检查服务端授权或稍后重试。' }
  finally { loadingForm.value = false }
}
onMounted(async () => {
  try { const result = await catalogApi.listPublishedItems(); items.value = result.data.items; source.value = result.source; if (items.value[0]) await select(items.value[0]) }
  catch { error.value = '服务配置暂不可用，请检查后端服务或稍后重试。' }
})
</script>

<template>
  <div class="page-heading"><div><h2>服务配置</h2><p>展示当前主体可见的已发布目录、动态表单和标签策略；草稿、审批及管理动作须走受控管理接口。</p></div><span class="readonly-badge">已发布配置只读预览</span></div>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在 API 确实不可用时展示，不代表生产配置。</p><p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <section class="panel config-catalog-panel"><div class="panel-header"><div><h3>可用服务目录</h3><p>服务端已按 IAM 身份、组织及服务数据范围过滤。</p></div><span class="readonly-badge">{{ items.length }} 项</span></div><div class="table-scroll"><table><thead><tr><th>目录名称</th><th>分类 / 类型</th><th>已发布表单版本</th><th>标准标签</th><th>表单摘要</th></tr></thead><tbody><tr v-for="item in items" :key="item.id" :class="{ 'config-row--selected': item.id === selected?.id }" @click="select(item)"><td><b>{{ item.name }}</b><span class="table-subtext">{{ item.code }}</span></td><td>{{ item.categoryCode }}<span class="table-subtext">{{ typeLabel(item.ticketType) }}</span></td><td>v{{ item.publishedVersion }}</td><td>{{ item.tags?.length ?? 0 }}</td><td>{{ item.summary ?? '—' }}</td></tr></tbody></table></div></section>
  <template v-if="selected"><section class="config-summary"><div><b>{{ selected.name }}</b><span>{{ selected.summary ?? '当前目录未提供摘要。' }}</span></div><div class="tag-row"><span class="tag tag--blue">{{ typeLabel(selected.ticketType) }}</span><span class="tag tag--muted">v{{ selected.publishedVersion }}</span></div></section>
    <p v-if="loadingForm" class="compact-loading">正在读取已发布表单…</p>
    <div v-else-if="selectedForm" class="config-workspace"><section class="panel"><div class="panel-header"><div><h3>动态表单字段</h3><p>字段定义来自已发布版本，前端只负责安全呈现。</p></div></div><div class="field-config-list"><article v-for="field in selectedForm.fields" :key="field.code"><div><b>{{ field.label }} <em v-if="field.required">必填</em></b><small>{{ field.code }} · {{ field.sensitivity }} / {{ field.masking }}</small></div><span>{{ field.type }}<small v-if="field.dictionaryCode"> · {{ field.dictionaryCode }}</small></span></article></div></section>
      <section class="panel"><div class="panel-header"><div><h3>标签与字段策略</h3><p>目录标签及字段可见性仍由服务端再次校验。</p></div></div><div class="tag-row config-tags"><span v-for="tag in selected.tags" :key="tag.code" class="tag tag--blue">{{ tag.name }}</span></div><div class="config-note"><b>标签策略</b><span>标准标签：{{ selectedForm.tagPolicy.allowStandardTags ? '允许' : '不允许' }}；自定义标签：{{ selectedForm.tagPolicy.allowFreeTags ? '允许' : '不允许' }}；最多 {{ selectedForm.tagPolicy.maxTags }} 个。</span></div><div class="config-note"><b>规则案例</b><span>匹配接口只返回当前主体可见、已脱敏的建议，不暴露规则表达式或内部路由。</span></div></section></div>
  </template>
</template>
