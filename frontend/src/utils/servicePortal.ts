import type { ServiceCatalogItem } from '../api/catalog'
import type { ServiceSystem, ServiceSystemModule } from '../api/service-systems'

export const portalTypeLabels: Record<string, string> = {
  INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号与权限', PROBLEM: '问题管理', CHANGE: '变更申请',
}
const text = (value: string) => value.normalize('NFKC').trim().toLocaleLowerCase('zh-CN')
export function filterPortalSystems(systems: ServiceSystem[], keyword: string): ServiceSystem[] {
  const query = text(keyword)
  return systems.filter((system) => system.lifecycleStatus === 'PUBLISHED' && (!query || text(`${system.systemName} ${system.systemCode}`).includes(query)))
}
export function sortPortalModules(modules: ServiceSystemModule[]): ServiceSystemModule[] {
  return modules.filter((module) => module.active).slice().sort((a, b) => a.sortOrder - b.sortOrder || a.moduleName.localeCompare(b.moduleName, 'zh-CN'))
}
export function groupPortalServices(items: ServiceCatalogItem[], keyword: string) {
  const query = text(keyword)
  const groups = new Map<string, ServiceCatalogItem[]>()
  const seen = new Set<string>()
  for (const item of items) {
    if (seen.has(item.id) || (query && !text(`${item.name} ${item.code} ${item.summary ?? ''}`).includes(query))) continue
    seen.add(item.id)
    const group = groups.get(item.ticketType) ?? []
    group.push(item)
    groups.set(item.ticketType, group)
  }
  const order = Object.keys(portalTypeLabels)
  return [...groups].sort(([a], [b]) => (order.indexOf(a) < 0 ? order.length : order.indexOf(a)) - (order.indexOf(b) < 0 ? order.length : order.indexOf(b)))
    .map(([type, entries]) => ({ type, label: portalTypeLabels[type] ?? '其他服务', items: entries }))
}
export function portalTicketUrl(systemCode: string, catalogId: string, moduleCode?: string): string {
  const query = new URLSearchParams({ systemCode, catalogId })
  if (moduleCode) query.set('moduleCode', moduleCode)
  return `/tickets/new?${query.toString()}`
}
/** Each region owns a gate. Scope resets invalidate every gate before clearing data. */
export function createPortalRequestGate() {
  let generation = 0
  return {
    invalidate() { generation += 1 },
    next() { const request = ++generation; return () => request === generation },
  }
}
