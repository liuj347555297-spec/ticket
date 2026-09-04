export type TicketDraftFieldValue = string | boolean | string[]

export interface TicketDraftFormData {
  systemCode: string
  moduleCode: string
  catalogId: string
  type: string
  title: string
  descriptionHtml: string
  descriptionText: string
  tags: string[]
}

export interface TicketDraft {
  schemaVersion: 1
  subjectId: string
  formVersion: number | null
  createdAt: number
  updatedAt: number
  form: TicketDraftFormData
  fieldValues: Record<string, TicketDraftFieldValue>
}

const STORAGE_PREFIX = 'servicehub:ticket-create-draft:v1:'
export const TICKET_DRAFT_TTL_MS = 24 * 60 * 60 * 1000
const SENSITIVE_FIELD = /(pass(word)?|token|secret|cookie|credential|private.?key|certificate|密码|口令|令牌|密钥|凭据|私钥|证书)/i
const SENSITIVE_ASSIGNMENT = /((?:pass(?:word)?|token|secret|cookie|credential|private.?key|密码|口令|令牌|密钥|凭据|私钥)\s*[:=：]\s*)[^\s<]{2,}/gi

function storageKey(subjectId: string): string {
  return `${STORAGE_PREFIX}${encodeURIComponent(subjectId)}`
}

// Session storage survives refreshes in the same tab but is cleared when the browser session ends.
// This deliberately avoids leaving ticket content behind on shared workstations.
function draftStorage(): Storage { return sessionStorage }

function canStoreField(code: string): boolean {
  return /^[a-zA-Z][a-zA-Z0-9_.-]{0,99}$/.test(code) && !SENSITIVE_FIELD.test(code)
}

function safeFieldValues(values: Record<string, TicketDraftFieldValue>): Record<string, TicketDraftFieldValue> {
  return Object.fromEntries(Object.entries(values).filter(([code, value]) => {
    if (!canStoreField(code)) return false
    if (typeof value === 'boolean') return true
    if (typeof value === 'string') return value.length <= 10_000
    return Array.isArray(value) && value.length <= 100 && value.every((item) => typeof item === 'string' && item.length <= 500)
  }).map(([code, value]) => [code, typeof value === 'string' ? redactSensitiveValues(value) : Array.isArray(value) ? value.map(redactSensitiveValues) : value]))
}

function redactSensitiveValues(value: string): string {
  return value
    .replace(SENSITIVE_ASSIGNMENT, '$1[草稿未保存敏感值]')
    .replace(/\b(?:blob:|data:)[^"'\s>]+/gi, '')
}

function safeForm(form: TicketDraftFormData): TicketDraftFormData {
  return {
    systemCode: form.systemCode.slice(0, 100),
    moduleCode: form.moduleCode.slice(0, 100),
    catalogId: form.catalogId.slice(0, 100),
    type: form.type.slice(0, 50),
    // Free text and custom tags are intentionally memory-only. Content regexes cannot
    // reliably identify credentials, personal data, internal logs or pending filenames.
    title: '',
    descriptionHtml: '',
    descriptionText: '',
    tags: [],
  }
}

function isDraft(value: unknown): value is TicketDraft {
  if (!value || typeof value !== 'object') return false
  const draft = value as Partial<TicketDraft>
  const form = draft.form as Partial<TicketDraftFormData> | undefined
  return draft.schemaVersion === 1
    && typeof draft.subjectId === 'string'
    && typeof draft.createdAt === 'number'
    && typeof draft.updatedAt === 'number'
    && Boolean(form && typeof form.systemCode === 'string' && typeof form.moduleCode === 'string' && typeof form.catalogId === 'string'
      && typeof form.type === 'string' && typeof form.title === 'string' && typeof form.descriptionHtml === 'string'
      && typeof form.descriptionText === 'string' && Array.isArray(form.tags) && form.tags.every((tag) => typeof tag === 'string'))
    && Boolean(draft.fieldValues && typeof draft.fieldValues === 'object')
}

export function readTicketDraft(subjectId: string, now = Date.now()): TicketDraft | null {
  try {
    const key = storageKey(subjectId)
    const raw = draftStorage().getItem(key)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isDraft(parsed) || parsed.subjectId !== subjectId || parsed.createdAt > now + 5 * 60_000 || parsed.createdAt + TICKET_DRAFT_TTL_MS <= now) {
      draftStorage().removeItem(key)
      return null
    }
    return { ...parsed, form: safeForm(parsed.form), fieldValues: safeFieldValues(parsed.fieldValues) }
  } catch {
    return null
  }
}

export function writeTicketDraft(input: Omit<TicketDraft, 'schemaVersion' | 'createdAt' | 'updatedAt'>, now = Date.now()): TicketDraft | null {
  try {
    const previous = readTicketDraft(input.subjectId, now)
    const draft: TicketDraft = {
      ...input,
      schemaVersion: 1,
      createdAt: previous?.createdAt ?? now,
      updatedAt: now,
      form: safeForm(input.form),
      fieldValues: safeFieldValues(input.fieldValues),
    }
    draftStorage().setItem(storageKey(input.subjectId), JSON.stringify(draft))
    return draft
  } catch {
    return null
  }
}

export function removeTicketDraft(subjectId: string): void {
  try { draftStorage().removeItem(storageKey(subjectId)) } catch { /* Storage may be unavailable by browser policy. */ }
}
