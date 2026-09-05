import assert from 'node:assert/strict'
import test from 'node:test'
import { filterSystems, paginateRows } from '../src/utils/adminTable.ts'

const systems = [
  { systemCode: 'ERP', systemName: '财务系统', lifecycleStatus: 'PUBLISHED', owningOrganizationId: 'ORG-FIN', ownerIamUserId: 'u-1' },
  { systemCode: 'CRM', systemName: '客户管理', lifecycleStatus: 'DRAFT', owningOrganizationId: 'ORG-SALES', ownerIamUserId: 'u-2' },
  { systemCode: 'OPS', systemName: '运维平台', lifecycleStatus: 'RETIRED', owningOrganizationId: 'ORG-IT' },
]

test('系统查询条件同时生效且不修改源数组', () => {
  const result = filterSystems(systems, { keyword: '系统', status: 'PUBLISHED', organization: 'fin', owner: 'U-1' })
  assert.deepEqual(result.map((item) => item.systemCode), ['ERP'])
  assert.equal(systems.length, 3)
})

test('分页越界时收敛到最后一页', () => {
  const result = paginateRows(systems, 99, 2)
  assert.equal(result.page, 2)
  assert.deepEqual(result.items.map((item) => item.systemCode), ['OPS'])
})

test('空数组仍返回第一页', () => {
  assert.deepEqual(paginateRows([], 4, 10), { items: [], total: 0, page: 1, pageSize: 10 })
})
