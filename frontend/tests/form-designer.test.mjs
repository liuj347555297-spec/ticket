import { test } from 'node:test'
import assert from 'node:assert/strict'
import { controlRegistry, copyField, findControl, formValueKey, moveItem, newField, replaceNodeBindings, uniqueFieldCode, validateFields, validateNodeBindings } from '../src/forms/registry.ts'

test('registry admits exactly fifteen compiled controls at version one', () => {
  assert.equal(controlRegistry.length, 15)
  assert.equal(new Set(controlRegistry.map(control => control.id)).size, 15)
  for (const control of controlRegistry) {
    assert.equal(control.version, 1)
    assert.equal(validateFields([newField(control.id, [], `test-${control.id}`)]).length, 0)
  }
  assert.equal(findControl('script'), undefined)
  assert.equal(findControl('https://example.org/plugin.js'), undefined)
  assert.equal(findControl('text', 2), undefined)
  assert.equal(findControl('attachment').managed, true)
  assert.equal(findControl('user').managed, true)
})

test('field creation and duplication isolate IDs, codes and mutable options', () => {
  const field = newField('select', [], 'original')
  const copied = copyField(field, [field], 'copy')
  assert.notEqual(copied.id, field.id)
  assert.notEqual(copied.code, field.code)
  copied.options[0].label = 'changed'
  assert.equal(field.options[0].label, '选项一')
  assert.equal(uniqueFieldCode([{ ...field, code: 'text_1' }, { ...field, code: 'text_2' }], 'text'), 'text_3')
  assert.equal(uniqueFieldCode([], 'script:alert(1)'), 'field_1')
  assert.ok(uniqueFieldCode([], 'a'.repeat(64)).length <= 64)
})

test('reorder does not mutate original arrays and invalid positions preserve order', () => {
  const items = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]
  assert.deepEqual(moveItem(items, 0, 2).map(item => item.id), ['b', 'c', 'a'])
  assert.deepEqual(moveItem(items, 2, 0).map(item => item.id), ['c', 'a', 'b'])
  assert.deepEqual(items.map(item => item.id), ['a', 'b', 'c'])
  assert.deepEqual(moveItem(items, -1, 1), items)
  assert.deepEqual(moveItem(items, 0, 3), items)
  assert.deepEqual(moveItem(items, 0.5, 1), items)
})

test('metadata validation rejects unknown controls, versions, collisions and system fields', () => {
  const field = newField('text', [], 'first')
  assert.equal(validateFields([field]).length, 0)
  assert.ok(validateFields([{ ...field, control: 'eval' }]).some(error => error.includes('未注册')))
  assert.ok(validateFields([{ ...field, controlVersion: 9 }]).some(error => error.includes('未注册')))
  assert.ok(validateFields([field, field]).some(error => error.includes('重复')))
  assert.ok(validateFields([{ ...field, code: 'status' }]).some(error => error.includes('保留字段')))
  assert.ok(validateFields([{ ...field, code: 'a.b' }]).some(error => error.includes('编码')))
  assert.ok(validateFields([{ ...field, dictionaryCode: 'HTTPS://HOST' }]).some(error => error.includes('字典')))
  const select = newField('select', [], 'select')
  assert.ok(validateFields([{ ...select, options: [{ value: 'one', label: 'A' }, { value: 'one', label: 'B' }] }]).some(error => error.includes('编码不能重复')))
})

test('form value namespace distinguishes task, form revision and separator-containing identifiers', () => {
  const keys = [formValueKey('node', 'form', 1, 'description'), formValueKey('node', 'form', 2, 'description'), formValueKey('other', 'form', 1, 'description'), formValueKey('node', 'form:1', 1, 'description'), formValueKey('node:form', '1', 1, 'description')]
  assert.equal(new Set(keys).size, keys.length)
  assert.deepEqual(JSON.parse(keys[0]), ['node', 'form', 1, 'description'])
})

test('node updates preserve other nodes and normalize only current-node order', () => {
  const other = { nodeId: 'other', formId: 'f0', formRevision: 3, displayOrder: 4, mode: 'READ_ONLY', requiredOnComplete: false }
  const current = { nodeId: 'task', formId: 'f1', formRevision: 1, displayOrder: 1, mode: 'EDIT', requiredOnComplete: true }
  const extra = { ...current, formId: 'f2', displayOrder: 8 }
  const original = [other, current]
  const next = replaceNodeBindings(original, 'task', [extra, current])
  assert.equal(next[0], other)
  assert.deepEqual(next.slice(1).map(binding => [binding.formId, binding.displayOrder]), [['f2', 1], ['f1', 2]])
  assert.equal(original.length, 2)
})

test('binding validation requires explicit existing revision and one revision per form at a node', () => {
  const form = { formId: 'f1', code: 'FORM', name: '测试表单', revision: 1, status: 'DRAFT', fields: [] }
  const binding = { nodeId: 'task', formId: 'f1', formRevision: 1, displayOrder: 1, mode: 'EDIT', requiredOnComplete: true }
  assert.deepEqual(validateNodeBindings([binding], [form]), [])
  assert.ok(validateNodeBindings([{ ...binding, formRevision: 2 }], [form]).some(error => error.includes('不存在')))
  assert.ok(validateNodeBindings([binding, { ...binding, formRevision: 2 }], [form, { ...form, revision: 2 }]).some(error => error.includes('只能绑定')))
  assert.ok(validateNodeBindings([{ ...binding, mode: 'READ_ONLY' }], [form]).some(error => error.includes('只读')))
  assert.deepEqual(validateNodeBindings([binding, { ...binding, nodeId: 'another' }], [form]), [])
})
