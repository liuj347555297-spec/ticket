import { defineStore } from 'pinia'

export interface SessionProjection {
  iamUserId: string
  displayName: string
  organizationName: string
}

// This UI state is only a display projection. Authentication is owned by the IAM integration.
export const useSessionStore = defineStore('session', {
  state: (): { currentUser: SessionProjection | null } => ({ currentUser: null }),
  actions: {
    setProjection(user: SessionProjection | null) {
      this.currentUser = user
    },
  },
})
