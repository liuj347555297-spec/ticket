// Playwright CLI run-code fixture. Intercepts every API request, never writes real tickets.
async page => {
  const a = 'TKT-20260903-990001', b = 'TKT-20260903-990002'
  const date = '2026-09-03T08:00:00Z'
  const identity = { iamUserId: 'iam-ui-handler', displayName: '验收处理人', organizationName: '验收运维组', capturedAt: date }
  const calls = [], comments = [], approvals = []
  let commentFailed = false
  const actions = () => [{ code: 'INTERNAL_COMMENT', label: '内部评论' }, ...(!approvals.length ? [{ code: 'RESOLVE', label: '解决' }] : []), { code: 'TRANSFER', label: '转办', requiresTarget: true }, { code: 'HOLD', label: '挂起', disabledReason: '当前节点不支持挂起' }]
  const ticket = id => ({ id, type: 'INCIDENT', status: id === a ? 'PENDING_USER_FEEDBACK' : 'CLOSED', priority: 'P2', title: id === a ? 'ERP 采购页面查询缓慢' : '只读工单校验', description: '采购订单查询超过10秒，影响同部门用户。', requester: { ...identity, displayName: '验收申请人' }, assignee: identity, serviceCatalogItem: { id: 'SC-UI', name: '业务系统 - 性能问题' }, version: 3, createdAt: date, updatedAt: date, availableActions: id === a ? actions() : [] })
  await page.exposeFunction('processingProbe', () => calls)
  await page.route('**/api/v1/**', async route => {
    const path = route.request().url().split('?')[0]
    const id = path.includes(b) ? b : a
    let body = {}, status = 200
    if (path.endsWith('/me')) body = { user: { iamUserId: identity.iamUserId, displayName: identity.displayName, organization: { iamOrganizationId: 'ORG-UI', name: identity.organizationName } }, authorization: { roles: ['SECOND_LINE_SUPPORT'], dataScopes: [] } }
    else if (path.includes('/notifications')) body = { items: [], total: 0, page: 1, pageSize: 100 }
    else if (path.endsWith('/csrf')) body = {}
    else if (path.endsWith('/knowledge/documents')) body = [{ id: 'KB-UI', title: 'ERP 慢查询排查步骤', owningOrganizationId: 'ORG-UI', serviceCatalogItemIds: ['SC-UI'], categoryCode: 'BUSINESS_SYSTEM', tags: [], status: 'PUBLISHED', currentVersionId: 'KV-1', updatedAt: date }]
    else if (path.endsWith('/workflow/actions')) {
      const request = route.request().postDataJSON()
      calls.push({ ticketId: id, request, version: route.request().headers()['if-match'] })
      if (request.action === 'INTERNAL_COMMENT' && !commentFailed) { status = 500; commentFailed = true }
      else if (request.action === 'INTERNAL_COMMENT') comments.push({ id: `COMMENT-${calls.length}`, body: request.comment, authorIamUserId: identity.iamUserId, createdAt: date })
      else if (request.action === 'RESOLVE') approvals.push({ id: 'LA-UI', action: 'RESOLVE', applicantIamUserId: identity.iamUserId, reason: request.reason, status: 'PENDING_APPROVAL', processKey: 'lifecycleAction', processVersion: 1, createdAt: date })
      body = status === 200 ? ticket(id) : { message: 'private implementation details' }
    } else if (path.endsWith('/workflow')) body = { tasks: id === a ? [{ nodeKey: 'user_feedback', status: 'CLAIMED', assigneeIamUserId: identity.iamUserId }] : [], participants: [{ role: 'PRIMARY', identity, assignedAt: date }], availableActions: id === a ? actions() : [], comments: id === a ? comments : [], events: [], approvalRequests: [], approvalDecisions: [], handoverRequests: [], coHandlerRequests: [], lifecycleApprovalRequests: id === a ? approvals : [], controlledJumpActions: [] }
    else if (path.endsWith('/sla')) body = { policyNameSnapshot: '业务系统标准服务', responseDueAt: date, resolutionDueAt: '2026-09-04T08:00:00Z', pausedSeconds: 0, riskLevel: 'ON_TRACK', calculatedAt: date }
    else if (path.endsWith('/attachments')) body = []
    else if (path.endsWith('/relations')) body = id === a ? [{ relationType: 'RELATED', direction: 'OUTBOUND', relatedTicket: ticket(b) }] : []
    else if (path.endsWith(a) || path.endsWith(b)) body = ticket(id)
    else { status = 404; body = {} }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto(`http://127.0.0.1:1525/tickets/${a}`)
}
