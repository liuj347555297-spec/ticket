import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const view = readFileSync(new URL('../src/views/KnowledgeView.vue', import.meta.url), 'utf8')
const detail = readFileSync(new URL('../src/views/KnowledgeArticleView.vue', import.meta.url), 'utf8')
const sidebar = readFileSync(new URL('../src/components/TicketKnowledgeSidebar.vue', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/knowledge.ts', import.meta.url), 'utf8')

test('knowledge workspace exposes only backed repository, favorite, draft and review sections', () => {
  for (const label of ['知识仓库', '我的收藏', '我的草稿', '我发起的', '我的待审', '已审知识']) assert.match(view, new RegExp(label))
  assert.match(api, /\/knowledge\/documents\/favorites/)
  assert.match(api, /\/knowledge\/documents\/drafts/)
  assert.match(api, /\/knowledge\/documents\/workbench/)
  assert.match(api, /async updateDraft/)
  assert.match(api, /'If-Match'/)
  assert.match(view, /editDraft/)
  assert.match(view, /catalogApi\.listPublishedItems/)
  assert.match(view, /multiple filterable/)
  assert.match(view, /loadGeneration/)
  assert.match(view, /role === 'SERVICE_MANAGER' \|\| role === 'PLATFORM_ADMIN'/)
  assert.match(view, /editing\.value=\{id:saved\.id,version:saved\.version\}/)
})

test('article detail shows real source and version data and marks unsupported capabilities', () => {
  assert.match(detail, /历史版本/)
  assert.match(detail, /sourceTicketId/)
  assert.match(detail, /分享、纠错、知识专题、最近访客与评论尚未实现/)
  assert.doesNotMatch(detail, /浏览量|点赞数|积分排名/)
})

test('ticket knowledge sidebar emits the agreed reference payload', () => {
  assert.match(sidebar, /defineEmits<\{ reference:/)
  assert.match(sidebar, /emit\('reference', \{ id: entry\.id, title: entry\.title, url:/)
})
