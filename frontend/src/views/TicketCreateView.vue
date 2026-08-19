<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type TagKind, type TicketCreateRequest, type TicketType } from '@/api/tickets'

interface CatalogItem { id: string; name: string; type: TicketType; hint: string }

const router = useRouter()
const submitting = ref(false)
const submitError = ref('')
const submitNotice = ref('')
const customTag = ref('')
const form = ref({
  catalogId: 'CAT-ERP-PERFORMANCE', type: 'INCIDENT' as TicketType, title: '', description: '',
  affectedSystem: 'ERP', affectedPage: '', occurrence: '持续发生', urgency: 'NORMAL', contactWindow: '工作时间', tags: ['#页面卡顿', '#ERP'] as string[],
})

const catalogItems: CatalogItem[] = [
  { id: 'CAT-ERP-PERFORMANCE', name: '业务系统 - 页面性能问题', type: 'INCIDENT', hint: '适用于页面加载慢、查询超时、操作卡顿。' },
  { id: 'CAT-NETWORK-FAULT', name: '网络服务 - 连通性故障', type: 'INCIDENT', hint: '适用于无法访问、网络中断、VPN 异常。' },
  { id: 'CAT-FIN-ACCESS', name: '账号与权限 - 角色申请', type: 'ACCESS_REQUEST', hint: '需按目录触发审批与最小权限校验。' },
  { id: 'CAT-SOFTWARE-INSTALL', name: '软件服务 - 白名单软件安装', type: 'SERVICE_REQUEST', hint: '仅申请目录中已批准的软件。' },
]
const suggestedTags = ['#页面卡顿', '#扫码', '#核协E+', '#账号权限', '#网络故障', '#软件安装']
const selectedCatalog = computed(() => catalogItems.find((item) => item.id === form.value.catalogId) ?? catalogItems[0])

function onCatalogChange(): void {
  form.value.type = selectedCatalog.value.type
}

function addTag(): void {
  const value = customTag.value.trim()
  const tag = value ? (value.startsWith('#') ? value : `#${value.replaceAll('#', '')}`) : ''
  if (tag && !form.value.tags.includes(tag) && form.value.tags.length < 20) form.value.tags.push(tag)
  customTag.value = ''
}

function toggleTag(tag: string): void {
  const index = form.value.tags.indexOf(tag)
  if (index >= 0) form.value.tags.splice(index, 1)
  else if (form.value.tags.length < 20) form.value.tags.push(tag)
}

function tagKind(name: string): TagKind {
  return suggestedTags.includes(name) ? 'STANDARD' : 'FREE'
}

async function submit(): Promise<void> {
  submitError.value = ''
  submitNotice.value = ''
  if (form.value.title.trim().length < 4) {
    submitError.value = '请填写至少 4 个字符的工单主题。'
    return
  }
  if (!form.value.description.trim()) {
    submitError.value = '请说明问题现象或服务诉求。'
    return
  }
  submitting.value = true
  const request: TicketCreateRequest = {
    serviceCatalogItemId: form.value.catalogId,
    type: form.value.type,
    title: form.value.title.trim(),
    description: form.value.description.trim(),
    structuredFields: {
      affectedSystem: form.value.affectedSystem,
      affectedPage: form.value.affectedPage.trim(),
      occurrence: form.value.occurrence,
      urgency: form.value.urgency,
      contactWindow: form.value.contactWindow,
    },
    tags: form.value.tags.map((name) => ({ name, kind: tagKind(name) })),
  }
  try {
    const result = await ticketApi.create(request)
    submitNotice.value = result.source === 'demo' ? '演示工单已创建，正在打开详情。' : '工单已提交，正在打开详情。'
    await router.push(`/tickets/${result.data.id}`)
  } catch (error) {
    submitError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-heading"><div><h2>发起工单</h2><p>优先选择服务目录并填写标准字段；提交人、组织、优先级与处理人由后端确定。</p></div></div>
  <form class="ticket-create-layout" @submit.prevent="submit">
    <section class="panel form-panel">
      <div class="panel-header"><div><h3>服务与问题描述</h3><p>目录决定后续流程、审批、分派与 SLA 规则。</p></div></div>
      <div class="form-grid">
        <label class="field field--full"><span>服务目录 <b>*</b></span><select v-model="form.catalogId" @change="onCatalogChange"><option v-for="item in catalogItems" :key="item.id" :value="item.id">{{ item.name }}</option></select><small>{{ selectedCatalog.hint }}</small></label>
        <label class="field"><span>工单类型</span><input :value="form.type === 'INCIDENT' ? '故障报修' : form.type === 'ACCESS_REQUEST' ? '账号权限' : '服务请求'" readonly /></label>
        <label class="field"><span>影响系统 <b>*</b></span><select v-model="form.affectedSystem"><option>ERP</option><option>财务共享</option><option>核协E+</option><option>办公网络</option><option>其他</option></select></label>
        <label class="field field--full"><span>工单主题 <b>*</b></span><input v-model="form.title" maxlength="200" placeholder="例如：ERP 采购订单页面加载缓慢" /></label>
        <label class="field field--full"><span>问题现象 / 服务说明 <b>*</b></span><textarea v-model="form.description" maxlength="4000" rows="5" placeholder="请填写发生时间、影响范围、已尝试操作及报错信息；避免填写密码、令牌等敏感信息。"></textarea><small>{{ form.description.length }}/4000</small></label>
        <label class="field"><span>受影响页面 / 模块</span><input v-model="form.affectedPage" maxlength="200" placeholder="例如：采购订单列表" /></label>
        <label class="field"><span>发生情况</span><select v-model="form.occurrence"><option>持续发生</option><option>间歇发生</option><option>首次发生</option></select></label>
        <label class="field"><span>紧急程度</span><select v-model="form.urgency"><option value="NORMAL">一般</option><option value="HIGH">较高</option><option value="URGENT">紧急</option></select></label>
        <label class="field"><span>可联系时段</span><select v-model="form.contactWindow"><option>工作时间</option><option>全天可联系</option><option>指定时段</option></select></label>
      </div>
    </section>

    <aside class="form-sidebar">
      <section class="panel form-panel">
        <div class="panel-header"><div><h3>标签</h3><p>标准选项优先；自定义标签以 # 开头。</p></div></div>
        <div class="tag-choice"><button v-for="tag in suggestedTags" :key="tag" class="tag-choice__item" :class="{ 'is-selected': form.tags.includes(tag) }" type="button" @click="toggleTag(tag)">{{ tag }}</button></div>
        <div class="tag-adder"><input v-model="customTag" maxlength="50" placeholder="#自定义标签" @keyup.enter.prevent="addTag" /><button class="button button--secondary" type="button" @click="addTag">添加</button></div>
        <div v-if="form.tags.length" class="tag-row selected-tags"><span v-for="tag in form.tags" :key="tag" class="tag tag--blue">{{ tag }} <button type="button" :aria-label="`移除 ${tag}`" @click="toggleTag(tag)">×</button></span></div>
      </section>
      <section class="panel rule-hint"><h3>案例匹配（预留）</h3><p>当前按目录、CI、字段、标签、错误码和关键词做规则匹配；AI 能力后续单独接入。</p><span>提交后由后端执行匹配与权限过滤</span></section>
    </aside>

    <div class="form-actions"><p v-if="submitError" class="form-alert form-alert--error">{{ submitError }}</p><p v-else-if="submitNotice" class="form-alert form-alert--success">{{ submitNotice }}</p><RouterLink class="button button--secondary" to="/tickets">取消</RouterLink><button class="button button--primary" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交工单' }}</button></div>
  </form>
</template>
