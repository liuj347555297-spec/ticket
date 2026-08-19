import type { Ticket, TicketCreateRequest, TicketPage, TicketQuery } from '@/api/tickets'

const demoRequester = {
  iamUserId: 'iam-u-000821',
  displayName: '访客用户',
  organizationName: '数字化运营中心 / 应用服务部',
  positionName: '业务专员',
  capturedAt: '2026-08-19T09:12:00+08:00',
}

const demoAssignee = {
  iamUserId: 'iam-u-000063',
  displayName: '李工',
  organizationName: '数字化运营中心 / 应用运维组',
  positionName: '应用运维工程师',
  capturedAt: '2026-08-19T09:26:00+08:00',
}

let tickets: Ticket[] = [
  {
    id: 'TKT-20260819-000421', type: 'INCIDENT', status: 'IN_PROGRESS', priority: 'P2',
    title: 'ERP 采购订单页面加载缓慢', description: '采购订单列表查询超过 15 秒，影响日常提交。',
    requester: demoRequester, assignee: demoAssignee,
    serviceCatalogItem: { id: 'CAT-ERP-PERFORMANCE', name: '业务系统 - 页面性能问题' },
    tags: [{ name: '#ERP', kind: 'STANDARD' }, { name: '#采购订单', kind: 'FREE' }, { name: '#页面卡顿', kind: 'STANDARD' }],
    createdAt: '2026-08-19T09:12:00+08:00', updatedAt: '2026-08-19T09:28:00+08:00', version: 3,
  },
  {
    id: 'TKT-20260818-000380', type: 'ACCESS_REQUEST', status: 'PENDING_ACCEPTANCE', priority: 'P3',
    title: '申请财务共享系统报表查看权限', description: '需查看本部门月度费用报表。', requester: demoRequester,
    serviceCatalogItem: { id: 'CAT-FIN-ACCESS', name: '账号与权限 - 角色申请' },
    tags: [{ name: '#财务共享', kind: 'STANDARD' }, { name: '#权限申请', kind: 'STANDARD' }],
    createdAt: '2026-08-18T14:03:00+08:00', updatedAt: '2026-08-18T14:10:00+08:00', version: 1,
  },
  {
    id: 'TKT-20260816-000299', type: 'SERVICE_REQUEST', status: 'RESOLVED', priority: 'P4',
    title: '办公电脑安装 PDF 阅读器', description: '安装已纳入软件白名单的阅读工具。', requester: demoRequester,
    serviceCatalogItem: { id: 'CAT-SOFTWARE-INSTALL', name: '软件服务 - 白名单软件安装' },
    tags: [{ name: '#软件安装', kind: 'STANDARD' }],
    createdAt: '2026-08-16T10:21:00+08:00', updatedAt: '2026-08-16T11:06:00+08:00', version: 2,
  },
]

function matches(ticket: Ticket, query: TicketQuery): boolean {
  if (query.status && ticket.status !== query.status) return false
  if (query.type && ticket.type !== query.type) return false
  if (!query.q) return true
  const key = query.q.toLocaleLowerCase()
  return [ticket.id, ticket.title, ticket.serviceCatalogItem.name, ...(ticket.tags?.map((tag) => tag.name) ?? [])]
    .join(' ')
    .toLocaleLowerCase()
    .includes(key)
}

export const demoTicketRepository = {
  list(query: TicketQuery): TicketPage {
    const page = query.page ?? 1
    const pageSize = query.pageSize ?? 20
    const filtered = tickets.filter((ticket) => matches(ticket, query))
    return { items: filtered.slice((page - 1) * pageSize, page * pageSize), page, pageSize, total: filtered.length }
  },
  get(ticketId: string): Ticket {
    const ticket = tickets.find((item) => item.id === ticketId)
    if (!ticket) throw new Error('演示工单不存在')
    return ticket
  },
  create(request: TicketCreateRequest): Ticket {
    const index = tickets.length + 422
    const now = new Date().toISOString()
    const ticket: Ticket = {
      id: `TKT-20260819-${String(index).padStart(6, '0')}`,
      type: request.type,
      status: 'SUBMITTED',
      priority: 'P3',
      title: request.title,
      description: request.description,
      requester: { ...demoRequester, capturedAt: now },
      serviceCatalogItem: { id: request.serviceCatalogItemId, name: '待后端目录解析' },
      tags: request.tags,
      createdAt: now,
      updatedAt: now,
      version: 0,
    }
    tickets = [ticket, ...tickets]
    return ticket
  },
}
