<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useSessionStore } from '@/stores/session'
import { ApiError } from '@/api/client'
import { designerApi, type ControlId, type StudioDraftInput, type StudioDraftSummary, type StudioField, type StudioFormRevision } from '@/api/designer'
import { catalogApi, serviceCatalogAdminApi } from '@/api/catalog'
import BpmnCanvas from '@/components/designer/BpmnCanvas.vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import FormPreview from '@/components/designer/FormPreview.vue'
import NodeFormBindings from '@/components/designer/NodeFormBindings.vue'
import { createEmptyBpmn } from '@/bpmn/templates'
import { validateFields, validateNodeBindings } from '@/forms/registry'
import { hasBindableNode, validateFormIdentities } from '@/utils/studioValidation'
import { belongsToStudioContext, canAssociateStudio } from '@/utils/studioScope'

type CanvasNode = { id: string; name: string; type: string }
type LegacyForm = { id: string; name: string; fields: Array<{ code: string; label: string; type: string; required: boolean; helpText?: string; dictionaryCode?: string }> }
const props = defineProps<{ systemCode: string; systemName: string; organizationId: string; serviceCatalogItemId?: string; serviceName?: string; serviceManaged?: boolean }>()
const emit = defineEmits<{ close: []; 'state-change': [state: { dirty: boolean; busy: boolean; uncertain: boolean }] }>()
const context = computed(() => ({ systemCode: props.systemCode, organizationId: props.organizationId, serviceCatalogItemId: props.serviceCatalogItemId }))
const contextReady = computed(() => Boolean(props.systemCode && props.organizationId))
const session = useSessionStore()
const canRead = computed(() => session.authorization?.roles.some(role => ['SERVICE_MANAGER', 'PLATFORM_ADMIN', 'AUDITOR'].includes(role)) ?? false)
const canWrite = computed(() => contextReady.value && (session.authorization?.roles.some(role => ['SERVICE_MANAGER', 'PLATFORM_ADMIN'].includes(role)) ?? false))
function emptyDraft(): StudioDraftInput { return { version: 0, name: props.serviceName ? `${props.serviceName} · 表单与流程`.slice(0,120) : '新的工单设计', ...context.value, bpmnXml: createEmptyBpmn(), forms: [], nodeBindings: [], reason: '' } }
const draft = ref<StudioDraftInput>(emptyDraft())
const savedId = ref(''), baseline = ref(JSON.stringify(draft.value))
const draftList = ref<StudioDraftSummary[]>([]), legacyForms = ref<LegacyForm[]>([])
const loadingList = ref(false), busy = ref(false), legacyLoading = ref(false)
const notice = ref(''), error = ref(''), legacyId = ref('')
const activeTab = ref<'workflow' | 'forms' | 'check'>('forms'), showPreview = ref(false)
const selectedFormKey = ref(''), selectedNode = ref<CanvasNode | null>(null)
const canvas = ref<InstanceType<typeof BpmnCanvas>>()
const fileInput = ref<HTMLInputElement>()
const documentEpoch = ref(0), canvasPending = ref(false)
let generation = 0, listGeneration = 0, disposed = false
let createKey = crypto.randomUUID()
let pendingCreate: StudioDraftInput | null = null
const createUncertain = ref(false)
const dirty = computed(() => canvasPending.value || JSON.stringify(draft.value) !== baseline.value)
const scopedDrafts = computed(() => draftList.value.filter(value => belongsToStudioContext(value, context.value)))
const assignableDrafts = computed(() => draftList.value.filter(value => canAssociateStudio(value, context.value)))
const formKey = (form: StudioFormRevision) => `${form.formId}@${form.revision}`
const activeForm = computed(() => draft.value.forms.find(form => formKey(form) === selectedFormKey.value))
const selectedBindableNode = computed(() => selectedNode.value && ['bpmn:StartEvent', 'bpmn:UserTask'].includes(selectedNode.value.type) ? selectedNode.value.id : '')
const graphNodes = computed<CanvasNode[]>(() => {
  const doc = new DOMParser().parseFromString(draft.value.bpmnXml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length) return []
  return Array.from(doc.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', '*')).filter(node => node.hasAttribute('id')).map(node => ({ id: node.getAttribute('id')!, name: node.getAttribute('name') ?? '', type: `bpmn:${node.localName.charAt(0).toUpperCase()}${node.localName.slice(1)}` }))
})
const unresolvedBindings = computed(() => draft.value.nodeBindings.filter(binding => !hasBindableNode(binding, graphNodes.value) || !draft.value.forms.some(form => form.formId === binding.formId && form.revision === binding.formRevision)))
const canvasListeners = computed(() => {
  const epoch = documentEpoch.value
  return {
    'update:xml': (xml: string) => { if (!disposed && epoch === documentEpoch.value) { draft.value.bpmnXml = xml; canvasPending.value = false } },
    'dirty-change': (value: boolean) => { if (!disposed && epoch === documentEpoch.value) canvasPending.value = value },
    'select-node': (node: CanvasNode | null) => { if (!disposed && epoch === documentEpoch.value) selectedNode.value = node },
  }
})
function safeError(cause: unknown): string {
  if (cause instanceof ApiError && cause.status === 409) return '设计已被其他人修改。当前编辑保留，请先另行核对最新版本。'
  if (cause instanceof ApiError && [401, 403].includes(cause.status)) return '当前身份无权执行此操作，请检查登录及组织范围。'
  if (cause instanceof ApiError && [400, 422].includes(cause.status)) return '设计未通过校验，请检查 XML、控件、节点绑定、修订号和修改原因。'
  return '设计服务暂不可用。当前页面修改尚未保存，请勿关闭页面。'
}
function applyDraft(value: StudioDraftInput, id = ''): void {
  documentEpoch.value++; canvasPending.value = false
  draft.value = { version: value.version, name: value.name, organizationId: value.organizationId, systemCode: value.systemCode ?? undefined, serviceCatalogItemId: value.serviceCatalogItemId ?? undefined, bpmnXml: value.bpmnXml, forms: value.forms, nodeBindings: value.nodeBindings, reason: value.reason }
  savedId.value = id; baseline.value = JSON.stringify(draft.value)
  selectedFormKey.value = value.forms[0] ? formKey(value.forms[0]) : ''
  selectedNode.value = null
  createKey = crypto.randomUUID(); pendingCreate = null; createUncertain.value = false
}
async function refreshList(): Promise<void> {
  if (!contextReady.value || !canRead.value) return
  const current = generation, listRequest = ++listGeneration
  loadingList.value = true
  try { const result = await designerApi.list(); if (!disposed && generation === current && listGeneration === listRequest) draftList.value = result.items }
  catch { if (!disposed && generation === current && listGeneration === listRequest) error.value = '设计列表加载失败；当前打开的编辑内容未被清空，请稍后刷新列表。' }
  finally { if (!disposed && generation === current && listGeneration === listRequest) loadingList.value = false }
}
function allowDiscard(): boolean { return (!dirty.value && !createUncertain.value) || window.confirm(createUncertain.value ? '创建设计稿结果尚未确认，请先核对设计包列表。确定离开当前编辑吗？' : '当前设计尚未保存，确定放弃这些修改吗？') }
function canLeave(): boolean { return !busy.value && allowDiscard() }
defineExpose({ canLeave })
function newDesign(): void { if (!canWrite.value || busy.value || createUncertain.value || !allowDiscard()) return; applyDraft(emptyDraft()); activeTab.value = 'forms'; showPreview.value = false; notice.value = ''; error.value = '' }
async function openDraft(id: string, associate = false): Promise<void> {
  if (busy.value || !allowDiscard()) return
  if (associate && (!canWrite.value || !window.confirm(`将此设计归入“${props.systemName}${props.serviceName ? ` / ${props.serviceName}` : ''}”？保存后归属锁定；原表单、流程内容保留，不会发布执行。`))) return
  const current = generation; busy.value = true; error.value = ''
  try {
    const result = await designerApi.get(id)
    if (disposed || current !== generation) return
    if (!(associate ? canAssociateStudio(result, context.value) : belongsToStudioContext(result, context.value))) { error.value = '设计归属已变化或不属于当前系统服务，请刷新列表后重新选择。'; return }
    applyDraft(result, result.id)
    if (associate) {
      draft.value.systemCode = props.systemCode
      if (props.serviceCatalogItemId) draft.value.serviceCatalogItemId = props.serviceCatalogItemId
      draft.value.reason = `归入系统 ${props.systemCode}${props.serviceCatalogItemId ? ` 的服务 ${props.serviceCatalogItemId}` : ''}`
    }
    activeTab.value = 'workflow'; showPreview.value = false
    notice.value = associate ? '归属已填入当前编辑，点击“保存配置草稿”后生效；尚未修改旧设计。' : '配置已从服务端读取。'
  }
  catch (cause) { if (!disposed && current === generation) error.value = safeError(cause) }
  finally { if (!disposed && current === generation) busy.value = false }
}
async function captureCanvas(): Promise<void> {
  const epoch = documentEpoch.value, instance = canvas.value
  if (!instance) return
  const xml = await instance.getXml()
  if (disposed || epoch !== documentEpoch.value || instance !== canvas.value) throw new Error('Design changed')
  draft.value.bpmnXml = xml; canvasPending.value = false
}
async function changeTab(tab: 'workflow' | 'forms' | 'check'): Promise<void> {
  if (busy.value || createUncertain.value || activeTab.value === tab) return
  const current = generation
  try { await captureCanvas(); if (!disposed && generation === current) activeTab.value = tab } catch { if (!disposed && generation === current) error.value = '流程 XML 尚未就绪，请修正画布错误后再切换。' }
}
async function save(): Promise<void> {
  if (!canWrite.value || busy.value) return
  error.value = ''; notice.value = ''
  const current = generation; busy.value = true
  try {
    if (!createUncertain.value) await captureCanvas()
    if (disposed || generation !== current) return
    if (!belongsToStudioContext(draft.value, context.value)) { error.value = '当前配置不属于所选系统服务，禁止保存。'; return }
    if (!draft.value.name.trim() || !draft.value.organizationId || draft.value.reason.trim().length < 5) { error.value = '请填写工单设计名称和至少 5 字的修改原因。系统及组织由当前配置上下文确定。'; return }
    const issues = [...validateFormIdentities(draft.value.forms), ...draft.value.forms.flatMap(form => validateFields(form.fields).map(issue => `${form.name} r${form.revision}：${issue}`)), ...validateNodeBindings(draft.value.nodeBindings, draft.value.forms)]
    if (issues.length) { error.value = issues.slice(0, 5).join('；'); return }
    if (unresolvedBindings.value.length) { error.value = '存在已删除节点或不存在表单版本的绑定，请先在“发布检查”中处理。'; return }
    const input: StudioDraftInput = pendingCreate ?? JSON.parse(JSON.stringify(draft.value))
    if (!savedId.value) pendingCreate = input
    const result = savedId.value ? await designerApi.save(savedId.value, input) : await designerApi.create(input, createKey)
    if (disposed || generation !== current) return
    const keepForm = selectedFormKey.value
    applyDraft(result, result.id)
    if (draft.value.forms.some(form => formKey(form) === keepForm)) selectedFormKey.value = keepForm
    notice.value = `设计稿已保存（并发版本 v${result.version}）。当前仅设计，不会部署或改变工单运行。`
    await refreshList()
  } catch (cause) {
    if (!disposed && current === generation) {
      if (!savedId.value && pendingCreate) {
        const rejected = !createUncertain.value && cause instanceof ApiError && [400, 401, 403, 404, 413, 415, 422].includes(cause.status)
        if (rejected) pendingCreate = null
        else createUncertain.value = true
      }
      error.value = createUncertain.value ? '创建设计稿结果尚未确认，已保留原请求。再次保存将重试同一份内容；也可刷新左侧列表核对。请勿重新创建。' : safeError(cause)
    }
  }
  finally { if (!disposed && current === generation) busy.value = false }
}
function createForm(): void {
  if (!canWrite.value) return
  const suffix = crypto.randomUUID().slice(0, 8)
  const form: StudioFormRevision = { formId: `FORM-${crypto.randomUUID()}`, code: `FORM_${suffix}`, name: '未命名表单', revision: 1, status: 'DRAFT', fields: [] }
  draft.value.forms.push(form); selectedFormKey.value = formKey(form)
}
function copyRevision(): void {
  if (!activeForm.value || !canWrite.value) return
  const form: StudioFormRevision = JSON.parse(JSON.stringify(activeForm.value))
  form.revision = Math.max(...draft.value.forms.filter(item => item.formId === form.formId).map(item => item.revision)) + 1
  form.status = 'DRAFT'; draft.value.forms.push(form); selectedFormKey.value = formKey(form)
}
function freezeRevision(): void {
  if (!activeForm.value || activeForm.value.status === 'FROZEN' || !canWrite.value) return
  if (!activeForm.value.fields.length) { error.value = '请先为表单添加控件，再冻结设计版本。'; return }
  const issues = [...validateFormIdentities(draft.value.forms), ...validateFields(activeForm.value.fields)]
  if (issues.length) { error.value = issues.join('；'); return }
  if (window.confirm('冻结后必须创建新修订才能修改。冻结只是设计快照，不是发布执行。确定冻结？')) activeForm.value.status = 'FROZEN'
}
function updateFields(fields: StudioField[]): void { if (activeForm.value?.status === 'DRAFT' && canWrite.value) activeForm.value.fields = fields }
async function loadLegacy(): Promise<void> {
  if (legacyLoading.value) return
  const current = generation; legacyLoading.value = true
  try {
    let forms: LegacyForm[]
    if (props.serviceCatalogItemId) {
      if (props.serviceManaged) forms = [await serviceCatalogAdminApi.get(props.serviceCatalogItemId)]
      else {
        const result = await catalogApi.getPublishedForm(props.serviceCatalogItemId)
        if (result.source !== 'api') throw new Error('Published form unavailable')
        forms = [{ id: result.data.serviceCatalogItem.id, name: result.data.serviceCatalogItem.name, fields: result.data.fields }]
      }
    } else {
      const result = await serviceCatalogAdminApi.list()
      forms = result.items.filter(form => form.applicableOrganizationIds.includes(props.organizationId))
    }
    if (!disposed && generation === current) {
      legacyForms.value = forms; legacyId.value = props.serviceCatalogItemId ? forms[0]?.id ?? '' : ''
      if (!forms.length) notice.value = '当前范围没有可复制的旧表单，可直接添加表单控件。'
    }
  }
  catch (cause) { if (!disposed && generation === current) error.value = safeError(cause) }
  finally { if (!disposed && generation === current) legacyLoading.value = false }
}
function importLegacy(): void {
  const legacy = legacyForms.value.find(item => item.id === legacyId.value)
  if (!legacy || !canWrite.value) return
  const controls: Record<string, ControlId> = { TEXT: 'text', LONG_TEXT: 'textarea', TEXTAREA: 'textarea', NUMBER: 'number', DATE: 'date', SINGLE_SELECT: 'select', RADIO: 'select', MULTI_SELECT: 'multiselect', CHECKBOX_GROUP: 'multiselect', DATETIME: 'datetime', BOOLEAN: 'boolean', ERROR_CODE: 'text', TAGS: 'tags', CI_REFERENCE: 'ci', RICH_TEXT: 'richtext' }
  const fields: StudioField[] = legacy.fields.map(field => ({ id: `FIELD-${crypto.randomUUID()}`, code: field.code, label: field.label, control: controls[field.type], controlVersion: 1, required: field.required, sensitive: false, helpText: field.helpText ?? '', options: [], ...(field.dictionaryCode ? { dictionaryCode: field.dictionaryCode } : {}) }))
  if (fields.some(field => !field.control)) { error.value = '旧表单存在未注册控件，无法自动导入。'; return }
  const form: StudioFormRevision = { formId: `FORM-${crypto.randomUUID()}`, code: `IMPORTED_${crypto.randomUUID().slice(0, 8)}`, name: `${legacy.name}（导入副本）`, revision: 1, status: 'DRAFT', fields }
  draft.value.forms.push(form); selectedFormKey.value = formKey(form)
  notice.value = '已复制控件元数据；旧目录不变。默认值、条件和敏感策略未自动迁移，请重新核对后保存。'
}
async function importXml(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''
  if (!file || !canWrite.value || busy.value || createUncertain.value) return
  if (file.size > 512 * 1024) { error.value = 'BPMN 文件最大 512 KiB。'; return }
  const current = generation, epoch = documentEpoch.value
  busy.value = true
  try {
    await captureCanvas()
    const previousXml = draft.value.bpmnXml
    const xml = await file.text()
    if (disposed || generation !== current || epoch !== documentEpoch.value) return
    if (dirty.value && !window.confirm('导入会替换当前流程图（表单保留）。确定继续？')) return
    if (!canvas.value || !(await canvas.value.importXml(xml))) {
      if (!disposed && generation === current && epoch === documentEpoch.value) { await canvas.value?.importXml(previousXml); error.value = 'BPMN 导入失败，已保留原图。文件可能不受支持或含不安全扩展。' }
      return
    }
    if (disposed || generation !== current || epoch !== documentEpoch.value) return
    draft.value.bpmnXml = xml; notice.value = '流程已导入设计画布，请检查节点及表单绑定。'
  } catch { if (!disposed && generation === current && epoch === documentEpoch.value) error.value = '无法导入 BPMN 文件，原设计内容保留。' }
  finally { if (!disposed && generation === current && epoch === documentEpoch.value) busy.value = false }
}
async function exportXml(): Promise<void> {
  const current = generation
  try {
    await captureCanvas()
    if (disposed || generation !== current) return
    const url = URL.createObjectURL(new Blob([draft.value.bpmnXml], { type: 'application/xml' }))
    const link = document.createElement('a'); link.href = url; link.download = 'servicehub-design.bpmn'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch { error.value = '流程尚未就绪，无法导出。' }
}
watch(() => JSON.stringify([props.systemCode, props.organizationId, props.serviceCatalogItemId, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId]), () => {
  generation++; listGeneration++; applyDraft(emptyDraft()); draftList.value = []; legacyForms.value = []; error.value = ''; notice.value = ''; busy.value = false; loadingList.value = false
  activeTab.value = 'forms'; showPreview.value = false
  if (contextReady.value && canRead.value && !session.loading) void refreshList()
}, { immediate: true, flush: 'sync' })
watch(() => JSON.stringify([session.loading, session.authorization, session.source]), () => {
  if (session.loading) return
  if (!canRead.value) { error.value = '当前权限已变化，编辑内容保留在本页，但不能读取或保存设计。'; return }
  if (createUncertain.value) { error.value = '身份能力已刷新，原创建请求及幂等键仍保留；再次保存只会重试原请求。'; return }
  if (!dirty.value) void refreshList()
}, { immediate: true, flush: 'sync' })
watch(() => [dirty.value, busy.value, createUncertain.value] as const, ([dirty, busy, uncertain]) => emit('state-change', { dirty, busy, uncertain }), { immediate: true, flush: 'sync' })
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { disposed = true; generation++; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section class="design-studio">
    <div class="studio-context-header"><div><small>{{ systemName }}{{ serviceName ? ` / ${serviceName}` : ' / 新工单配置' }}</small><h3>配置表单与流程</h3><p>在当前系统内维护申请表单、节点表单与 BPMN，不离开系统配置。</p></div><button type="button" :disabled="busy" @click="emit('close')">← 返回工单服务</button></div>
    <p v-if="!canRead || !contextReady" class="form-alert form-alert--error">请先保存或选择一个有权配置的系统，再进入表单与流程配置。</p>
    <template v-else>
      <p class="studio-boundary">当前可保存与预览设计，不会改变已发布服务或正在运行的工单。自由流程的多表单审批运行将在发布包与任务数据权限接入后开放。</p>
      <p v-if="notice" class="form-alert form-alert--success" role="status">{{ notice }}</p>
      <p v-if="error" class="form-alert form-alert--error" role="alert">{{ error }}</p>
      <div class="studio-shell">
        <aside class="panel studio-list"><header><h3>{{ serviceCatalogItemId ? '本服务的配置' : '本系统的工单设计' }}</h3><button type="button" :disabled="!canWrite || busy || createUncertain" @click="newDesign">＋ 新建</button></header><button type="button" :disabled="loadingList" @click="refreshList">{{ loadingList ? '加载中…' : '刷新配置' }}</button><p v-if="!loadingList && !scopedDrafts.length">尚无已保存配置，可先新建表单，再配置流程。</p><button v-for="item in scopedDrafts" :key="item.id" class="studio-list-item" :class="{ active: savedId === item.id }" type="button" :disabled="busy" @click="openDraft(item.id)"><b>{{ item.name }}</b><small>草稿 v{{ item.version }}{{ item.serviceCatalogItemId ? ` · ${item.serviceCatalogItemId}` : ' · 系统工单设计' }}</small></button><details v-if="canWrite && assignableDrafts.length" class="studio-legacy"><summary>归入已有设计（{{ assignableDrafts.length }}）</summary><p>仅显示同组织未归属设计，或尚未绑定服务的本系统设计。确认并保存后锁定归属。</p><button v-for="item in assignableDrafts" :key="item.id" class="studio-list-item" type="button" :disabled="busy || createUncertain" @click="openDraft(item.id, true)"><b>{{ item.name }}</b><small>{{ item.systemCode ? '关联到当前服务' : '归入当前系统' }} · v{{ item.version }}</small></button></details></aside>
        <div class="studio-workspace">
          <div class="panel studio-metadata"><label>工单设计名称<input v-model="draft.name" maxlength="120" :disabled="!canWrite || busy || createUncertain" /></label><div class="studio-owner"><small>归属系统 / 服务</small><b>{{ systemName }}</b><span>{{ draft.serviceCatalogItemId ? (serviceName || draft.serviceCatalogItemId) : '系统内独立工单设计' }}</span><small>{{ organizationId }}</small></div><label class="studio-reason">修改原因<input v-model="draft.reason" maxlength="500" placeholder="至少5字，说明此次配置调整" :disabled="!canWrite || busy || createUncertain" /></label><button class="button button--primary" type="button" :disabled="!canWrite || busy" @click="save">{{ busy ? '处理中…' : '保存配置草稿' }}</button></div>
          <nav class="studio-tabs" aria-label="设计工作区"><button v-for="tab in [{code:'workflow',label:'流程设计'},{code:'forms',label:'表单库与版本'},{code:'check',label:'发布检查'}] as const" :key="tab.code" type="button" :class="{active:activeTab===tab.code}" :disabled="busy || createUncertain" @click="changeTab(tab.code)">{{ tab.label }}</button><small>{{ dirty ? '有未保存修改' : savedId ? '已保存' : '新设计尚未保存' }}</small></nav>
          <div v-if="activeTab === 'workflow'" class="studio-flow" :inert="busy || createUncertain">
            <section class="panel studio-canvas"><div class="studio-tools"><input ref="fileInput" type="file" hidden accept=".bpmn,.xml" @change="importXml" /><button type="button" :disabled="!canWrite" @click="fileInput?.click()">导入 BPMN</button><button type="button" @click="exportXml">导出 BPMN</button></div><BpmnCanvas :key="documentEpoch" ref="canvas" :xml="draft.bpmnXml" :read-only="!canWrite" v-on="canvasListeners" /></section>
            <aside class="panel studio-bindings"><h3>节点表单</h3><p v-if="selectedNode">{{ selectedNode.name || selectedNode.id }} · {{ selectedNode.type }}</p><NodeFormBindings v-if="selectedBindableNode" :node-id="selectedBindableNode" :forms="draft.forms" :model-value="draft.nodeBindings" :disabled="!canWrite" @update:model-value="draft.nodeBindings = $event" /><p v-else>请选择开始事件或用户任务，为该节点绑定一张或多张表单修订。</p></aside>
          </div>
          <section v-else-if="activeTab === 'forms'" class="panel studio-forms" :inert="busy || createUncertain">
            <div class="studio-tools"><button type="button" :disabled="!canWrite" @click="createForm">＋ 新建表单</button><button type="button" :disabled="legacyLoading || !canWrite" @click="loadLegacy">读取旧服务表单</button><select v-if="legacyForms.length" v-model="legacyId" aria-label="选择旧服务表单"><option value="">选择要复制的表单</option><option v-for="form in legacyForms" :key="form.id" :value="form.id">{{ form.name }}</option></select><button v-if="legacyForms.length" type="button" :disabled="!legacyId || !canWrite" @click="importLegacy">导入为独立副本</button></div>
            <div class="studio-form-list"><button v-for="form in draft.forms" :key="formKey(form)" :class="{active:selectedFormKey===formKey(form)}" type="button" @click="selectedFormKey=formKey(form)">{{ form.name }} · r{{ form.revision }} · {{ form.status === 'FROZEN' ? '冻结快照' : '草稿' }}</button></div>
            <template v-if="activeForm"><div class="studio-form-meta"><label>表单名称<input v-model="activeForm.name" maxlength="120" :disabled="!canWrite || activeForm.status==='FROZEN'" /></label><label>稳定编码<input v-model="activeForm.code" maxlength="64" :disabled="!canWrite || draft.forms.filter(form=>form.formId===activeForm?.formId).length > 1 || draft.forms.some(form=>form.formId===activeForm?.formId && form.status==='FROZEN')" /></label><button type="button" :disabled="!canWrite" @click="copyRevision">复制为新修订</button><button type="button" :disabled="!canWrite || activeForm.status==='FROZEN'" @click="freezeRevision">冻结设计版本</button><button type="button" @click="showPreview=!showPreview">{{ showPreview ? '返回编辑' : '试填预览' }}</button></div><p v-if="activeForm.status==='FROZEN'" class="studio-boundary">此修订为不可变设计快照；修改请复制为新修订。冻结不等于发布运行。</p><FormPreview v-if="showPreview" :key="formKey(activeForm)" :fields="activeForm.fields" /><FormDesigner v-else :key="formKey(activeForm)" :model-value="activeForm.fields" :disabled="!canWrite || activeForm.status==='FROZEN'" @update:model-value="updateFields" /></template>
            <p v-else>先新建表单，或从旧服务表单复制。不同表单可以各自维护修订并绑定到同一节点。</p>
          </section>
          <section v-else class="panel studio-check" :inert="busy || createUncertain"><h3>设计与运行能力检查</h3><ul><li>独立表单修订：{{ draft.forms.length }} 份；节点表单绑定：{{ draft.nodeBindings.length }} 条。</li><li>{{ unresolvedBindings.length ? `发现 ${unresolvedBindings.length} 条失效引用，保存前必须修正。` : '当前已加载图形中的绑定引用可解析。' }}</li><li>未接入自由 BPMN 的发布审批、运行表单提交与并行任务推进。</li><li>当前执行模式：DRAFT_ONLY。不能发布到现有工单引擎。</li></ul><div v-for="binding in unresolvedBindings" :key="`${binding.nodeId}-${binding.formId}-${binding.formRevision}`"><span>{{ binding.nodeId }} → {{ binding.formId }} r{{ binding.formRevision }}</span><button type="button" :disabled="!canWrite" @click="draft.nodeBindings=draft.nodeBindings.filter(item=>item!==binding)">移除失效绑定</button></div><button class="button button--secondary" type="button" disabled>发布并启用（运行接入未完成）</button></section>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.studio-context-header { display:flex;justify-content:space-between;gap:16px;align-items:center;padding:14px 0; }.studio-context-header h3{margin:6px 0;font-size:17px;color:#2d557d}.studio-context-header small,.studio-context-header p{font-size:12px;color:#71859a}.studio-context-header button{flex-shrink:0}.studio-owner{display:grid;gap:4px;font-size:12px;color:#446481}.studio-owner small{font-size:10px;color:#71859a}.studio-legacy{margin-top:16px;padding-top:12px;border-top:1px solid #e1e8ef}.studio-legacy summary{cursor:pointer;font-size:12px;color:#3979b4}
.studio-boundary { color:#6d6141; background:#fff9e9; border:1px solid #eadbb6; border-radius:5px; padding:10px 12px; font-size:12px; line-height:1.7; }
.studio-shell { display:grid; grid-template-columns:210px minmax(0,1fr); gap:12px; margin-top:12px; align-items:start; }
.studio-list header { display:flex; justify-content:space-between; align-items:center; }.studio-list h3{font-size:14px}.studio-list p,.studio-bindings p,.studio-forms>p{font-size:12px;color:#71859a;line-height:1.7}
.design-studio button { border:1px solid #d4e0ec; border-radius:4px; padding:7px 10px; color:#34618b; background:#fff; }.design-studio button:disabled{opacity:.55;cursor:not-allowed}.design-studio button.button--primary{background:#176fc1;color:#fff}
.studio-list-item{display:grid;text-align:left;gap:5px;width:100%;margin-top:9px;overflow-wrap:anywhere}.studio-list-item small{font-size:10px;color:#8695a6}.design-studio button.active{border-color:#3685ca;background:#edf6ff;color:#1769ad}
.studio-workspace{min-width:0}.studio-metadata{display:flex;flex-wrap:wrap;align-items:end;gap:10px}.studio-metadata label,.studio-form-meta label{display:grid;gap:5px;color:#667e94;font-size:11px}.studio-reason{flex:1}.design-studio input,.design-studio select{min-width:0;max-width:100%;padding:7px 8px;border:1px solid #d7e3ee;border-radius:4px;background:white;color:#385a77;font-size:12px}
.studio-tabs{display:flex;gap:7px;align-items:center;margin:12px 0}.studio-tabs small{margin-left:auto;color:#7a8ea3;font-size:11px}.studio-flow{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:12px;align-items:start}.studio-canvas{padding:10px;min-width:0}.studio-tools,.studio-form-meta{display:flex;align-items:center;flex-wrap:wrap;gap:7px;margin-bottom:12px}.studio-bindings h3{font-size:14px}.studio-form-list{display:flex;gap:7px;flex-wrap:wrap;margin:12px 0}.studio-check{font-size:13px;line-height:1.9}.studio-check>div{display:flex;gap:10px;justify-content:space-between;padding:8px;background:#fff7f4;margin:8px 0}
@media(max-width:1280px){.studio-shell{grid-template-columns:180px minmax(0,1fr)}.studio-flow{grid-template-columns:1fr}.studio-bindings{max-width:100%}}@media(max-width:760px){.studio-shell{grid-template-columns:1fr}.studio-list{max-height:220px;overflow:auto}.studio-tabs{flex-wrap:wrap}.studio-tabs small{margin-left:0}}
</style>
