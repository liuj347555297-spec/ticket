import type { ControlId, NodeFormBinding, StudioField, StudioFormRevision } from '../api/designer'

export interface ControlRegistration {
  id: ControlId
  version: 1
  label: string
  group: '基础控件' | '业务控件' | '布局'
  symbol: string
  description: string
  managed?: boolean
  options?: boolean
}

/** Compiled allowlist. Design data cannot supply a component, script, URL or loader. */
export const controlRegistry: readonly ControlRegistration[] = Object.freeze([
  { id: 'text', version: 1, label: '单行文本', group: '基础控件', symbol: 'T', description: '简短业务描述' },
  { id: 'textarea', version: 1, label: '多行文本', group: '基础控件', symbol: '¶', description: '较长的文字说明' },
  { id: 'number', version: 1, label: '数字', group: '基础控件', symbol: '#', description: '数值输入' },
  { id: 'date', version: 1, label: '日期', group: '基础控件', symbol: '▦', description: '选择年月日' },
  { id: 'datetime', version: 1, label: '日期时间', group: '基础控件', symbol: '◷', description: '选择日期和时间' },
  { id: 'select', version: 1, label: '单选', group: '基础控件', symbol: '◉', description: '静态选项或受控字典', options: true },
  { id: 'multiselect', version: 1, label: '多选', group: '基础控件', symbol: '☑', description: '选择多个选项', options: true },
  { id: 'boolean', version: 1, label: '是 / 否', group: '基础控件', symbol: '✓', description: '布尔开关' },
  { id: 'richtext', version: 1, label: '富文本', group: '业务控件', symbol: 'Aa', description: '复用平台文字编辑器；预览不接收图片或附件' },
  { id: 'tags', version: 1, label: '标签', group: '业务控件', symbol: '♯', description: '运行接入后按标准标签与自由标签策略校验', managed: true },
  { id: 'ci', version: 1, label: 'CMDB 配置项', group: '业务控件', symbol: '▣', description: '需要组织范围与 CMDB 只读投影上下文', managed: true },
  { id: 'attachment', version: 1, label: '受管附件', group: '业务控件', symbol: '↥', description: '需要工单身份、上传授权与扫描回执；设计预览不上传', managed: true },
  { id: 'iam', version: 1, label: '申请人快照', group: '业务控件', symbol: '◎', description: '运行时由 IAM 提供的只读系统块，不接收用户改写', managed: true },
  { id: 'user', version: 1, label: '候选人员', group: '业务控件', symbol: '♙', description: '需要当前节点授权候选池；不提供任意人员搜索', managed: true },
  { id: 'section', version: 1, label: '分组标题', group: '布局', symbol: '─', description: '分隔表单内容，不保存填写值' },
] satisfies ControlRegistration[])

export function findControl(id: string, version = 1): ControlRegistration | undefined {
  return controlRegistry.find(control => control.id === id && control.version === version)
}

export function uniqueFieldCode(fields: readonly StudioField[], base = 'field'): string {
  const safeBase = /^[a-z][a-z0-9_]*$/.test(base) ? base.slice(0, 54) : 'field'
  const existing = new Set(fields.map(field => field.code))
  let number = 1
  while (existing.has(`${safeBase}_${number}`)) number++
  return `${safeBase}_${number}`
}

export function newField(control: ControlId, fields: readonly StudioField[], id = crypto.randomUUID()): StudioField {
  const registered = findControl(control)
  if (!registered) throw new Error('控件未注册')
  return { id, code: uniqueFieldCode(fields, control), label: registered.label, control, controlVersion: 1,
    required: false, sensitive: false, helpText: '', options: registered.options ? [{ value: 'option_1', label: '选项一' }, { value: 'option_2', label: '选项二' }] : [] }
}

export function copyField(field: StudioField, fields: readonly StudioField[], id = crypto.randomUUID()): StudioField {
  return { ...field, id, code: uniqueFieldCode(fields, field.code), label: `${field.label}（副本）`.slice(0, 80), options: field.options.map(option => ({ ...option })) }
}

export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  const result = [...items]
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 0 || from >= items.length || to < 0 || to >= items.length || from === to) return result
  const [item] = result.splice(from, 1)
  result.splice(to, 0, item)
  return result
}

/** Tuple encoding prevents separator collisions between node, form and field codes. */
export function formValueKey(nodeId: string, formId: string, revision: number, fieldCode: string): string {
  return JSON.stringify([nodeId, formId, revision, fieldCode])
}

export const reservedFieldCodes = new Set(['id', 'ticket_id', 'ticket_no', 'status', 'priority', 'requester', 'requester_id', 'requester_department', 'iam_user_id', 'organization', 'organization_id', 'handler', 'assignee', 'processor', 'workflow', 'workflow_instance', 'process_instance', 'audit', 'created_at', 'updated_at', 'attachments'])

function plainMetadata(value: string): boolean { return !/[<>\u0000-\u001f\u007f-\u009f]/u.test(value) && !value.includes('${') && !value.includes('#{') }

export function validateFields(fields: readonly StudioField[]): string[] {
  const errors: string[] = []
  const ids = new Set<string>(), codes = new Set<string>()
  if (fields.length > 100) errors.push('每张表单最多 100 个控件。')
  for (const field of fields) {
    const label = field.label || field.code || '未命名字段'
    if (!findControl(field.control, field.controlVersion)) errors.push(`${label}：控件或版本未注册。`)
    if (!/^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$/.test(field.id) || ids.has(field.id)) errors.push(`${label}：控件 ID 无效或重复。`)
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(field.code)) errors.push(`${label}：编码须以小写字母开头，仅含小写字母、数字、下划线，最多 64 字符。`)
    if (reservedFieldCodes.has(field.code)) errors.push(`${label}：不能覆盖平台保留字段。`)
    if (codes.has(field.code)) errors.push(`${label}：同一表单字段编码不能重复。`)
    if (!field.label.trim() || field.label.length > 80) errors.push(`${label}：名称必填，最多 80 字符。`)
    if (field.helpText.length > 300) errors.push(`${label}：帮助提示最多 300 字符。`)
    if (!plainMetadata(field.label) || !plainMetadata(field.helpText)) errors.push(`${label}：名称和提示请使用单行纯文本，不含尖括号、控制字符或表达式。`)
    if (field.dictionaryCode && (!['select', 'multiselect'].includes(field.control) || !/^[A-Z][A-Z0-9_]{1,62}$/.test(field.dictionaryCode))) errors.push(`${label}：字典仅适用于单选/多选，编码须为 2–63 位大写字母、数字和下划线。`)
    if (field.options.length > 100) errors.push(`${label}：最多 100 个静态选项。`)
    if (field.options.length && !['select', 'multiselect'].includes(field.control)) errors.push(`${label}：只有单选/多选可配置静态选项。`)
    if (['select', 'multiselect'].includes(field.control)) {
      if (!field.dictionaryCode && field.options.length === 0) errors.push(`${label}：请添加选项或绑定字典。`)
      if (field.options.some(option => !option.value.trim() || !option.label.trim()) || new Set(field.options.map(option => option.value)).size !== field.options.length) errors.push(`${label}：选项编码和名称不能为空，编码不能重复。`)
      if (field.options.some(option => option.value.length > 100 || option.label.length > 100 || !plainMetadata(option.value) || !plainMetadata(option.label))) errors.push(`${label}：选项编码和名称最多 100 字符，必须为单行纯文本。`)
    }
    if ((field.control === 'section' || field.control === 'iam') && field.required) errors.push(`${label}：分组和只读身份快照不能要求用户填写。`)
    ids.add(field.id); codes.add(field.code)
  }
  return errors
}

export function replaceNodeBindings(all: readonly NodeFormBinding[], nodeId: string, bindings: readonly NodeFormBinding[]): NodeFormBinding[] {
  return [...all.filter(binding => binding.nodeId !== nodeId), ...bindings.map((binding, index) => ({ ...binding, nodeId, displayOrder: index + 1 }))]
}

export function validateNodeBindings(bindings: readonly NodeFormBinding[], forms: readonly StudioFormRevision[]): string[] {
  const errors: string[] = [], seen = new Set<string>()
  for (const binding of bindings) {
    const identity = JSON.stringify([binding.nodeId, binding.formId])
    if (!binding.nodeId.trim()) errors.push('请选择流程节点后绑定表单。')
    if (!forms.some(form => form.formId === binding.formId && form.revision === binding.formRevision)) errors.push(`${binding.nodeId}：绑定的表单修订不存在。`)
    if (seen.has(identity)) errors.push(`${binding.nodeId}：同一表单只能绑定一个明确修订。`)
    if (!['EDIT', 'READ_ONLY'].includes(binding.mode)) errors.push(`${binding.nodeId}：表单访问模式无效。`)
    if (binding.mode === 'READ_ONLY' && binding.requiredOnComplete) errors.push(`${binding.nodeId}：只读表单不能设为完成时必填。`)
    if (!Number.isInteger(binding.displayOrder) || binding.displayOrder < 1) errors.push(`${binding.nodeId}：表单顺序无效。`)
    seen.add(identity)
  }
  return errors
}
