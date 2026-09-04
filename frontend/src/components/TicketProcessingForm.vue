<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { Ticket, TicketActionCode, TicketAvailableAction } from '@/api/tickets'
import TicketRichTextEditor from '@/components/TicketRichTextEditor.vue'
import { actionSavesProcessingNote, composeResolutionReason, normalizeProcessingText, prepareProcessingNote, resolutionCategories, type ProcessingActionPayload, type ProcessingNoteField } from '@/utils/processingNotes'

const props = defineProps<{ ticket: Ticket; actions: TicketAvailableAction[]; disabled: boolean }>()
const emit = defineEmits<{ 'dirty-change': [dirty: boolean] }>()
const processingHtml = ref('')
const processingText = ref('')
const resolutionHtml = ref('')
const resolutionText = ref('')
const resolutionCategory = ref('')
const processingEditor = ref<InstanceType<typeof TicketRichTextEditor>>()
const resolutionEditor = ref<InstanceType<typeof TicketRichTextEditor>>()
const error = ref('')
const errorSummary = ref<HTMLDivElement>()
const errorField = ref<ProcessingNoteField>('processing')
const validActions = computed(() => props.actions.filter((action) => !action.disabledReason))
const canProcess = computed(() => !props.disabled && validActions.value.some((action) => actionSavesProcessingNote(action.code)))
const canResolve = computed(() => !props.disabled && validActions.value.some((action) => action.code === 'RESOLVE'))
const processingLength = computed(() => normalizeProcessingText(processingText.value).length)
const resolutionLength = computed(() => composeResolutionReason(resolutionCategory.value, resolutionText.value).length)
const dirty = computed(() => Boolean(normalizeProcessingText(processingText.value) || normalizeProcessingText(resolutionText.value) || resolutionCategory.value))
const prepared = new Map<TicketActionCode, { processing: string; resolution: string; category: string }>()

watch(dirty, (value) => emit('dirty-change', value), { immediate: true })
watch([processingText, resolutionText, resolutionCategory], () => { error.value = '' })
watch(() => props.ticket.id, resetDrafts)

function prepareAction(code: TicketActionCode): ProcessingActionPayload | null {
  if (props.disabled || !validActions.value.some((action) => action.code === code)) {
    errorField.value = code === 'RESOLVE' ? 'resolution' : 'processing'
    error.value = props.actions.find((action) => action.code === code)?.disabledReason || '当前工单或身份暂不可执行此操作，请刷新后查看可用操作。'
    void nextTick(() => errorSummary.value?.focus())
    return null
  }
  const result = prepareProcessingNote(code, processingText.value, resolutionCategory.value, resolutionText.value)
  if (!result.ok) {
    error.value = result.message
    errorField.value = result.field
    void nextTick(() => focusError())
    return null
  }
  error.value = ''
  prepared.set(code, { processing: processingHtml.value, resolution: resolutionHtml.value, category: resolutionCategory.value })
  return result.payload
}

function focusError(): void {
  if (errorField.value === 'resolution') resolutionEditor.value?.focus()
  else processingEditor.value?.focus()
}

/** Call only after the server confirms success; newer edits must not be discarded. */
function acknowledgeAction(code: TicketActionCode): void {
  const snapshot = prepared.get(code)
  if (!snapshot) return
  if (code === 'RESOLVE') {
    if (resolutionHtml.value === snapshot.resolution && resolutionCategory.value === snapshot.category) {
      resolutionHtml.value = ''; resolutionText.value = ''; resolutionCategory.value = ''
    }
  } else if (actionSavesProcessingNote(code) && processingHtml.value === snapshot.processing) {
    processingHtml.value = ''; processingText.value = ''
  }
  prepared.delete(code)
  error.value = ''
}

function resetDrafts(): void {
  processingHtml.value = ''; processingText.value = ''
  resolutionHtml.value = ''; resolutionText.value = ''; resolutionCategory.value = ''
  error.value = ''; prepared.clear()
}

defineExpose({ prepareAction, acknowledgeAction, resetDrafts })
</script>

<template>
  <div class="processing-form">
    <div v-if="error" ref="errorSummary" class="processing-form__error" role="alert" tabindex="-1">
      <span>{{ error }}</span><button type="button" @click="focusError">定位填写位置</button>
    </div>
    <section class="processing-form__section" aria-labelledby="processing-notes-heading">
      <h3 id="processing-notes-heading">事件处理信息</h3>
      <div class="processing-form__readonly">
        <span class="processing-form__label">处理人</span><span>{{ ticket.assignee?.displayName || '尚未分配' }}</span>
        <span class="processing-form__label">当前记录方式</span><span>内部协作记录</span>
      </div>
      <div class="processing-form__field-heading"><label>处理意见</label><span :class="{ 'is-over-limit': processingLength > 2000 }">{{ processingLength }} / 2000 字符</span></div>
      <TicketRichTextEditor ref="processingEditor" v-model="processingHtml" :disabled="!canProcess" :allow-images="false" aria-label="处理意见" @plain-text-change="processingText = $event" />
      <p v-if="!canProcess" class="processing-form__hint">当前页面只读或操作提交中，暂不可填写处理意见；已有记录可在协作记录中查看。</p>
      <p v-else class="processing-form__hint">记录排查过程、已采取措施和下一步安排。点击“保存处理意见”保存为内部记录。支持操作原因的动作会带入这里的内容（最多 1000 字符）；认领、转办、恢复、分类、请求用户反馈不保存意见，请另行保存。</p>
    </section>
    <section class="processing-form__section" aria-labelledby="processing-resolution-heading">
      <h3 id="processing-resolution-heading">事件解决信息</h3>
      <div class="processing-form__category"><label for="processing-resolution-category">解决类别</label><el-select id="processing-resolution-category" v-model="resolutionCategory" :disabled="!canResolve" clearable placeholder="可选，请选择解决类别" aria-label="解决类别"><el-option v-for="category in resolutionCategories" :key="category" :label="category" :value="category" /></el-select><span>类别会一并写入解决依据</span></div>
      <div class="processing-form__field-heading"><label>解决说明 <span v-if="canResolve" class="processing-form__required">*</span></label><span :class="{ 'is-over-limit': resolutionLength > 1000 }">{{ resolutionLength }} / 1000 字符（含类别）</span></div>
      <TicketRichTextEditor ref="resolutionEditor" v-model="resolutionHtml" :disabled="!canResolve" :allow-images="false" aria-label="解决说明" @plain-text-change="resolutionText = $event" />
      <p v-if="!canResolve" class="processing-form__hint">当前暂不可解决工单，解决区只读；可用操作由当前状态和身份决定，提交期间也不可编辑。</p>
      <p v-else class="processing-form__hint">请说明解决方法、处理结果及验证情况；填写后点击底部“解决”并确认，不会直接关闭工单。</p>
    </section>
    <p class="processing-form__storage-note">编辑器排版仅用于本页编辑；当前接口按纯文本保存，不支持图片或文件。未提交内容仅保留在本页，离开或切换工单后不保留。</p>
  </div>
</template>

<style scoped>
.processing-form { min-width: 0; }
.processing-form__section + .processing-form__section { margin-top: 22px; }
.processing-form__section h3 { margin: 0 0 13px; border-bottom: 2px solid #648eda; padding: 0 8px 7px; color: #315b91; font-size: 13px; text-align: center; }
.processing-form__readonly { display: grid; grid-template-columns: auto minmax(100px, 1fr) auto minmax(100px, 1fr); align-items: baseline; gap: 10px; margin-bottom: 16px; font-size: 12px; }
.processing-form__label, .processing-form__category > span { color: #7b8a9e; }
.processing-form__field-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 10px 0 7px; font-size: 12px; }
.processing-form__field-heading > span { color: #8290a3; font-size: 11px; }
.processing-form__field-heading > .is-over-limit, .processing-form__required { color: #c23b3b; }
.processing-form__category { display: grid; grid-template-columns: auto minmax(140px, 240px) minmax(100px, 1fr); align-items: center; gap: 12px; font-size: 12px; }
.processing-form__hint, .processing-form__storage-note { margin: 7px 0 0; color: #758498; font-size: 11px; line-height: 1.65; }
.processing-form__storage-note { margin-top: 16px; padding: 10px 12px; background: #f5f8fc; border-radius: 4px; }
.processing-form__error { display: flex; gap: 12px; justify-content: space-between; padding: 10px 12px; margin-bottom: 15px; border: 1px solid #efc7c7; background: #fff5f4; color: #a62c2c; font-size: 12px; }
.processing-form__error button { flex-shrink: 0; border: 0; background: transparent; color: #a62c2c; text-decoration: underline; cursor: pointer; }
.processing-form :deep(.ticket-rich-editor__content) { min-height: 130px; }
.processing-form :deep(.ticket-rich-editor__toolbar) { gap: 5px; }
.processing-form :deep(.ticket-rich-editor__toolbar .el-button) { padding: 5px 7px; }
@media (max-width: 720px) {
  .processing-form__readonly { grid-template-columns: auto minmax(0, 1fr); }
  .processing-form__category { grid-template-columns: auto minmax(0, 1fr); }
  .processing-form__category > span { grid-column: 2; }
  .processing-form__field-heading { flex-wrap: wrap; gap: 4px; }
  .processing-form__error { flex-direction: column; }
  .processing-form__error button { align-self: flex-start; }
}
</style>
