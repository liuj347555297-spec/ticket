<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ControlId, StudioField } from '@/api/designer'
import FormPreview from './FormPreview.vue'
import { controlRegistry, copyField, findControl, moveItem, newField, validateFields } from '@/forms/registry'

const props = withDefaults(defineProps<{ modelValue: StudioField[]; disabled?: boolean }>(), { disabled: false })
const emit = defineEmits<{ 'update:modelValue': [fields: StudioField[]] }>()
const selectedId = ref('')
const preview = ref(false)
const dragOverId = ref('')
const notice = ref('')
const dragSource = ref<{ kind: 'control'; control: ControlId } | { kind: 'field'; id: string }>()
const selectedField = computed(() => props.modelValue.find(field => field.id === selectedId.value))
const selectedControl = computed(() => selectedField.value && findControl(selectedField.value.control, selectedField.value.controlVersion))
const errors = computed(() => validateFields(props.modelValue))
const groups = ['基础控件', '业务控件', '布局'] as const
const propertyPrefix = `field-property-${crypto.randomUUID()}`
watch(() => props.modelValue, fields => {
  if (!fields.some(field => field.id === selectedId.value)) selectedId.value = fields[0]?.id ?? ''
}, { immediate: true })
watch(() => props.disabled, () => { dragSource.value = undefined; dragOverId.value = '' })
function publish(fields: StudioField[]): void { if (!props.disabled) emit('update:modelValue', fields) }
function add(control: ControlId, index = props.modelValue.length): void {
  if (props.disabled || props.modelValue.length >= 100 || !findControl(control)) return
  const field = newField(control, props.modelValue)
  const fields = [...props.modelValue]; fields.splice(index, 0, field)
  publish(fields); selectedId.value = field.id; notice.value = `已添加${field.label}`
}
function update(change: Partial<StudioField>): void {
  if (!selectedField.value || props.disabled) return
  publish(props.modelValue.map(field => field.id === selectedId.value ? { ...field, ...change } : field))
}
function inputValue(event: Event): string { return (event.target as HTMLInputElement).value }
function checked(event: Event): boolean { return (event.target as HTMLInputElement).checked }
function copy(field: StudioField): void {
  if (props.disabled || props.modelValue.length >= 100) return
  const duplicate = copyField(field, props.modelValue), fields = [...props.modelValue]
  fields.splice(fields.findIndex(item => item.id === field.id) + 1, 0, duplicate)
  publish(fields); selectedId.value = duplicate.id; notice.value = `已复制${field.label}`
}
function remove(field: StudioField): void {
  publish(props.modelValue.filter(item => item.id !== field.id)); notice.value = `已移除${field.label}，保存设计包后才会持久化`
}
function move(index: number, target: number): void { publish(moveItem(props.modelValue, index, target)); notice.value = `字段已移动至第 ${target + 1} 位` }
function startControlDrag(event: DragEvent, control: ControlId): void {
  if (props.disabled) { event.preventDefault(); return }
  dragSource.value = { kind: 'control', control }
  event.dataTransfer?.setData('text/plain', control)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}
function startFieldDrag(event: DragEvent, id: string): void {
  if (props.disabled) { event.preventDefault(); return }
  dragSource.value = { kind: 'field', id }; selectedId.value = id
  event.dataTransfer?.setData('text/plain', id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
function allowDrop(event: DragEvent, id = 'end'): void {
  if (props.disabled || !dragSource.value) return
  event.preventDefault(); dragOverId.value = id
}
function drop(event: DragEvent, target: number): void {
  event.preventDefault(); event.stopPropagation()
  if (props.disabled || !dragSource.value) return
  const source = dragSource.value
  if (source.kind === 'control') add(source.control, target)
  else {
    const from = props.modelValue.findIndex(field => field.id === source.id)
    if (from >= 0) move(from, Math.min(target, props.modelValue.length - 1))
  }
  dragSource.value = undefined; dragOverId.value = ''
}
function endDrag(): void { dragSource.value = undefined; dragOverId.value = '' }
function updateOption(index: number, key: 'label' | 'value', value: string): void {
  if (!selectedField.value) return
  update({ options: selectedField.value.options.map((option, i) => i === index ? { ...option, [key]: value } : option) })
}
function addOption(): void {
  if (!selectedField.value) return
  let next = selectedField.value.options.length + 1
  while (selectedField.value.options.some(option => option.value === `option_${next}`)) next++
  update({ options: [...selectedField.value.options, { value: `option_${next}`, label: `选项 ${next}` }] })
}
</script>

<template>
  <section class="form-designer">
    <header class="form-designer__toolbar"><div><b>表单画布</b><span>{{ modelValue.length }} / 100 个控件 · {{ disabled ? '冻结修订只读，请复制为新修订后编辑' : '拖入或点击控件添加；上移 / 下移支持键盘操作' }}</span></div><button type="button" @click="preview = !preview">{{ preview ? '返回设计' : '试填预览' }}</button></header>
    <p v-if="notice" class="form-designer__notice" role="status">{{ notice }}</p>
    <div v-if="preview" class="form-designer__preview"><FormPreview :fields="modelValue" /></div>
    <div v-else class="form-designer__workspace">
      <aside class="form-designer__palette" aria-label="表单控件库">
        <section v-for="group in groups" :key="group"><h4>{{ group }}</h4><div class="form-designer__controls"><button v-for="control in controlRegistry.filter(item => item.group === group)" :key="control.id" type="button" :draggable="!disabled" :disabled="disabled || modelValue.length >= 100" :title="control.description" :aria-label="`添加${control.label}`" @click="add(control.id)" @dragstart="startControlDrag($event, control.id)" @dragend="endDrag"><i>{{ control.symbol }}</i><span>{{ control.label }}</span></button></div></section>
        <p>仅注册本项目支持的控件。不允许脚本、外部链接或自定义组件代码。</p>
      </aside>
      <main class="form-designer__canvas" aria-label="表单设计画布" @dragover="allowDrop($event)" @drop="drop($event, modelValue.length)">
        <p v-if="!modelValue.length" class="form-designer__empty">把左侧控件拖到这里，或点击控件添加。<span>先配置字段，再通过“试填预览”检查使用体验。</span></p>
        <article v-for="(field, index) in modelValue" :key="field.id" class="form-designer__card" :class="{ 'is-selected': selectedId === field.id, 'is-drop-target': dragOverId === field.id }" :draggable="!disabled" tabindex="0" :aria-label="`编辑${field.label}，第${index + 1}项`" @click="selectedId = field.id" @keydown.enter.self.prevent="selectedId = field.id" @keydown.space.self.prevent="selectedId = field.id" @dragstart="startFieldDrag($event, field.id)" @dragend="endDrag" @dragover.stop="allowDrop($event, field.id)" @drop="drop($event, index)">
          <div class="form-designer__card-heading"><span class="form-designer__handle" aria-hidden="true">⠿</span><b>{{ field.label || '未命名字段' }} <em v-if="field.required">*</em></b><span v-if="field.sensitive" class="form-designer__sensitive">敏感</span><small>{{ findControl(field.control, field.controlVersion)?.label ?? '未注册控件' }}</small></div>
          <div class="form-designer__ghost" :class="{ 'is-multiline': ['textarea', 'richtext'].includes(field.control), 'is-section': field.control === 'section' }"><span>{{ field.control === 'section' ? field.label : findControl(field.control)?.managed ? '受管业务控件 · 运行上下文接入后启用' : ['select', 'multiselect'].includes(field.control) ? field.dictionaryCode ? `字典 ${field.dictionaryCode}` : '请选择…' : field.control === 'boolean' ? '○ 是 / 否' : '填写区域' }}</span><small>{{ field.code }}</small></div>
          <p v-if="field.helpText" class="form-designer__field-help">{{ field.helpText }}</p>
          <div class="form-designer__card-actions"><button type="button" :disabled="disabled || index === 0" :aria-label="`上移${field.label}`" @click.stop="move(index, index - 1)">↑ 上移</button><button type="button" :disabled="disabled || index === modelValue.length - 1" :aria-label="`下移${field.label}`" @click.stop="move(index, index + 1)">↓ 下移</button><button type="button" :disabled="disabled || modelValue.length >= 100" :aria-label="`复制${field.label}`" @click.stop="copy(field)">复制</button><button type="button" :disabled="disabled" class="is-danger" :aria-label="`删除${field.label}`" @click.stop="remove(field)">删除</button></div>
        </article>
        <div v-if="modelValue.length" class="form-designer__drop-end" :class="{ 'is-drop-target': dragOverId === 'end' }">拖到这里，添加到表单末尾</div>
      </main>
      <aside class="form-designer__properties" aria-label="字段属性">
        <h4>字段属性</h4>
        <template v-if="selectedField">
          <div class="form-designer__control-version">{{ selectedControl?.label ?? '未知控件' }} · 控件 v{{ selectedField.controlVersion }}</div>
          <label :for="`${propertyPrefix}-label`">显示名称 <em>*</em></label><input :id="`${propertyPrefix}-label`" :value="selectedField.label" :disabled="disabled" maxlength="80" @input="update({ label: inputValue($event) })" />
          <label :for="`${propertyPrefix}-code`">字段编码 <em>*</em></label><input :id="`${propertyPrefix}-code`" :value="selectedField.code" :disabled="disabled" maxlength="64" spellcheck="false" @input="update({ code: inputValue($event) })" /><small>小写字母开头，可含数字和下划线。同表单唯一，不覆盖系统字段。</small>
          <label class="form-designer__check"><input type="checkbox" :checked="selectedField.required" :disabled="disabled || ['section', 'iam'].includes(selectedField.control)" @change="update({ required: checked($event) })" />用户必填</label>
          <label class="form-designer__check"><input type="checkbox" :checked="selectedField.sensitive" :disabled="disabled || selectedField.control === 'section'" @change="update({ sensitive: checked($event) })" />敏感字段</label>
          <label :for="`${propertyPrefix}-help`">填写帮助</label><textarea :id="`${propertyPrefix}-help`" :value="selectedField.helpText" :disabled="disabled" rows="3" maxlength="300" placeholder="例如：填写报错时间和受影响范围" @input="update({ helpText: inputValue($event) })" />
          <template v-if="selectedControl?.options">
            <label :for="`${propertyPrefix}-dictionary`">受控字典编码（可选）</label><input :id="`${propertyPrefix}-dictionary`" :value="selectedField.dictionaryCode ?? ''" :disabled="disabled" maxlength="63" placeholder="例如 AFFECTED_SYSTEM" @input="update({ dictionaryCode: inputValue($event).trim().toUpperCase() || undefined })" />
            <small>绑定字典后实际选项由服务端授权提供，下面静态选项不用于字典预览。</small>
            <div class="form-designer__options-head"><b>静态选项</b><button type="button" :disabled="disabled || selectedField.options.length >= 100" @click="addOption">+ 添加</button></div>
            <div v-for="(option, index) in selectedField.options" :key="index" class="form-designer__option"><input :value="option.label" :disabled="disabled" :aria-label="`选项 ${index + 1} 名称`" maxlength="100" placeholder="显示名称" @input="updateOption(index, 'label', inputValue($event))" /><input :value="option.value" :disabled="disabled" :aria-label="`选项 ${index + 1} 编码`" maxlength="100" placeholder="保存编码" @input="updateOption(index, 'value', inputValue($event))" /><button type="button" :disabled="disabled" :aria-label="`移除选项 ${index + 1}`" @click="update({ options: selectedField.options.filter((_, i) => i !== index) })">×</button></div>
          </template>
          <p v-if="selectedControl?.managed" class="form-designer__managed-note">{{ selectedControl.description }}。当前为设计能力，未绕过运行授权。</p>
          <p v-if="selectedField.control === 'richtext'" class="form-designer__managed-note">复用平台富文本编辑器；设计预览只支持文字，不上传、不提交试填值。</p>
        </template>
        <p v-else class="form-designer__empty">选择画布上的控件后编辑属性。</p>
      </aside>
    </div>
    <details v-if="errors.length" class="form-designer__errors" open><summary>有 {{ errors.length }} 项配置需要调整</summary><ul><li v-for="error in errors" :key="error">{{ error }}</li></ul></details>
    <footer>本次为设计草稿。字段权限、条件规则和业务数据校验仍需后续运行接入；预览不等于已发布。</footer>
  </section>
</template>

<style scoped>
.form-designer { border: 1px solid #d8e2ee; border-radius: 6px; overflow: hidden; color: #354c68; background: #fff; }
.form-designer button { font-family: inherit; cursor: pointer; }
.form-designer button:disabled { opacity: .45; cursor: not-allowed; }
.form-designer__toolbar { display: flex; gap: 12px; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #dce5ef; background: #f8fafd; }
.form-designer__toolbar > div { display: flex; flex-direction: column; gap: 5px; }
.form-designer__toolbar b { font-size: 14px; }
.form-designer__toolbar span { font-size: 11px; color: #75879e; }
.form-designer__toolbar > button { flex-shrink: 0; border: 1px solid #a9bfdc; border-radius: 4px; background: #fff; color: #3165a9; padding: 8px 12px; }
.form-designer__workspace { display: grid; grid-template-columns: 185px minmax(250px, 1fr) 255px; min-height: 470px; }
.form-designer__palette { padding: 14px 10px; border-right: 1px solid #dfe7f1; background: #fbfcfe; }
.form-designer h4 { margin: 0 0 12px; font-size: 12px; color: #526d8c; }
.form-designer__palette section + section { margin-top: 20px; }
.form-designer__controls { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
.form-designer__controls button { display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 0; border: 1px solid #d9e3ee; border-radius: 4px; background: #fff; color: #486789; font-size: 11px; padding: 9px 3px; }
.form-designer__controls button:not(:disabled):hover { border-color: #709fdf; background: #eef5ff; }
.form-designer__controls i { font-size: 17px; font-style: normal; color: #4c7bb8; }
.form-designer__palette > p { font-size: 10px; color: #8291a4; line-height: 1.65; margin-top: 22px; }
.form-designer__canvas { min-width: 0; padding: 16px; background: #f0f4f9; }
.form-designer__card { margin-bottom: 12px; padding: 12px; background: #fff; border: 1px solid #d9e2ed; border-radius: 5px; outline-offset: 2px; }
.form-designer__card.is-selected { border-color: #598cd0; box-shadow: 0 0 0 1px #598cd0; }
.form-designer__card.is-drop-target { border-top: 4px solid #3b7ad0; }
.form-designer__card-heading { display: flex; align-items: center; gap: 7px; margin-bottom: 12px; }
.form-designer__card-heading b { font-size: 13px; overflow-wrap: anywhere; }
.form-designer__card-heading small { margin-left: auto; font-size: 10px; color: #8a9aae; white-space: nowrap; }
.form-designer__handle { color: #8fa3be; cursor: grab; }
.form-designer em { color: #b84747; font-style: normal; }
.form-designer__sensitive { padding: 2px 4px; font-size: 10px; color: #966022; background: #fff0d7; }
.form-designer__ghost { display: flex; justify-content: space-between; gap: 8px; padding: 10px; color: #9caabe; border: 1px solid #e0e7ef; border-radius: 4px; font-size: 12px; background: #fbfcfe; }
.form-designer__ghost small { font-size: 10px; overflow-wrap: anywhere; }
.form-designer__ghost.is-multiline { min-height: 66px; }
.form-designer__ghost.is-section { color: #4875b1; border: 0; border-bottom: 2px solid #7ca4dc; background: transparent; }
.form-designer__field-help { margin: 7px 0; font-size: 11px; color: #7c8da3; }
.form-designer__card-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 10px; }
.form-designer__card-actions button { border: 0; padding: 3px 0; color: #5179ac; background: transparent; font-size: 11px; }
.form-designer__card-actions .is-danger { color: #aa5151; }
.form-designer__drop-end { padding: 16px 8px; border: 1px dashed #c4d4e7; color: #7d94ae; text-align: center; font-size: 11px; }
.form-designer__drop-end.is-drop-target { border-color: #3b7ad0; background: #e5f0ff; }
.form-designer__properties { min-width: 0; padding: 14px; border-left: 1px solid #dfe7f1; }
.form-designer__properties > label { display: block; font-size: 12px; margin: 15px 0 7px; }
.form-designer__properties input:not([type=checkbox]), .form-designer__properties textarea { box-sizing: border-box; min-width: 0; width: 100%; padding: 8px; border: 1px solid #cdd9e8; border-radius: 4px; background: #fff; color: #3e5572; font: inherit; font-size: 12px; }
.form-designer__properties input:disabled, .form-designer__properties textarea:disabled { background: #f4f7fa; color: #8391a4; }
.form-designer__properties > small { display: block; color: #8090a5; font-size: 10px; line-height: 1.6; margin-top: 6px; }
.form-designer__properties > .form-designer__check { display: flex; gap: 8px; align-items: center; }
.form-designer__control-version { padding: 8px; font-size: 11px; color: #4c73a3; background: #eef4fd; border-radius: 4px; }
.form-designer__options-head { display: flex; justify-content: space-between; align-items: center; margin: 20px 0 10px; font-size: 12px; }
.form-designer__options-head button, .form-designer__option button { border: 0; color: #3d70b1; background: transparent; font-size: 11px; }
.form-designer__option { display: grid; grid-template-columns: 1fr 1fr 15px; gap: 5px; margin-bottom: 7px; }
.form-designer__option button { padding: 0; font-size: 19px; color: #b16565; }
.form-designer__managed-note { font-size: 11px; line-height: 1.65; padding: 9px; background: #fff7e7; color: #8b6b36; }
.form-designer__empty { padding: 38px 12px; text-align: center; color: #7a8da5; font-size: 12px; line-height: 1.8; }
.form-designer__empty span { display: block; font-size: 11px; }
.form-designer__preview { padding: 20px; max-width: 900px; margin: auto; }
.form-designer__errors { border-top: 1px solid #e6c4bd; padding: 12px 16px; background: #fff7f4; color: #a2483c; font-size: 12px; line-height: 1.65; }
.form-designer__errors ul { margin-bottom: 0; padding-left: 20px; }
.form-designer__notice { margin: 0; padding: 8px 16px; background: #eef7f1; color: #427e60; font-size: 11px; }
.form-designer footer { padding: 10px 16px; border-top: 1px solid #dfe7f1; color: #7d8ca0; font-size: 11px; line-height: 1.6; }
@media (max-width: 1150px) { .form-designer__workspace { grid-template-columns: 150px minmax(230px, 1fr); }.form-designer__properties { grid-column: 1 / -1; border-left: 0; border-top: 1px solid #dfe7f1; }.form-designer__properties > input, .form-designer__properties > textarea { max-width: 700px; }.form-designer__option { max-width: 700px; } }
@media (max-width: 650px) { .form-designer__workspace { display: flex; flex-direction: column; }.form-designer__palette { border-right: 0; }.form-designer__controls { grid-template-columns: repeat(3, 1fr); }.form-designer__palette section + section { margin-top: 14px; }.form-designer__canvas { padding: 12px; }.form-designer__toolbar { align-items: flex-start; }.form-designer__card-heading { flex-wrap: wrap; }.form-designer__ghost { flex-direction: column; } }
</style>
