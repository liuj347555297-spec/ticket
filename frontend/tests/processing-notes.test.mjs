import { test } from 'node:test'
import assert from 'node:assert/strict'
import { actionSavesProcessingNote, composeResolutionReason, normalizeProcessingText, prepareProcessingNote } from '../src/utils/processingNotes.ts'

test('processing notes become internal comment detail, preserving multiline evidence', () => {
  assert.deepEqual(prepareProcessingNote('INTERNAL_COMMENT', ' 已检查\r\n继续观察 ', '', ''), {
    ok: true, payload: { reason: '记录处理进展', detail: '已检查\n继续观察' },
  })
  assert.equal(prepareProcessingNote('INTERNAL_COMMENT', ' \n\u00a0', '', '').ok, false)
  assert.equal(prepareProcessingNote('INTERNAL_COMMENT', '字'.repeat(2000), '', '').ok, true)
  assert.equal(prepareProcessingNote('INTERNAL_COMMENT', '字'.repeat(2001), '', '').ok, false)
})

test('resolution requires an explanation and includes category in the same reason', () => {
  assert.deepEqual(prepareProcessingNote('RESOLVE', 'unrelated draft', '故障修复', ' 已恢复\n验证通过 '), {
    ok: true, payload: { reason: '解决类别：故障修复\n解决说明：已恢复\n验证通过', detail: '' },
  })
  assert.equal(prepareProcessingNote('RESOLVE', '', '故障修复', ' ').ok, false)
  assert.equal(prepareProcessingNote('RESOLVE', '', '伪造类别', '已完成').ok, false)
  assert.equal(composeResolutionReason('', ' 验证通过 '), '验证通过')
})

test('resolution limit counts labels and category without silent truncation', () => {
  const prefix = composeResolutionReason('服务完成', '').length
  assert.equal(prepareProcessingNote('RESOLVE', '', '服务完成', '字'.repeat(1000 - prefix)).ok, true)
  assert.equal(prepareProcessingNote('RESOLVE', '', '服务完成', '字'.repeat(1001 - prefix)).ok, false)
  assert.equal(prepareProcessingNote('RESOLVE', '', '', '字'.repeat(1000)).ok, true)
  assert.equal(prepareProcessingNote('RESOLVE', '', '', '字'.repeat(1001)).ok, false)
})

test('other workflow actions carry processing notes as reason, or leave blank for confirmation', () => {
  assert.deepEqual(prepareProcessingNote('HANDOVER', ' 请网络组接手 ', '', ''), { ok: true, payload: { reason: '请网络组接手', detail: '' } })
  assert.deepEqual(prepareProcessingNote('HOLD', '', '', ''), { ok: true, payload: { reason: '', detail: '' } })
  assert.deepEqual(prepareProcessingNote('CLOSE', '处理信息由独立接口保存', '', ''), { ok: true, payload: { reason: '', detail: '' } })
})

test('actions that do not persist reasons neither submit nor consume processing notes', () => {
  for (const action of ['TRANSFER', 'CLAIM', 'RESUME', 'CLASSIFY', 'START_PROCESSING', 'REQUEST_USER_FEEDBACK', 'CLOSE']) {
    assert.deepEqual(prepareProcessingNote(action, '请保留这段处理意见', '', ''), { ok: true, payload: { reason: '', detail: '' } })
    assert.deepEqual(prepareProcessingNote(action, '字'.repeat(3000), '', ''), { ok: true, payload: { reason: '', detail: '' } })
    assert.deepEqual(prepareProcessingNote(action, '', '', ''), { ok: true, payload: { reason: '', detail: '' } })
    assert.equal(actionSavesProcessingNote(action), false)
  }
  assert.equal(actionSavesProcessingNote('INTERNAL_COMMENT'), true)
  assert.equal(actionSavesProcessingNote('HANDOVER'), true)
  assert.equal(actionSavesProcessingNote('RESOLVE'), false)
})

test('limits match server UTF-16 length and normalized line endings', () => {
  assert.equal(normalizeProcessingText(' a\r\nb\rc\u00a0 '), 'a\nb\nc')
  assert.equal(prepareProcessingNote('RESOLVE', '', '', '😀'.repeat(500)).ok, true)
  assert.equal(prepareProcessingNote('RESOLVE', '', '', '😀'.repeat(501)).ok, false)
})
