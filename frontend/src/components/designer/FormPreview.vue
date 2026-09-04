<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { StudioField } from '@/api/designer'
import TicketRichTextEditor from '@/components/TicketRichTextEditor.vue'
import { findControl } from '@/forms/registry'

const props = withDefaults(defineProps<{ fields: StudioField[]; readonly?: boolean }>(), { readonly: false })
const values = reactive<Record<string, string | string[] | boolean>>(Object.create(null))
const instanceId = `form-preview-${crypto.randomUUID()}`
const previousTypes = new Map<string, string>()
watch(() => props.fields, fields => {
  const activeIds = new Set(fields.map(field => field.id))
  Object.keys(values).forEach(id => { if (!activeIds.has(id)) { delete values[id]; previousTypes.delete(id) } })
  fields.forEach(field => {
    if (!(field.id in values) || previousTypes.get(field.id) !== field.control) values[field.id] = field.control === 'multiselect' ? [] : field.control === 'boolean' ? false : ''
    previousTypes.set(field.id, field.control)
  })
}, { immediate: true, deep: true })
function textValue(id: string): string { return typeof values[id] === 'string' ? values[id] as string : '' }
function inputValue(event: Event): string { return (event.target as HTMLInputElement).value }
function selectedValues(event: Event): string[] { return Array.from((event.target as HTMLSelectElement).selectedOptions).map(option => option.value) }
function resetValues(): void { props.fields.forEach(field => { values[field.id] = field.control === 'multiselect' ? [] : field.control === 'boolean' ? false : '' }) }
</script>

<template>
  <div class="form-preview">
    <div class="form-preview__notice"><span>{{ readonly ? '只读展示' : '试填预览' }} · 填写值仅保留在当前组件内存，不保存到设计稿，不提交工单。</span><button v-if="!readonly" type="button" @click="resetValues">清空试填</button></div>
    <p v-if="!fields.length" class="form-preview__empty">还没有表单控件，请从左侧控件库添加。</p>
    <div v-for="field in fields" :key="field.id" class="form-preview__field">
      <p v-if="!findControl(field.control, field.controlVersion)" role="alert" class="form-preview__unsupported">{{ field.label }}：未注册控件或版本，已阻止渲染。</p>
      <template v-else-if="field.control === 'section'"><h4>{{ field.label }}</h4><small v-if="field.helpText">{{ field.helpText }}</small></template>
      <template v-else>
        <label :for="`${instanceId}-${field.id}`">{{ field.label }} <span v-if="field.required" class="form-preview__required">*</span><span v-if="field.sensitive" class="form-preview__sensitive">敏感字段</span></label>
        <div v-if="findControl(field.control)?.managed" class="form-preview__managed"><b>{{ findControl(field.control)?.label }} · 受管控件</b><span>{{ findControl(field.control)?.description }}</span><small>当前仅展示设计占位，不加载真实业务数据。</small></div>
        <div v-else-if="field.dictionaryCode" class="form-preview__managed"><b>受控字典：{{ field.dictionaryCode }}</b><span>实际选项由服务端按表单、版本、字段和组织范围提供；设计预览不请求真实字典。</span></div>
        <TicketRichTextEditor v-else-if="field.control === 'richtext'" :model-value="textValue(field.id)" :disabled="readonly" :allow-images="false" :aria-label="field.label" @update:model-value="values[field.id] = $event" />
        <textarea v-else-if="field.control === 'textarea'" :id="`${instanceId}-${field.id}`" :value="textValue(field.id)" :disabled="readonly" rows="3" maxlength="4000" :aria-required="field.required" @input="values[field.id] = inputValue($event)" />
        <select v-else-if="field.control === 'select'" :id="`${instanceId}-${field.id}`" :value="values[field.id]" :disabled="readonly" :aria-required="field.required" @change="values[field.id] = inputValue($event)"><option value="">请选择</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select>
        <select v-else-if="field.control === 'multiselect'" :id="`${instanceId}-${field.id}`" :value="values[field.id]" :disabled="readonly" multiple :aria-required="field.required" @change="values[field.id] = selectedValues($event)"><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select>
        <label v-else-if="field.control === 'boolean'" class="form-preview__boolean"><input :id="`${instanceId}-${field.id}`" type="checkbox" :checked="values[field.id] === true" :disabled="readonly" @change="values[field.id] = ($event.target as HTMLInputElement).checked" />是</label>
        <input v-else :id="`${instanceId}-${field.id}`" :type="field.control === 'number' ? 'number' : field.control === 'date' ? 'date' : field.control === 'datetime' ? 'datetime-local' : 'text'" :value="textValue(field.id)" :disabled="readonly" :aria-required="field.required" maxlength="4000" @input="values[field.id] = inputValue($event)" />
        <small v-if="field.helpText">{{ field.helpText }}</small>
      </template>
    </div>
  </div>
</template>

<style scoped>
.form-preview { min-width: 0; }
.form-preview__notice { display: flex; gap: 12px; align-items: center; justify-content: space-between; padding: 11px; margin-bottom: 16px; color: #5f738d; background: #f1f6fc; font-size: 12px; line-height: 1.6; }
.form-preview__notice button { flex-shrink: 0; border: 0; background: transparent; color: #2c62a9; cursor: pointer; }
.form-preview__field { margin-bottom: 18px; }
.form-preview__field > label { display: block; margin-bottom: 7px; font-size: 13px; color: #354d69; }
.form-preview__field > input, .form-preview__field > textarea, .form-preview__field > select { box-sizing: border-box; width: 100%; border: 1px solid #cdd8e6; border-radius: 4px; padding: 9px; font: inherit; font-size: 13px; color: #354d69; background: #fff; }
.form-preview__field > :disabled { background: #f5f7fa; color: #7a8b9f; }
.form-preview__field > select[multiple] { min-height: 88px; }
.form-preview__field h4 { margin: 22px 0 8px; padding-bottom: 9px; border-bottom: 2px solid #6999da; color: #365f96; }
.form-preview__field small { display: block; margin-top: 6px; font-size: 11px; color: #73869e; line-height: 1.5; }
.form-preview__field > .form-preview__boolean { display: flex; gap: 7px; align-items: center; }
.form-preview__required { color: #bc4141; }
.form-preview__sensitive { margin-left: 8px; font-size: 11px; color: #996019; background: #fff4de; padding: 2px 5px; border-radius: 3px; }
.form-preview__managed { display: flex; flex-direction: column; gap: 6px; border: 1px dashed #c6d6e9; border-radius: 4px; padding: 12px; background: #f8fafc; color: #687e98; font-size: 12px; line-height: 1.6; }
.form-preview__managed b { font-weight: 500; color: #4c698c; }
.form-preview__empty { color: #7a8b9e; text-align: center; padding: 34px 12px; }
.form-preview__unsupported { color: #a13e3e; padding: 12px; border: 1px solid #e4bcbc; }
</style>
