import { apiRequest } from '@/api/client'

export interface ServiceAnnouncement {
  id: string
  title: string
  body: string
  audienceScope: 'ALL' | 'ORGANIZATION'
  pinned: boolean
  effectiveFrom: string
  effectiveUntil: string
}
export interface CreateServiceAnnouncementRequest {
  title: string
  body: string
  audienceScope: 'ALL' | 'ORGANIZATION'
  targetOrganizationIamId?: string
  pinned: boolean
  effectiveUntil: string
}

export const announcementApi = {
  list(limit = 3): Promise<ServiceAnnouncement[]> {
    return apiRequest<ServiceAnnouncement[]>(`/announcements?limit=${limit}`)
  },
  create(request: CreateServiceAnnouncementRequest): Promise<ServiceAnnouncement> {
    return apiRequest<ServiceAnnouncement>('/announcements', { method: 'POST', body: request })
  },
}
