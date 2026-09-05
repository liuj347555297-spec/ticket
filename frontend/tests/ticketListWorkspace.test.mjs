import assert from 'node:assert/strict'
import test from 'node:test'
import { defaultTicketColumns, mergeTicketColumns, ticketColumnStorageKey, ticketCsv, ticketQueueFromQuery } from '../src/utils/ticketListWorkspace.ts'

test('路由队列只接受已知枚举且支持当日到期', () => {
  assert.equal(ticketQueueFromQuery('TODAY_DUE'), 'TODAY_DUE')
  assert.equal(ticketQueueFromQuery(['MY_DONE', 'ALL']), 'MY_DONE')
  assert.equal(ticketQueueFromQuery('UNKNOWN'), 'MY_TODO')
})

test('列设置仅合并已知布尔值并按账号隔离', () => {
  const merged = mergeTicketColumns({ service: false, status: 'bad', injected: true })
  assert.equal(merged.service, false)
  assert.equal(merged.status, defaultTicketColumns.status)
  assert.equal('injected' in merged, false)
  assert.notEqual(ticketColumnStorageKey('user-a'), ticketColumnStorageKey('user-b'))
})

test('CSV 使用当前数据列并阻断公式注入', () => {
  const value = ticketCsv([{ id: 'TKT-1', title: '=cmd', type: 'INCIDENT', status: 'IN_PROGRESS', priority: 'P2', requester: { iamUserId: 'u', displayName: '张三', organizationId: 'o', organizationName: '组织', capturedAt: '2026-09-05T00:00:00Z' }, serviceCatalogItem: { id: 's', name: '服务' }, createdAt: '2026-09-05T00:00:00Z', version: 1 }], { type: { INCIDENT: '故障报修' }, status: { IN_PROGRESS: '处理中' } })
  assert.match(value, /"'=cmd"/)
  assert.match(value, /工单编号/)
  assert.equal(value.startsWith('\uFEFF'), true)
})
