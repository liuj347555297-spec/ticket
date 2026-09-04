import { test } from 'node:test'
import assert from 'node:assert/strict'
import { belongsToStudioContext, canAssociateStudio } from '../src/utils/studioScope.ts'
const system = { organizationId: 'ORG-A', systemCode: 'ERP' }
const service = { ...system, serviceCatalogItemId: 'SC-ONE' }
test('system and service design lists isolate both scope and concrete offering', () => {
  assert.equal(belongsToStudioContext(service, system), true)
  assert.equal(belongsToStudioContext(system, service), false)
  assert.equal(belongsToStudioContext({ ...service, serviceCatalogItemId: 'SC-TWO' }, service), false)
  assert.equal(belongsToStudioContext({ ...service, systemCode: 'CRM' }, system), false)
  assert.equal(belongsToStudioContext({ ...service, organizationId: 'ORG-B' }, system), false)
  assert.equal(belongsToStudioContext({}, {}), false)
})
test('legacy association is explicit, same-organization, and cannot move existing owners', () => {
  assert.equal(canAssociateStudio({ organizationId: 'ORG-A' }, system), true)
  assert.equal(canAssociateStudio(system, service), true)
  assert.equal(canAssociateStudio({ ...system, systemCode: 'CRM' }, service), false)
  assert.equal(canAssociateStudio(service, { ...service, serviceCatalogItemId: 'SC-TWO' }), false)
  assert.equal(canAssociateStudio({ organizationId: 'ORG-B' }, system), false)
  assert.equal(canAssociateStudio({ organizationId: 'ORG-A', serviceCatalogItemId: 'SC-ONE' }, system), false)
})
