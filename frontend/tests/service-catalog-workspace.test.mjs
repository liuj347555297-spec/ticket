import { test } from 'node:test'
import assert from 'node:assert/strict'
import { allowDesignerTransition, createRequestScope, effectiveMappings, embeddedDesignerKey, mappingChanges, mergeOfferingMetadata, preserveDesignerDuringIdentityRefresh } from '../src/utils/serviceCatalogWorkspace.ts'
const mapping = (id, active = true, isDefault = false) => ({ systemCode: 'ERP', serviceCatalogItemId: id, active, isDefault, version: 2 })
test('switch and identity invalidation reject stale response generations', () => {
  const scope = createRequestScope(), first = scope.next(), second = scope.next()
  assert.equal(scope.accepts(first), false); assert.equal(scope.accepts(second), true)
  scope.next(); assert.equal(scope.accepts(second), false)
})
test('module display falls back without changing direct module mappings', () => {
  const system = [mapping('system')], module = [mapping('module', false)]
  assert.deepEqual(effectiveMappings(system, module, true), system)
  assert.deepEqual(effectiveMappings(system, [mapping('module')], true), [mapping('module')])
  assert.deepEqual(effectiveMappings(system, [mapping('module')], false), system)
  assert.equal(module[0].active, false)
})
test('mapping diff preserves unseen selected IDs and deactivates removed mappings explicitly', () => {
  const result = mappingChanges([mapping('a', true, true), mapping('b'), mapping('hidden')], ['b', 'hidden', 'new'], 'b')
  assert.deepEqual(result, [{ serviceCatalogItemId: 'a', active: false, isDefault: false, version: 2 }, { serviceCatalogItemId: 'new', active: true, isDefault: false, version: 0 }, { serviceCatalogItemId: 'b', active: true, isDefault: true, version: 2 }])
  assert.throws(() => mappingChanges([], [], 'missing'))
  assert.deepEqual(mappingChanges([mapping('a')], ['a'], ''), [])
})
test('published seed metadata enriches only mapped scope and never overrides managed metadata', () => {
  const managed = { id: 'managed', name: '后台草稿名称', publishedVersion: 3 }
  const seed = { id: 'SC-ERP-PERFORMANCE', name: '业务系统 - 页面性能问题', publishedVersion: 3 }
  const publicCopy = { ...managed, name: '公开旧名称', publishedVersion: 2 }
  const unrelated = { id: 'other-system', name: '不可混入的服务' }
  const scope = [mapping(managed.id), mapping(seed.id)]
  const original = [managed]
  const merged = mergeOfferingMetadata(original, [publicCopy, seed, unrelated], scope)
  assert.deepEqual(merged, [managed, seed])
  assert.equal(merged[0], managed)
  assert.deepEqual(original, [managed])
  assert.deepEqual(mergeOfferingMetadata(original, [], scope), [managed])
  assert.deepEqual(mergeOfferingMetadata(original, [seed], []), [managed])
})
test('embedded designer transition delegates dirty confirmation but blocks busy operations', () => {
  let calls = 0
  const accept = { canLeave: () => { calls++; return true } }, cancel = { canLeave: () => false }
  assert.equal(allowDesignerTransition(true, accept, { dirty: true, busy: false, uncertain: false }), true)
  assert.equal(calls, 1)
  assert.equal(allowDesignerTransition(true, cancel, { dirty: true, busy: false, uncertain: false }), false)
  assert.equal(allowDesignerTransition(true, accept, { dirty: false, busy: true, uncertain: false }), false)
  assert.equal(calls, 1)
  assert.equal(allowDesignerTransition(true, undefined, { dirty: false, busy: false, uncertain: true }), false)
  assert.equal(allowDesignerTransition(false, cancel, { dirty: true, busy: true, uncertain: true }), true)
})
test('embedded design component keys isolate system, service and identity', () => {
  const values = [embeddedDesignerKey(1, 'ERP'), embeddedDesignerKey(1, 'ERP', 'service-a'), embeddedDesignerKey(1, 'ERP', 'service-b'), embeddedDesignerKey(1, 'FIN', 'service-a'), embeddedDesignerKey(2, 'ERP', 'service-a')]
  assert.equal(new Set(values).size, values.length)
  assert.equal(embeddedDesignerKey(1, 'ERP', undefined), embeddedDesignerKey(1, 'ERP'))
})
test('same-subject refresh retains protected design attempts but cross-subject refresh clears them', () => {
  const uncertain = { dirty: true, busy: false, uncertain: true }
  assert.equal(preserveDesignerDuringIdentityRefresh(true, true, uncertain), true)
  assert.equal(preserveDesignerDuringIdentityRefresh(true, true, { dirty: false, busy: true, uncertain: false }), true)
  assert.equal(preserveDesignerDuringIdentityRefresh(true, false, uncertain), false)
  assert.equal(preserveDesignerDuringIdentityRefresh(false, true, uncertain), false)
  assert.equal(preserveDesignerDuringIdentityRefresh(true, true, { dirty: false, busy: false, uncertain: false }), false)
})
