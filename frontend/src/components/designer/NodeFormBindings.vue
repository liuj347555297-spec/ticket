<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { NodeFormBinding, StudioFormRevision } from '@/api/designer'
import { moveItem, replaceNodeBindings, validateNodeBindings } from '@/forms/registry'

const props = withDefaults(defineProps<{ nodeId: string; forms: StudioFormRevision[]; modelValue: NodeFormBinding[]; disabled?: boolean }>(), { disabled: false })
const emit = defineEmits<{ 'update:modelValue': [bindings: NodeFormBinding[]] }>()
const selectedForm = ref('')
const bindingKey = (form: StudioFormRevision): string => JSON.stringify([form.formId, form.revision])
const nodeBindings = computed(() => props.modelValue.filter(binding => binding.nodeId === props.nodeId).sort((a, b) => a.displayOrder - b.displayOrder))
const availableForms = computed(() => props.forms.filter(form => !nodeBindings.value.some(binding => binding.formId === form.formId)))
const errors = computed(() => validateNodeBindings(nodeBindings.value, props.forms))
watch(() => props.nodeId, () => { selectedForm.value = '' })
function title(binding: NodeFormBinding): string { return props.forms.find(form => form.formId === binding.formId && form.revision === binding.formRevision)?.name ?? `缺失表单 ${binding.formId}` }
function publish(bindings: NodeFormBinding[]): void { if (!props.disabled && props.nodeId) emit('update:modelValue', replaceNodeBindings(props.modelValue, props.nodeId, bindings)) }
function add(): void {
  const form = availableForms.value.find(form => bindingKey(form) === selectedForm.value)
  if (!form || props.disabled || !props.nodeId) return
  publish([...nodeBindings.value, { nodeId: props.nodeId, formId: form.formId, formRevision: form.revision, displayOrder: nodeBindings.value.length + 1, mode: 'EDIT', requiredOnComplete: false }])
  selectedForm.value = ''
}
function update(index: number, change: Partial<NodeFormBinding>): void { publish(nodeBindings.value.map((binding, i) => i === index ? { ...binding, ...change } : binding)) }
function setMode(index: number, event: Event): void { const mode = (event.target as HTMLSelectElement).value === 'READ_ONLY' ? 'READ_ONLY' : 'EDIT'; update(index, { mode, ...(mode === 'READ_ONLY' ? { requiredOnComplete: false } : {}) }) }
</script>

<template>
  <section class="node-form-bindings">
    <header><h3>节点多表单</h3><p v-if="nodeId">当前节点 <code>{{ nodeId }}</code> · 绑定明确修订，后续新修订不会自动替换。</p><p v-else>请在 BPMN 画布中选择一个开始节点或用户任务节点。</p></header>
    <template v-if="nodeId">
      <div class="node-form-bindings__add"><select v-model="selectedForm" :disabled="disabled || !availableForms.length" aria-label="选择表单修订"><option value="">{{ availableForms.length ? '选择要绑定的表单修订' : '暂无可新增的表单' }}</option><option v-for="form in availableForms" :key="bindingKey(form)" :value="bindingKey(form)">{{ form.name }} · r{{ form.revision }} · {{ form.status === 'FROZEN' ? '已冻结' : '草稿' }}</option></select><button type="button" :disabled="disabled || !selectedForm" @click="add">添加</button></div>
      <p class="node-form-bindings__hint">同一节点可绑定多张不同表单；每张表单仅选择一个修订，避免同名字段混淆。</p>
      <ul v-if="errors.length" class="node-form-bindings__errors" role="alert"><li v-for="error in errors" :key="error">{{ error }}</li></ul>
      <article v-for="(binding, index) in nodeBindings" :key="`${binding.formId}:${binding.formRevision}`" class="node-form-bindings__card">
        <div class="node-form-bindings__title"><b>{{ index + 1 }}. {{ title(binding) }}</b><span>r{{ binding.formRevision }}</span></div>
        <label>访问模式<select :value="binding.mode" :disabled="disabled" :aria-label="`${title(binding)}访问模式`" @change="setMode(index, $event)"><option value="EDIT">可填写</option><option value="READ_ONLY">只读参考</option></select></label>
        <label class="node-form-bindings__check"><input type="checkbox" :checked="binding.requiredOnComplete" :disabled="disabled || binding.mode === 'READ_ONLY'" @change="update(index, { requiredOnComplete: ($event.target as HTMLInputElement).checked })" />节点完成时必须提交</label>
        <div class="node-form-bindings__actions"><button type="button" :disabled="disabled || index === 0" :aria-label="`上移${title(binding)}`" @click="publish(moveItem(nodeBindings, index, index - 1))">↑ 上移</button><button type="button" :disabled="disabled || index === nodeBindings.length - 1" :aria-label="`下移${title(binding)}`" @click="publish(moveItem(nodeBindings, index, index + 1))">↓ 下移</button><button type="button" :disabled="disabled" class="is-danger" :aria-label="`移除${title(binding)}绑定`" @click="publish(nodeBindings.filter((_, i) => i !== index))">移除绑定</button></div>
      </article>
      <p v-if="!nodeBindings.length" class="node-form-bindings__empty">此节点尚未绑定表单。添加后可配置填写顺序与访问模式。</p>
      <p class="node-form-bindings__hint">这里保存设计绑定，不会改变已运行工单，也不会立即启用审批。</p>
    </template>
  </section>
</template>

<style scoped>
.node-form-bindings { min-width: 0; color: #354b66; }
.node-form-bindings h3 { margin: 0 0 8px; font-size: 14px; }
.node-form-bindings header p, .node-form-bindings__hint, .node-form-bindings__empty { margin: 8px 0 14px; color: #7689a0; font-size: 11px; line-height: 1.65; }
.node-form-bindings code { overflow-wrap: anywhere; }
.node-form-bindings__add { display: flex; gap: 7px; }
.node-form-bindings select { width: 100%; min-width: 0; border: 1px solid #cdd9e8; border-radius: 4px; padding: 8px; background: #fff; color: #405b7b; }
.node-form-bindings button { flex-shrink: 0; padding: 7px 10px; border: 1px solid #ccd9e8; border-radius: 4px; background: #fff; color: #3264a4; cursor: pointer; font-size: 12px; }
.node-form-bindings button:disabled { opacity: .45; cursor: not-allowed; }
.node-form-bindings__card { border: 1px solid #dae3ee; border-radius: 5px; padding: 12px; margin: 10px 0; }
.node-form-bindings__title { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 12px; font-size: 13px; }
.node-form-bindings__title span { color: #8091a6; }
.node-form-bindings__card > label { display: flex; gap: 10px; align-items: center; font-size: 12px; margin: 10px 0; }
.node-form-bindings__card > label:not(.node-form-bindings__check) { flex-direction: column; align-items: stretch; }
.node-form-bindings__actions { display: flex; flex-wrap: wrap; gap: 6px; }
.node-form-bindings__actions .is-danger { color: #a74848; margin-left: auto; }
.node-form-bindings__errors { padding: 9px 9px 9px 25px; border: 1px solid #e9c1bd; background: #fff5f3; color: #a23d36; font-size: 12px; line-height: 1.6; }
</style>
