import { defineStore } from 'pinia'
import { notificationApi, type NotificationResult } from '@/api/notifications'

/** Display-only unread counter. Recipient authorization is always enforced by the API. */
export const useNotificationStore = defineStore('notifications', {
  state: (): { unreadCount: number; source: NotificationResult<{ unreadCount: number }>['source'] | null } => ({
    unreadCount: 0,
    source: null,
  }),
  actions: {
    async loadUnreadCount(): Promise<void> {
      const result = await notificationApi.unreadCount()
      this.unreadCount = result.data.unreadCount
      this.source = result.source
    },
    markLocallyRead(): void {
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },
    clear(): void { this.unreadCount = 0; this.source = null },
  },
})
