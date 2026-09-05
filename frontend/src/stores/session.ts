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
    applyCurrentUser(value: CurrentUserResponse, source: CurrentUserSource = 'api') {
      this.source = source
      this.authorization = value.authorization
      this.currentUser = {
        iamUserId: value.user.iamUserId,
        displayName: value.user.displayName,
        organizationIamOrganizationId: value.user.organization.iamOrganizationId,
        organizationName: value.user.organization.name,
      }
    },
    clearSession() {
      this.currentUser = null
      this.authorization = null
      this.source = 'unauthenticated'
    },
    setProjection(user: SessionProjection | null) {
      this.currentUser = user
    },
    async loadCurrentUser() {
      this.loading = true
      try {
        const result = await identityApi.getCurrentUser()
        if (result.data) this.applyCurrentUser(result.data, result.source)
        else this.clearSession()
      } catch (error) {
        // Never retain a previous subject or capability projection after identity refresh fails.
        this.currentUser = null
        this.authorization = null
        this.source = null
        throw error
      } finally {
        this.loading = false
      }
    },
  },
})
