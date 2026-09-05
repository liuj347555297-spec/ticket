import type { Ticket, TicketQueue } from '@/api/tickets'

export const ticketQueueCodes = ['MY_TODO', 'OVERDUE', 'TODAY_DUE', 'TODAY_COMPLETED', 'MY_DONE', 'MY_REQUESTED', 'DRAFTS', 'TO_READ', 'ALL'] as const satisfies readonly TicketQueue[]

export type TicketColumnKey = 'service' | 'requester' | 'assignee' | 'priority' | 'status' | 'created' | 'updated'

export const defaultTicketColumns: Record<TicketColumnKey, boolean> = {
  service: true,
  requester: false,
  assignee: true,
  priority: true,
  status: true,
  created: true,
  updated: false,
}

export function ticketQueueFromQuery(value: unknown, fallback: TicketQueue = 'MY_TODO'): TicketQueue {
  const candidate = Array.isArray(value) ? value[0] : value
  return typeof candidate === 'string' && (ticketQueueCodes as readonly string[]).includes(candidate) ? candidate as TicketQueue : fallback
}

export function ticketColumnStorageKey(actorId: string): string {
  return `servicehub.ticket-list.columns:${encodeURIComponent(actorId || 'anonymous')}`
}

export function mergeTicketColumns(value: unknown): Record<TicketColumnKey, boolean> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return { ...defaultTicketColumns }
  const saved = value as Record<string, unknown>
  return Object.fromEntries(Object.entries(defaultTicketColumns).map(([key, initial]) => [key, typeof saved[key] === 'boolean' ? saved[key] : initial])) as Record<TicketColumnKey, boolean>
}

function csvCell(value: unknown): string {
  let text = value == null ? '' : String(value).replace(/\r\n?/g, '\n')
  if (/^[=+\-@]/.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}

export function ticketCsv(tickets: readonly Ticket[], labels: { type: Record<string, string>; status: Record<string, string> }): string {
  const header = ['工单编号', '主题', '工单类型', '服务目录', '申请人', '当前处理人', '优先级', '状态', '发起时间', '更新时间']
  const rows = tickets.map(ticket => [ticket.id, ticket.title, labels.type[ticket.type] ?? ticket.type, ticket.serviceCatalogItem.name, ticket.requester.displayName, ticket.assignee?.displayName ?? '', ticket.priority, labels.status[ticket.status] ?? ticket.status, ticket.createdAt, ticket.updatedAt ?? ticket.createdAt])
  return '\uFEFF' + [header, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n')
}
