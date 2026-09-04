export interface StudioScope { organizationId: string; systemCode?: string | null; serviceCatalogItemId?: string | null }
export function belongsToStudioContext(value: StudioScope, context: StudioScope): boolean {
  return Boolean(context.systemCode) && value.systemCode === context.systemCode && value.organizationId === context.organizationId
    && (!context.serviceCatalogItemId || value.serviceCatalogItemId === context.serviceCatalogItemId)
}
export function canAssociateStudio(value: StudioScope, context: StudioScope): boolean {
  if (!context.systemCode || value.organizationId !== context.organizationId) return false
  if (!value.systemCode) return !value.serviceCatalogItemId
  return value.systemCode === context.systemCode && !value.serviceCatalogItemId && Boolean(context.serviceCatalogItemId)
}
