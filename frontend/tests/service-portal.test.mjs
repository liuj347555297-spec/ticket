import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createPortalRequestGate, filterPortalSystems, groupPortalServices, portalTicketUrl, sortPortalModules } from '../src/utils/servicePortal.ts'

const item = (id, ticketType = 'INCIDENT', name = id) => ({ id, code: id, name, ticketType, summary: '采购业务', publishedVersion: 1 })
test('one system can expose multiple services of the same technical ticket type', () => {
  const groups = groupPortalServices([item('access', 'ACCESS_REQUEST'), item('finance'), item('purchase'), item('finance')], '')
  assert.deepEqual(groups.map((group) => group.type), ['INCIDENT', 'ACCESS_REQUEST'])
  assert.deepEqual(groups[0].items.map((service) => service.id), ['finance', 'purchase'])
})
test('service search includes individual name, code, summary and Unicode normalization', () => {
  assert.equal(groupPortalServices([item('ERP_SERVICE', 'INCIDENT', 'ERP 采购报障')], 'ｅｒｐ').length, 1)
  assert.equal(groupPortalServices([item('purchase')], '采购业务').length, 1)
  assert.equal(groupPortalServices([item('purchase')], '不存在').length, 0)
  assert.equal(groupPortalServices([], '').length, 0)
})
test('only published systems match search and only active modules are sorted without mutation', () => {
  const systems = [{ systemName: '财务 ERP', systemCode: 'ERP', lifecycleStatus: 'PUBLISHED' }, { systemName: 'ERP 草稿', systemCode: 'DRAFT', lifecycleStatus: 'DRAFT' }]
  assert.deepEqual(filterPortalSystems(systems, 'ｅｒｐ').map((system) => system.systemCode), ['ERP'])
  const modules = [{ moduleName: '财务', active: true, sortOrder: 2 }, { moduleName: '采购', active: true, sortOrder: 1 }, { moduleName: '旧模块', active: false, sortOrder: 0 }]
  assert.deepEqual(sortPortalModules(modules).map((module) => module.moduleName), ['采购', '财务'])
  assert.equal(modules[0].moduleName, '财务')
})
test('ticket URLs preserve explicit system and independent offering identity with safe encoding', () => {
  const url = new URL(portalTicketUrl('ERP &A', 'FORM?x=1', '采购/付款'), 'https://local.test')
  assert.equal(url.pathname, '/tickets/new')
  assert.equal(url.searchParams.get('systemCode'), 'ERP &A')
  assert.equal(url.searchParams.get('catalogId'), 'FORM?x=1')
  assert.equal(url.searchParams.get('moduleCode'), '采购/付款')
  assert.equal(new URL(portalTicketUrl('ERP', 'CAT'), 'https://local.test').searchParams.has('moduleCode'), false)
})
test('a newer selection, identity reset or disposal invalidates earlier async region responses', () => {
  const gate = createPortalRequestGate()
  const oldSystem = gate.next()
  assert.equal(oldSystem(), true)
  const newSystem = gate.next()
  assert.equal(oldSystem(), false)
  assert.equal(newSystem(), true)
  gate.invalidate()
  assert.equal(newSystem(), false)
})
