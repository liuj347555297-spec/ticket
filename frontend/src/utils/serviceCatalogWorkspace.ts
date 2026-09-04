import type { ServiceSystemCatalogMapping, ServiceSystemCatalogMappingsInput } from '../api/service-systems'
import type { ServiceCatalogItem } from '../api/catalog'

export function createRequestScope() {
  let generation = 0
  return { next: () => ++generation, current: () => generation, accepts: (value: number) => value === generation }
}

export interface EmbeddedDesignerState { dirty: boolean; busy: boolean; uncertain: boolean }
export interface EmbeddedDesignerHandle { canLeave(): boolean }

/** Dirty is not a prohibition: the child owns the user's discard decision and pending-request safety. */
export function allowDesignerTransition(active: boolean, handle: EmbeddedDesignerHandle | undefined, state: EmbeddedDesignerState): boolean {
  if (!active) return true
  if (state.busy) return false
  return handle ? handle.canLeave() : !state.dirty && !state.uncertain
}

export function embeddedDesignerKey(identity: number, systemCode: string, catalogId?: string): string {
  return JSON.stringify([identity, systemCode, catalogId ?? null])
}

/** Same-subject refreshes must retain an in-flight, dirty, or uncertain editor in memory. */
export function preserveDesignerDuringIdentityRefresh(active: boolean, sameSubject: boolean, state: EmbeddedDesignerState): boolean {
  return active && sameSubject && (state.dirty || state.busy || state.uncertain)
}

/** Editing a module scope must not silently copy inherited system mappings into it. */
export function effectiveMappings(system: readonly ServiceSystemCatalogMapping[], module: readonly ServiceSystemCatalogMapping[], moduleSelected: boolean): ServiceSystemCatalogMapping[] {
  const activeModule = module.filter(item => item.active)
  return moduleSelected && activeModule.length ? activeModule : system.filter(item => item.active)
}

/** Enrich read-only display metadata; this does not create a managed configuration or grant actions. */
export function mergeOfferingMetadata(primary: readonly ServiceCatalogItem[], published: readonly ServiceCatalogItem[], scope: readonly ServiceSystemCatalogMapping[]): ServiceCatalogItem[] {
  const visibleIds = new Set(scope.filter(mapping => mapping.active).map(mapping => mapping.serviceCatalogItemId))
  const byId = new Map(primary.map(item => [item.id, item]))
  for (const item of published) if (visibleIds.has(item.id) && !byId.has(item.id)) byId.set(item.id, item)
  return [...byId.values()]
}

/** Send only intentional changes, including explicit deactivation for unchecked existing mappings. */
export function mappingChanges(existing: readonly ServiceSystemCatalogMapping[], selected: readonly string[], defaultId: string): ServiceSystemCatalogMappingsInput['mappings'] {
  const ids = new Set(selected)
  if (defaultId && !ids.has(defaultId)) throw new Error('默认服务必须在关联清单内。')
  const prior = new Map(existing.map(item => [item.serviceCatalogItemId, item]))
  return [...new Set([...existing.map(item => item.serviceCatalogItemId), ...selected])].flatMap(id => {
    const old = prior.get(id), active = ids.has(id), isDefault = active && id === defaultId
    if (old && old.active === active && old.isDefault === isDefault) return []
    return [{ serviceCatalogItemId: id, active, isDefault, version: old?.version ?? 0 }]
  }).sort((a, b) => Number(a.isDefault) - Number(b.isDefault))
}
