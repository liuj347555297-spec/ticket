<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketActionCode, type TicketAvailableAction, type TicketProcessingDetails, type TicketProcessingDetailsInput } from '@/api/tickets'
import { normalizeProcessingText, prepareProcessingNote, type ProcessingActionPayload, type ProcessingNoteField } from '@/utils/processingNotes'

const props = defineProps<{ ticket: Ticket; actions: TicketAvailableAction[]; disabled: boolean; assigneeName?: string }>()
const emit = defineEmits<{ 'dirty-change': [dirty: boolean]; 'state-change': [state: { editable: boolean; saving: boolean }] }>()
const emptyForm = (): TicketProcessingDetailsInput => ({ eventSource: undefined, proposingOrganization: '', onSiteSupportRequired: undefined, causeCategory: undefined, processingDescription: '', resolutionDescription: '', thirdPartyHandled: undefined, currentProgress: '' })
const form = ref<TicketProcessingDetailsInput>(emptyForm())
const detailVersion = ref(0)
const serverEditable = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const errorSummary = ref<HTMLDivElement>()
const errorField = ref<ProcessingNoteField>('processing')
const savedFingerprint = ref('')
let generation = 0

const eventSources = [
  { value: 'PHONE', label: '电话' }, { value: 'EMAIL', label: '邮件' },
  { value: 'MONITORING_ALERT', label: '监控告警' }, { value: 'ON_SITE_FEEDBACK', label: '现场反馈' }, { value: 'OTHER', label: '其他' },
] as const
const causeCategories = [
  { value: 'UNDER_INVESTIGATION', label: '待分析' }, { value: 'SOFTWARE_DEFECT', label: '软件缺陷' },
  { value: 'CONFIGURATION', label: '配置问题' }, { value: 'NETWORK', label: '网络问题' },
  { value: 'ACCESS_CONTROL', label: '权限问题' }, { value: 'DATA', label: '数据问题' },
  { value: 'HARDWARE', label: '硬件问题' }, { value: 'USER_OPERATION', label: '操作问题' }, { value: 'EXTERNAL_DEPENDENCY', label: '外部依赖' },
] as const
const validActions = computed(() => props.actions.filter((action) => !action.disabledReason))
const editable = computed(() => serverEditable.value && !props.disabled && !loading.value)
const fingerprint = computed(() => JSON.stringify({
  ...form.value,
  proposingOrganization: form.value.proposingOrganization?.trim() || undefined,
  processingDescription: normalizeProcessingText(form.value.processingDescription ?? '') || undefined,
  resolutionDescription: normalizeProcessingText(form.value.resolutionDescription ?? '') || undefined,
  currentProgress: normalizeProcessingText(form.value.currentProgress ?? '') || undefined,
}))
const dirty = computed(() => !loading.value && fingerprint.value !== savedFingerprint.value)
const processingLength = computed(() => normalizeProcessingText(form.value.processingDescription ?? '').length)
const resolutionLength = computed(() => normalizeProcessingText(form.value.resolutionDescription ?? '').length)

watch(dirty, (value) => emit('dirty-change', value), { immediate: true })
watch([editable, saving], () => emit('state-change', { editable: editable.value, saving: saving.value }), { immediate: true })
watch(() => props.ticket.id, load, { immediate: true })
watch(fingerprint, (value) => { if (value !== savedFingerprint.value) { error.value = ''; notice.value = '' } })

function applyDetails(details: TicketProcessingDetails): void {
  form.value = {
    eventSource: details.eventSource, proposingOrganization: details.proposingOrganization ?? '',
    onSiteSupportRequired: details.onSiteSupportRequired, causeCategory: details.causeCategory,
    processingDescription: details.processingDescription ?? '', resolutionDescription: details.resolutionDescription ?? '',
    thirdPartyHandled: details.thirdPartyHandled, currentProgress: details.currentProgress ?? '',
  }
  detailVersion.value = details.version
  serverEditable.value = details.editable
  savedFingerprint.value = fingerprint.value
}

async function load(): Promise<void> {
  const request = ++generation
  loading.value = true; error.value = ''; notice.value = ''; serverEditable.value = false
  try {
    const details = await ticketApi.getProcessingDetails(props.ticket.id)
    if (request !== generation) return
    applyDetails(details)
  } catch {
    if (request === generation) { form.value = emptyForm(); detailVersion.value = 0; savedFingerprint.value = fingerprint.value; error.value = '处理信息暂时无法读取，请稍后重试。' }
  } finally { if (request === generation) loading.value = false }
}

function validate(): boolean {
  if ((form.value.proposingOrganization?.trim().length ?? 0) > 160 || processingLength.value > 4000 || resolutionLength.value > 4000 || (form.value.currentProgress?.trim().length ?? 0) > 1000) {
    error.value = '处理信息超过允许长度，请精简后保存。'
    void nextTick(() => errorSummary.value?.focus())
    return false
  }
  return true
}

async function saveDetails(): Promise<boolean> {
  if (!editable.value || saving.value || !validate()) return false
  const request = generation
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const saved = await ticketApi.saveProcessingDetails(props.ticket.id, detailVersion.value, form.value)
    if (request !== generation) return false
    applyDetails(saved); notice.value = '处理信息已保存。'; return true
  } catch (cause) {
    if (request === generation) error.value = cause instanceof ApiError && cause.status === 409 ? '处理信息已被其他人员更新，请重新加载后核对。' : '处理信息未确认保存，当前填写已保留。'
    return false
  } finally { if (request === generation) saving.value = false }
}

function prepareAction(code: TicketActionCode): ProcessingActionPayload | null {
  if (props.disabled || !validActions.value.some((action) => action.code === code)) {
    errorField.value = code === 'RESOLVE' ? 'resolution' : 'processing'
    error.value = props.actions.find((action) => action.code === code)?.disabledReason || '当前工单或身份暂不可执行此操作，请刷新后查看可用操作。'
    void nextTick(() => errorSummary.value?.focus()); return null
  }
  if ((code === 'REQUEST_USER_FEEDBACK' || code === 'RESOLVE') && !normalizeProcessingText(form.value.resolutionDescription ?? '')) {
    errorField.value = 'resolution'; error.value = '请先填写并保存解决说明，再提交解决或验证结果。'
    void nextTick(() => errorSummary.value?.focus()); return null
  }
  if (code === 'RESOLVE') return { reason: '已核对保存的解决说明，确认问题已解决', detail: '' }
  const result = prepareProcessingNote(code, form.value.processingDescription ?? '', '', form.value.resolutionDescription ?? '')
  if (!result.ok) { error.value = result.message; errorField.value = result.field; void nextTick(() => errorSummary.value?.focus()); return null }
  return result.payload
}

function acknowledgeAction(_code: TicketActionCode): void { error.value = '' }
function resetDrafts(): void { generation++; form.value = emptyForm(); detailVersion.value = 0; serverEditable.value = false; savedFingerprint.value = fingerprint.value; error.value = ''; notice.value = '' }
function insertReference(reference: { id: string; title: string; url: string }): void {
  if (!editable.value || !reference.id || !reference.title.trim() || !reference.url.startsWith('/knowledge/')) return
  const citation = `[参考知识：${reference.title.trim()}](${reference.url})`
  const current = normalizeProcessingText(form.value.processingDescription ?? '')
  if (!current.includes(citation)) form.value.processingDescription = current ? `${current}\n${citation}` : citation
}

defineExpose({ prepareAction, acknowledgeAction, resetDrafts, saveDetails, insertReference, load })
</script>

<template>
  <div class="processing-form">
    <div v-if="error" ref="errorSummary" class="processing-form__error" role="alert" tabindex="-1"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
    <p v-if="notice" class="form-alert form-alert--success" role="status">{{ notice }}</p>
    <p v-if="loading" class="workflow-unavailable" role="status">正在读取处理信息…</p>
    <template v-else>
      <section class="processing-form__section" aria-labelledby="processing-confirm-heading">
        <h3 id="processing-confirm-heading">事件信息确认</h3>
        <div class="processing-form__readonly"><span class="processing-form__label">当前处理人</span><span>{{ assigneeName || '尚未领取' }}</span><span class="processing-form__label">保存版本</span><span>v{{ detailVersion }}</span></div>
        <div class="processing-form__grid">
          <label><span>事件来源</span><el-select v-model="form.eventSource" :disabled="!editable" clearable placeholder="请选择来源"><el-option v-for="item in eventSources" :key="item.value" :label="item.label" :value="item.value" /></el-select></label>
          <label><span>提出单位</span><el-input v-model="form.proposingOrganization" :disabled="!editable" maxlength="160" placeholder="填写提出事件的单位" /></label>
          <label><span>是否需要现场支持</span><el-radio-group v-model="form.onSiteSupportRequired" :disabled="!editable"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group></label>
          <label><span>原因分类</span><el-select v-model="form.causeCategory" :disabled="!editable" clearable placeholder="请选择原因分类"><el-option v-for="item in causeCategories" :key="item.value" :label="item.label" :value="item.value" /></el-select></label>
          <label><span>是否由第三方处理</span><el-radio-group v-model="form.thirdPartyHandled" :disabled="!editable"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group></label>
          <label><span>当前进展</span><el-input v-model="form.currentProgress" :disabled="!editable" maxlength="1000" placeholder="记录定位、修复或等待情况" /></label>
        </div>
      </section>
      <section class="processing-form__section" aria-labelledby="processing-notes-heading">
        <h3 id="processing-notes-heading">处理信息</h3>
        <div class="processing-form__field-heading"><label for="processing-description">处理说明</label><span :class="{ 'is-over-limit': processingLength > 4000 }">{{ processingLength }} / 4000</span></div>
        <el-input id="processing-description" v-model="form.processingDescription" :disabled="!editable" type="textarea" :rows="7" maxlength="4000" placeholder="记录排查过程、已采取措施和下一步安排；可从右侧知识库引用条目。" />
        <div class="processing-form__field-heading"><label for="resolution-description">解决说明</label><span :class="{ 'is-over-limit': resolutionLength > 4000 }">{{ resolutionLength }} / 4000</span></div>
        <el-input id="resolution-description" v-model="form.resolutionDescription" :disabled="!editable" type="textarea" :rows="6" maxlength="4000" placeholder="解决前填写方法、结果和验证依据。" />
      </section>
      <p v-if="!serverEditable" class="processing-form__storage-note">当前仅可查看。共享队列领取后，当前主办人才能编辑和保存处理信息。</p>
      <p v-else class="processing-form__storage-note">“保存”只保存处理信息，不推进流程；提交、解决、关闭等动作仍由底部真实流程按钮执行。</p>
    </template>
  </div>
</template>

<style scoped>
.processing-form { min-width: 0; }
.processing-form__section + .processing-form__section { margin-top: 22px; }
.processing-form__section h3 { margin: 0 0 13px; border-bottom: 2px solid #648eda; padding: 0 8px 7px; color: #315b91; font-size: 13px; text-align: center; }
.processing-form__readonly { display: grid; grid-template-columns: auto minmax(100px, 1fr) auto minmax(100px, 1fr); align-items: baseline; gap: 10px; margin-bottom: 16px; font-size: 12px; }
.processing-form__label { color: #7b8a9e; }
.processing-form__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 18px; }
.processing-form__grid > label { display: grid; grid-template-columns: 128px minmax(0, 1fr); align-items: center; gap: 10px; min-width: 0; color: #526b83; font-size: 12px; }
.processing-form__field-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 12px 0 7px; color: #526b83; font-size: 12px; }
.processing-form__field-heading > span { color: #8290a3; font-size: 11px; }
.processing-form__field-heading > .is-over-limit { color: #c23b3b; }
.processing-form__storage-note { margin: 16px 0 0; padding: 10px 12px; border-radius: 4px; color: #758498; background: #f5f8fc; font-size: 11px; line-height: 1.65; }
.processing-form__error { display: flex; gap: 12px; justify-content: space-between; padding: 10px 12px; margin-bottom: 15px; border: 1px solid #efc7c7; background: #fff5f4; color: #a62c2c; font-size: 12px; }
.processing-form__error button { flex-shrink: 0; border: 0; background: transparent; color: #a62c2c; text-decoration: underline; cursor: pointer; }
@media (max-width: 720px) {
  .processing-form__readonly, .processing-form__grid { grid-template-columns: 1fr; }
  .processing-form__grid > label { grid-template-columns: 1fr; gap: 5px; }
  .processing-form__error { flex-direction: column; }
}
</style>
