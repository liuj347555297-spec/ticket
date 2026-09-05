import type { TicketActionCode } from '../api/tickets'

/** A returned ticket is not proof that a required approval or confirmation ran. */
export function workflowOutcome(action: TicketActionCode, resultingStatus?: string): 'COMPLETED' | 'PENDING_APPROVAL' | 'PENDING_PROCESS_TASK' {
  if (action === 'ACCEPT') return resultingStatus === 'IN_PROGRESS' ? 'COMPLETED' : 'PENDING_APPROVAL'
  if (action === 'RESOLVE') return resultingStatus === 'RESOLVED' ? 'COMPLETED' : 'PENDING_APPROVAL'
  if (action === 'CLOSE') return resultingStatus === 'CLOSED' ? 'COMPLETED' : 'PENDING_APPROVAL'
  if (['HOLD', 'ESCALATE', 'CANCEL', 'REOPEN', 'ASSIGN', 'CONTROLLED_JUMP_REQUEST'].includes(action)) return 'PENDING_APPROVAL'
  if (action === 'HANDOVER' || action === 'ADD_COHANDLER') return 'PENDING_PROCESS_TASK'
  return 'COMPLETED'
}
