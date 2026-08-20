import { defineStore } from 'pinia'
import { identityApi, type CurrentUserResponse, type CurrentUserSource } from '@/api/identity'

export interface SessionProjection {
  iamUserId: string
  displayName: string
  organizationIamOrganizationId: string
  organizationName: string
}

// This UI state is only a display projection. Authentication is owned by the IAM integration.
export const useSessionStore = defineStore('session', {
  state: (): {
    currentUser: SessionProjection | null
    authorization: CurrentUserResponse['authorization'] | null
    source: CurrentUserSource | null
    loading: boolean
  } => ({ currentUser: null, authorization: null, source: null, loading: false }),
  actions: {
    setProjection(user: SessionProjection | null) {
      this.currentUser = user
    },
    async loadCurrentUser() {
      this.loading = true
      try {
        const result = await identityApi.getCurrentUser()
        this.source = result.source
        this.authorization = result.data?.authorization ?? null
        this.currentUser = result.data
          ? {
              iamUserId: result.data.user.iamUserId,
              displayName: result.data.user.displayName,
              organizationIamOrganizationId: result.data.user.organization.iamOrganizationId,
              organizationName: result.data.user.organization.name,
            }
          : null
      } finally {
        this.loading = false
      }
    },
  },
})
