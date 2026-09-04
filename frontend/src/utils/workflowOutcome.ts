import type { TicketActionCode } from '../api/tickets'

/** A returned ticket is not proof that a required approval or confirmation ran. */
export function workflowOutcome(action: TicketActionCode): 'COMPLETED' | 'PENDING_APPROVAL' | 'PENDING_PROCESS_TASK' {
  if (['HOLD', 'ESCALATE', 'CANCEL', 'REOPEN', 'ASSIGN', 'ACCEPT', 'RESOLVE', 'CLOSE', 'CONTROLLED_JUMP_REQUEST'].includes(action)) return 'PENDING_APPROVAL'
  if (action === 'HANDOVER' || action === 'ADD_COHANDLER') return 'PENDING_PROCESS_TASK'
  return 'COMPLETED'
}
