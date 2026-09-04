// Playwright CLI setup only. Run with run-code --filename; all API requests stay mocked.
async (page) => {
  const calls = { create: [], uploads: 0, queues: {}, failedOverdue: false }
  await page.exposeFunction('uxProbe', () => calls)
  const item = { id: 'SC-UX', code: 'INC_UX', name: '页面性能问题', summary: '页面加载慢或查询超时', ticketType: 'INCIDENT', categoryCode: 'BUSINESS_SYSTEM', publishedVersion: 1, formSchemaHash: 'test', tags: [] }
  await page.route('**/api/v1/**', async route => {
    const url = route.request().url()
    const path = url.split('?')[0]
    let body = {}
    let status = 200
    if (path.endsWith('/me')) body = { user: { iamUserId: 'ux-test', displayName: '体验验收', organization: { iamOrganizationId: 'ORG-UX', name: '验收组织' } }, authorization: { roles: ['REQUESTER', 'FIRST_LINE_SUPPORT'], dataScopes: [] } }
    else if (path.endsWith('/csrf')) body = {}
    else if (path.includes('/notifications')) body = { items: [], total: 0 }
    else if (path.endsWith('/announcements')) body = []
    else if (path.endsWith('/service-systems')) body = [{ code: 'ERP', name: '集团 ERP', owningOrganizationId: 'ORG-UX', status: 'PUBLISHED', version: 1 }]
    else if (path.endsWith('/modules')) body = []
    else if (path.endsWith('/catalog-mappings')) body = [{ systemCode: 'ERP', serviceCatalogItemId: 'SC-UX', active: true, defaultMapping: true, version: 1 }]
    else if (path.endsWith('/service-catalog/items')) body = { items: [item], total: 1 }
    else if (path.endsWith('/SC-UX/form')) body = { serviceCatalogItem: item, formVersion: 1, formSchemaHash: 'test', fields: [], tagPolicy: { allowStandardTags: false, allowFreeTags: false, maxTags: 0 } }
    else if (path.endsWith('/tickets') && route.request().method() === 'POST') {
      calls.create.push({ body: route.request().postDataJSON(), key: route.request().headers()['idempotency-key'] })
      if (calls.create.length === 1) { status = 503; body = { message: 'internal error must not be shown' } }
      else body = { id: 'TKT-UX-ONE', version: 1 }
    } else if (path.endsWith('/attachments') && route.request().method() === 'POST') {
      calls.uploads++
      if (calls.uploads === 2) { status = 500; body = { message: 'private storage error must not be shown' } }
      else body = { id: 'ATT-UX-ONE', scanStatus: 'CLEAN', detectedMediaType: 'text/plain' }
    } else if (path.endsWith('/tickets')) {
      const queue = url.match(/[?&]queue=([^&]+)/)?.[1] ?? 'ALL'
      calls.queues[queue] = (calls.queues[queue] ?? 0) + 1
      if (queue === 'OVERDUE' && !calls.failedOverdue) { calls.failedOverdue = true; status = 500 }
      else body = { items: [{ id: `TKT-${queue}`, title: queue === 'MY_REQUESTED' ? '我发起的独立事项' : '待处理的独立事项', type: 'INCIDENT', status: 'IN_PROGRESS', priority: 'P2' }], total: queue === 'MY_REQUESTED' ? 3 : 1, page: 1, pageSize: 6 }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.reload()
}
