import type { TicketActionCode } from '../api/tickets'

export const PROCESSING_NOTE_LIMIT = 2000
export const RESOLUTION_REASON_LIMIT = 1000
/** Only these backend branches actually persist an operation reason. */
export const processingReasonActions: readonly TicketActionCode[] = ['HANDOVER', 'ADD_COHANDLER', 'CONTROLLED_JUMP_REQUEST', 'HOLD', 'ESCALATE', 'CANCEL', 'REOPEN', 'ASSIGN', 'RESOLVE']
export const resolutionCategories = ['故障修复', '配置调整', '操作指导', '服务完成', '其他'] as const
export type ProcessingNoteField = 'processing' | 'resolution'
export interface ProcessingActionPayload { reason: string; detail: string }
export type ProcessingNoteResult =
  | { ok: true; payload: ProcessingActionPayload }
  | { ok: false; field: ProcessingNoteField; message: string }

export function actionSavesProcessingNote(action: TicketActionCode): boolean {
  return action === 'INTERNAL_COMMENT' || (action !== 'RESOLVE' && processingReasonActions.includes(action))
}

/** Match the server's UTF-16 String length; never silently truncate an operator's evidence. */
export function normalizeProcessingText(value: string): string {
  return value.replace(/\r\n?/g, '\n').replace(/\u00a0/g, ' ').trim()
}

export function composeResolutionReason(category: string, explanation: string): string {
  const body = normalizeProcessingText(explanation)
  return category ? `解决类别：${category}\n解决说明：${body}` : body
}

export function prepareProcessingNote(action: TicketActionCode, processing: string, category: string, resolution: string): ProcessingNoteResult {
  const note = normalizeProcessingText(processing)
  if (action === 'INTERNAL_COMMENT') {
    if (!note) return { ok: false, field: 'processing', message: '请先填写处理意见，再点击“保存处理意见”。' }
    if (note.length > PROCESSING_NOTE_LIMIT) return { ok: false, field: 'processing', message: `处理意见最多 ${PROCESSING_NOTE_LIMIT} 字符，请精简后重试。` }
    return { ok: true, payload: { reason: '记录处理进展', detail: note } }
  }
  if (action === 'RESOLVE') {
    if (category && !resolutionCategories.some((item) => item === category)) return { ok: false, field: 'resolution', message: '请选择有效的解决类别。' }
    if (!normalizeProcessingText(resolution)) return { ok: false, field: 'resolution', message: '请填写解决说明，说明处理结果和验证情况。' }
    const reason = composeResolutionReason(category, resolution)
    if (reason.length > RESOLUTION_REASON_LIMIT) return { ok: false, field: 'resolution', message: `解决类别和说明合计最多 ${RESOLUTION_REASON_LIMIT} 字符，请精简后重试。` }
    return { ok: true, payload: { reason, detail: '' } }
  }
  if (!processingReasonActions.includes(action)) return { ok: true, payload: { reason: '', detail: '' } }
  if (note.length > RESOLUTION_REASON_LIMIT) return { ok: false, field: 'processing', message: `此操作的原因最多 ${RESOLUTION_REASON_LIMIT} 字符，请精简处理意见，或先单独保存处理意见。` }
  return { ok: true, payload: { reason: note, detail: '' } }
}
