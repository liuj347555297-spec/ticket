import { test } from 'node:test'
import assert from 'node:assert/strict'
import { workflowOutcome } from '../src/utils/workflowOutcome.ts'

test('approval requests never masquerade as completed operations', () => {
  for (const code of ['RESOLVE', 'ASSIGN', 'CLOSE', 'HOLD', 'ESCALATE', 'CANCEL', 'REOPEN', 'CONTROLLED_JUMP_REQUEST']) assert.equal(workflowOutcome(code), 'PENDING_APPROVAL')
  assert.equal(workflowOutcome('ACCEPT', 'PENDING_ACCEPTANCE'), 'PENDING_APPROVAL')
  assert.equal(workflowOutcome('ACCEPT', 'IN_PROGRESS'), 'COMPLETED')
  assert.equal(workflowOutcome('RESOLVE', 'RESOLVED'), 'COMPLETED')
  assert.equal(workflowOutcome('CLOSE', 'CLOSED'), 'COMPLETED')
})
test('collaboration confirmations remain pending while direct operations can complete', () => {
  for (const code of ['HANDOVER', 'ADD_COHANDLER']) assert.equal(workflowOutcome(code), 'PENDING_PROCESS_TASK')
  for (const code of ['INTERNAL_COMMENT', 'CLAIM', 'TRANSFER', 'CLASSIFY', 'RESUME', 'START_PROCESSING', 'REQUEST_USER_FEEDBACK']) assert.equal(workflowOutcome(code), 'COMPLETED')
})
