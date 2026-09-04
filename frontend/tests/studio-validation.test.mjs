import { test } from 'node:test'
import assert from 'node:assert/strict'
import { hasBindableNode, validateFormIdentities } from '../src/utils/studioValidation.ts'
const form = { formId: 'FORM-1', revision: 1, code: 'FORM_A', name: '申请表', status: 'DRAFT', fields: [] }
test('revisions of one identity cannot drift to different stable codes', () => {
  assert.equal(validateFormIdentities([form, { ...form, revision: 2 }]).length, 0)
  assert.ok(validateFormIdentities([form, { ...form, revision: 2, code: 'FORM_B' }]).length)
  assert.ok(validateFormIdentities([form, { ...form, formId: 'FORM-2' }]).length)
})
test('invalid metadata is rejected before freezing a design snapshot', () => {
  for (const change of [{ name: '' }, { code: '1invalid' }, { name: '<script>' }, { revision: 0 }]) assert.ok(validateFormIdentities([{ ...form, ...change }]).length)
})
test('changing a bound user task into a generic task makes the binding invalid', () => {
  const binding = { nodeId: 'Task_1' }
  assert.equal(hasBindableNode(binding, [{ id: 'Task_1', type: 'bpmn:UserTask' }]), true)
  assert.equal(hasBindableNode(binding, [{ id: 'Task_1', type: 'bpmn:Task' }]), false)
  assert.equal(hasBindableNode(binding, []), false)
})
