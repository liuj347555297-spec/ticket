export interface ServiceLaunchIntent { systemCode: string; moduleCode: string; catalogId: string }
export type ServiceLaunchResult = { kind: 'NONE' } | { kind: 'INVALID' } | { kind: 'VALID'; intent: ServiceLaunchIntent }

/** URL values are selection hints only; server-visible systems and mappings must be rechecked. */
export function parseServiceLaunch(query: Record<string, unknown>): ServiceLaunchResult {
  if (!['systemCode', 'moduleCode', 'catalogId'].some(key => query[key] !== undefined)) return { kind: 'NONE' }
  const { systemCode, catalogId } = query
  const moduleCode = query.moduleCode ?? ''
  if (typeof systemCode !== 'string' || !/^[A-Z][A-Z0-9_]{2,63}$/.test(systemCode)
    || typeof catalogId !== 'string' || !/^SC-[A-Za-z0-9_-]{3,60}$/.test(catalogId)
    || typeof moduleCode !== 'string' || (moduleCode !== '' && !/^[A-Z][A-Z0-9_]{1,63}$/.test(moduleCode))) return { kind: 'INVALID' }
  return { kind: 'VALID', intent: { systemCode, moduleCode, catalogId } }
}

export function matchesServiceSelection(expected: ServiceLaunchIntent, actual: ServiceLaunchIntent): boolean {
  return expected.systemCode === actual.systemCode && expected.moduleCode === actual.moduleCode && expected.catalogId === actual.catalogId
}
