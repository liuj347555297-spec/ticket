import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseServiceLaunch, matchesServiceSelection } from '../src/utils/serviceLaunch.ts'
test('service entry requires a specific system and catalog, with optional module', () => {
  assert.deepEqual(parseServiceLaunch({}), { kind: 'NONE' })
  assert.deepEqual(parseServiceLaunch({ systemCode: 'ERP', catalogId: 'SC-ERP-FAULT' }), { kind: 'VALID', intent: { systemCode: 'ERP', moduleCode: '', catalogId: 'SC-ERP-FAULT' } })
  assert.equal(parseServiceLaunch({ systemCode: 'ERP', moduleCode: 'FIN', catalogId: 'SC-ERP-FAULT' }).kind, 'VALID')
})
test('ambiguous repeated parameters, partial and malformed entry hints are rejected', () => {
  for (const query of [{ systemCode: 'ERP' }, { catalogId: 'SC-ERP-FAULT' }, { systemCode: ['ERP'], catalogId: 'SC-ERP-FAULT' }, { systemCode: 'ERP', moduleCode: ['FIN'], catalogId: 'SC-ERP-FAULT' }, { systemCode: '../ERP', catalogId: 'SC-ERP-FAULT' }, { systemCode: 'ERP', catalogId: 'javascript:alert(1)' }]) assert.equal(parseServiceLaunch(query).kind, 'INVALID')
})
test('a result for another system, module or service cannot fill the current selection', () => {
  const entry = { systemCode: 'ERP', moduleCode: '', catalogId: 'SC-ERP-FAULT' }
  assert.equal(matchesServiceSelection(entry, { ...entry }), true)
  for (const change of [{ systemCode: 'CRM' }, { moduleCode: 'FIN' }, { catalogId: 'SC-ERP-ACCESS' }]) assert.equal(matchesServiceSelection(entry, { ...entry, ...change }), false)
})
