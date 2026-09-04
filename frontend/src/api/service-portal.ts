import { apiRequest } from '@/api/client'
import type { ServiceCatalogItem } from '@/api/catalog'

/** Published, permission-scoped entries resolved by the server; no demo fallback. */
export const servicePortalApi = {
  catalogItems(systemCode: string, moduleCode?: string): Promise<ServiceCatalogItem[]> {
    const query = moduleCode ? `?moduleCode=${encodeURIComponent(moduleCode)}` : ''
    return apiRequest<ServiceCatalogItem[]>(`/service-systems/${encodeURIComponent(systemCode)}/catalog-items${query}`)
  },
}
