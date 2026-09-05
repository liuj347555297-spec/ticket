import { apiRequest } from '@/api/client'
import type { TicketDraftFormData, TicketDraftFieldValue } from '@/utils/ticketDraft'
export interface PersonalDraftPayload { form: TicketDraftFormData; formVersion: number | null; fieldValues: Record<string,TicketDraftFieldValue> }
export interface PersonalDraftSummary { id:string; title:string; systemCode:string; catalogId:string; version:number; updatedAt:string }
export interface PersonalDraft extends PersonalDraftSummary { payload:PersonalDraftPayload; createdAt:string }
export const ticketDraftApi = {
  list:(page=1,pageSize=20)=>apiRequest<{items:PersonalDraftSummary[];page:number;pageSize:number;total:number}>(`/ticket-drafts?page=${page}&pageSize=${pageSize}`),
  get:(id:string)=>apiRequest<PersonalDraft>(`/ticket-drafts/${encodeURIComponent(id)}`),
  save:(id:string,version:number,payload:PersonalDraftPayload)=>apiRequest<PersonalDraft>(`/ticket-drafts/${encodeURIComponent(id)}`,{method:'PUT',headers:{'If-Match':`"${version}"`},body:{version,payload}}),
  delete:(id:string,version:number)=>apiRequest<void>(`/ticket-drafts/${encodeURIComponent(id)}`,{method:'DELETE',headers:{'If-Match':`"${version}"`}}),
}
