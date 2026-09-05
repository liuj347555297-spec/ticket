export interface PageSlice<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export function paginateRows<T>(rows: readonly T[], page: number, pageSize: number): PageSlice<T> {
  const safeSize = Math.max(1, Math.trunc(pageSize) || 1)
  const pages = Math.max(1, Math.ceil(rows.length / safeSize))
  const safePage = Math.min(Math.max(1, Math.trunc(page) || 1), pages)
  const start = (safePage - 1) * safeSize
  return { items: rows.slice(start, start + safeSize), total: rows.length, page: safePage, pageSize: safeSize }
}

export interface SystemFilters {
  keyword: string
  status: string
  organization: string
  owner: string
}

export interface FilterableSystem {
  systemCode: string
  systemName: string
  lifecycleStatus: string
  owningOrganizationId: string
  ownerIamUserId?: string
}

export function filterSystems<T extends FilterableSystem>(rows: readonly T[], filters: SystemFilters): T[] {
  const keyword = filters.keyword.trim().toLocaleLowerCase()
  const organization = filters.organization.trim().toLocaleLowerCase()
  const owner = filters.owner.trim().toLocaleLowerCase()
  return rows.filter((row) => {
    if (keyword && !`${row.systemCode} ${row.systemName}`.toLocaleLowerCase().includes(keyword)) return false
    if (filters.status && row.lifecycleStatus !== filters.status) return false
    if (organization && !row.owningOrganizationId.toLocaleLowerCase().includes(organization)) return false
    if (owner && !(row.ownerIamUserId ?? '').toLocaleLowerCase().includes(owner)) return false
    return true
  })
}
