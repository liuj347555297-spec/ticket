import { apiRequest } from '@/api/client'

export type ServiceSystemLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'

/**
 * Service-system registry is owned by ServiceHub.  It is routing metadata which
 * may reference a CMDB CI, rather than a client-side CMDB replica.
 */
export interface ServiceSystem {
  systemCode: string
  systemName: string
  ciId?: string
  ownerIamUserId?: string
  owningOrganizationId: string
  lifecycleStatus: ServiceSystemLifecycleStatus
  version: number
  changeReason?: string
  publishedAt?: string
}

export interface ServiceSystemModule {
  systemCode: string
  moduleCode: string
  moduleName: string
  modulePath?: string
  active: boolean
  sortOrder: number
  version: number
}

export interface ServiceSystemCatalogMapping {
  systemCode: string
  moduleCode?: string
  serviceCatalogItemId: string
  active: boolean
  isDefault: boolean
  version: number
}

export interface ServiceSystemPage {
  items: ServiceSystem[]
  page: number
  pageSize: number
  total: number
}

export interface ServiceSystemDraftInput {
  version: number
  systemCode: string
  systemName: string
  ciId?: string
  ownerIamUserId?: string
  owningOrganizationId: string
  lifecycleStatus: ServiceSystemLifecycleStatus
  reason: string
}

export interface ServiceSystemModuleInput {
  version: number
  moduleCode: string
  moduleName: string
  modulePath?: string
  active: boolean
  sortOrder: number
}

export interface ServiceSystemCatalogMappingsInput {
  version: number
  moduleCode?: string
  mappings: Array<Pick<ServiceSystemCatalogMapping, 'serviceCatalogItemId' | 'active' | 'isDefault' | 'version'>>
}

const writeHeaders = (version?: number): Record<string, string> => ({
  'Idempotency-Key': crypto.randomUUID(),
  ...(version === undefined ? {} : { 'If-Match': `\"${version}\"` }),
})

type SystemWire = { code: string; name: string; configurationItemId?: string; ownerIamUserId?: string; owningOrganizationId: string; status: ServiceSystemLifecycleStatus; version: number; changeReason?: string; publishedAt?: string }
type ModuleWire = { systemCode: string; code: string; name: string; path?: string; active: boolean; sortOrder: number; version: number }
type MappingWire = { systemCode: string; moduleCode?: string; serviceCatalogItemId: string; active: boolean; defaultMapping: boolean; version: number }
const systemFromWire = (value: SystemWire): ServiceSystem => ({ systemCode: value.code, systemName: value.name, ciId: value.configurationItemId, ownerIamUserId: value.ownerIamUserId, owningOrganizationId: value.owningOrganizationId, lifecycleStatus: value.status, version: value.version, changeReason: value.changeReason, publishedAt: value.publishedAt })
const moduleFromWire = (value: ModuleWire): ServiceSystemModule => ({ systemCode: value.systemCode, moduleCode: value.code, moduleName: value.name, modulePath: value.path, active: value.active, sortOrder: value.sortOrder, version: value.version })
const mappingFromWire = (value: MappingWire): ServiceSystemCatalogMapping => ({ systemCode: value.systemCode, moduleCode: value.moduleCode, serviceCatalogItemId: value.serviceCatalogItemId, active: value.active, isDefault: value.defaultMapping, version: value.version })
const systemRequest = (input: ServiceSystemDraftInput) => ({ code: input.systemCode, name: input.systemName, configurationItemId: input.ciId, ownerIamUserId: input.ownerIamUserId, owningOrganizationId: input.owningOrganizationId, version: input.version, reason: input.reason })

/** Public read contract. The backend applies IAM organization scope before returning data. */
export const serviceSystemApi = {
  list(): Promise<ServiceSystemPage> {
    return apiRequest<SystemWire[]>('/service-systems?page=1&pageSize=100').then((items) => ({ items: items.map(systemFromWire), page: 1, pageSize: 100, total: items.length }))
  },
  listModules(systemCode: string): Promise<ServiceSystemModule[]> {
    return apiRequest<ModuleWire[]>(`/service-systems/${encodeURIComponent(systemCode)}/modules`).then((items) => items.map(moduleFromWire))
  },
  listCatalogMappings(systemCode: string, moduleCode?: string): Promise<ServiceSystemCatalogMapping[]> {
    const query = moduleCode ? `?moduleCode=${encodeURIComponent(moduleCode)}` : ''
    return apiRequest<MappingWire[]>(`/service-systems/${encodeURIComponent(systemCode)}/catalog-mappings${query}`).then((items) => items.map(mappingFromWire))
  },
}

/** Back-office contract. Every mutation is optimistic-locked and server-audited. */
export const serviceSystemAdminApi = {
  list(): Promise<ServiceSystemPage> {
    return apiRequest<{ items: SystemWire[]; page: number; pageSize: number; total: number }>('/admin/service-systems?page=1&pageSize=100').then((page) => ({ ...page, items: page.items.map(systemFromWire) }))
  },
  get(systemCode: string): Promise<ServiceSystem> {
    return apiRequest<SystemWire>(`/admin/service-systems/${encodeURIComponent(systemCode)}`).then(systemFromWire)
  },
  create(input: ServiceSystemDraftInput): Promise<ServiceSystem> {
    return apiRequest<SystemWire>('/admin/service-systems', { method: 'POST', headers: writeHeaders(), body: systemRequest(input) }).then(systemFromWire)
  },
  update(systemCode: string, input: ServiceSystemDraftInput): Promise<ServiceSystem> {
    return apiRequest<SystemWire>(`/admin/service-systems/${encodeURIComponent(systemCode)}`, { method: 'PUT', headers: writeHeaders(input.version), body: systemRequest(input) }).then(systemFromWire)
  },
  listModules(systemCode: string): Promise<ServiceSystemModule[]> {
    return apiRequest<ModuleWire[]>(`/admin/service-systems/${encodeURIComponent(systemCode)}/modules`).then((items) => items.map(moduleFromWire))
  },
  createModule(systemCode: string, input: ServiceSystemModuleInput): Promise<ServiceSystemModule> {
    return apiRequest<ModuleWire>(`/admin/service-systems/${encodeURIComponent(systemCode)}/modules/${encodeURIComponent(input.moduleCode)}`, { method: 'PUT', headers: writeHeaders(0), body: { name: input.moduleName, path: input.modulePath, active: input.active, sortOrder: input.sortOrder } }).then(moduleFromWire)
  },
  updateModule(systemCode: string, moduleCode: string, input: ServiceSystemModuleInput): Promise<ServiceSystemModule> {
    return apiRequest<ModuleWire>(`/admin/service-systems/${encodeURIComponent(systemCode)}/modules/${encodeURIComponent(moduleCode)}`, { method: 'PUT', headers: writeHeaders(input.version), body: { name: input.moduleName, path: input.modulePath, active: input.active, sortOrder: input.sortOrder } }).then(moduleFromWire)
  },
  listCatalogMappings(systemCode: string, moduleCode?: string): Promise<ServiceSystemCatalogMapping[]> {
    const path = moduleCode ? `/admin/service-systems/${encodeURIComponent(systemCode)}/modules/${encodeURIComponent(moduleCode)}/catalog-mappings` : `/admin/service-systems/${encodeURIComponent(systemCode)}/catalog-mappings`
    return apiRequest<MappingWire[]>(path).then((items) => items.map(mappingFromWire))
  },
  saveCatalogMappings(systemCode: string, input: ServiceSystemCatalogMappingsInput): Promise<ServiceSystemCatalogMapping[]> {
    return Promise.all(input.mappings.map((mapping) => { const path = input.moduleCode ? `/admin/service-systems/${encodeURIComponent(systemCode)}/modules/${encodeURIComponent(input.moduleCode)}/catalog-mappings/${encodeURIComponent(mapping.serviceCatalogItemId)}` : `/admin/service-systems/${encodeURIComponent(systemCode)}/catalog-mappings/${encodeURIComponent(mapping.serviceCatalogItemId)}`; return apiRequest<MappingWire>(path, { method: 'PUT', headers: writeHeaders(mapping.version), body: { active: mapping.active, defaultMapping: mapping.isDefault } }).then(mappingFromWire) }))
  },
}
