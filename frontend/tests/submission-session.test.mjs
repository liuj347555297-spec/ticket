import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canReleaseSubmission, createSubmissionSession } from '../src/utils/submissionSession.ts'

test('retry after clearing the UI projection needs no new payload or key', async () => {
  const sent = []
  const session = createSubmissionSession(async (request, key) => {
    sent.push({ request, key })
    if (sent.length === 1) throw new TypeError('unknown outcome')
    return { id: 'TKT-ONE' }
  }, () => 'original-key')
  const input = { service: 'SC-ONE', title: 'original' }
  await assert.rejects(session.submit(input))
  input.service = ''; input.title = ''
  assert.deepEqual(await session.retry(), { id: 'TKT-ONE' })
  assert.deepEqual(sent[1], sent[0])
})

test('retry without a previous intent cannot create a request', async () => {
  let calls = 0
  const session = createSubmissionSession(async () => { calls++; return {} })
  await assert.rejects(session.retry(), /No submission intent/)
  assert.equal(calls, 0)
})

test('concurrent confirmation sends one request, then caches the successful ticket', async () => {
  let calls = 0
  let release
  const session = createSubmissionSession(async () => {
    calls++
    return new Promise(resolve => { release = resolve })
  }, () => 'key-1')
  const first = session.submit({ title: 'original' })
  const second = session.submit({ title: 'changed' })
  assert.equal(first, second)
  await Promise.resolve()
  release({ id: 'TKT-ONE' })
  assert.deepEqual(await first, { id: 'TKT-ONE' })
  assert.deepEqual(await session.submit({ title: 'another' }), { id: 'TKT-ONE' })
  assert.equal(calls, 1)
})

test('an uncertain response retries the same immutable payload and key', async () => {
  const sent = []
  let keys = 0
  const session = createSubmissionSession(async (request, key) => {
    sent.push({ request: structuredClone(request), key })
    request.values.push('transport mutation')
    if (sent.length === 1) throw new TypeError('connection interrupted')
    return { id: 'TKT-ONE' }
  }, () => `key-${++keys}`)
  const form = { title: 'original', values: ['one'] }
  await assert.rejects(session.submit(form))
  form.title = 'changed'
  form.values.push('two')
  await session.submit(form)
  assert.deepEqual(sent[0], sent[1])
  assert.equal(keys, 1)
})

test('a definitive rejection can release the intent for corrected input', async () => {
  let keys = 0
  const sent = []
  const session = createSubmissionSession(async (request, key) => {
    sent.push({ request, key })
    if (sent.length === 1) throw new Error('validation rejected')
    return 'ok'
  }, () => `key-${++keys}`)
  await assert.rejects(session.submit({ title: 'bad' }))
  session.resetRejected()
  await session.submit({ title: 'corrected' })
  assert.equal(sent[1].key, 'key-2')
  assert.equal(sent[1].request.title, 'corrected')
})

test('reset cannot release an in-flight or successful intent', async () => {
  let calls = 0
  const session = createSubmissionSession(async () => { calls++; return 'ok' }, () => 'key')
  const pending = session.submit({})
  session.resetRejected()
  assert.equal(session.submit({}), pending)
  await pending
  session.resetRejected()
  await session.submit({})
  assert.equal(calls, 1)
})

test('unknown then rejected then successful retains the original key and payload', async () => {
  let keys = 0
  let uncertain = false
  const sent = []
  const session = createSubmissionSession(async (request, key) => {
    sent.push({ request, key })
    if (sent.length === 1) throw { status: 503 }
    if (sent.length === 2) throw { status: 403 }
    return 'ok'
  }, () => `key-${++keys}`)
  for (let i = 0; i < 2; i++) {
    try { await session.submit({ title: 'original' }) }
    catch (error) {
      const release = canReleaseSubmission(error.status, uncertain)
      uncertain = !release
      if (release) session.resetRejected()
    }
  }
  await session.submit({ title: 'changed' })
  assert.equal(keys, 1)
  assert.deepEqual(sent[0], sent[2])
  assert.equal(canReleaseSubmission(422, false), true)
  assert.equal(canReleaseSubmission(409, false), false)
  assert.equal(canReleaseSubmission(undefined, false), false)
})
