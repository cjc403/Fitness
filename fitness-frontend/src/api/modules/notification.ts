import request from '@/utils/request'

export interface NotificationItem {
  id: number
  category: string
  title: string
  body?: string
  refType?: string
  refId?: number
  createdAt: string
  read: boolean
}

export interface NotificationSummary { unread: number }

export const listNotifications = () => request.get<NotificationItem[]>('/v1/notifications')
export const getNotificationSummary = () => request.get<NotificationSummary>('/v1/notifications/summary')
export const markNotificationRead = (id: number) => request.post(`/v1/notifications/${id}/read`)
export const markAllNotificationsRead = () => request.post<NotificationSummary>('/v1/notifications/read-all')
