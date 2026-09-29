import request from '@/utils/request'

export interface ChannelView {
  code: 'IN_APP' | 'SMS' | 'EMAIL'; name: string; enabled: boolean; mock: boolean
  provider?: string | null; note?: string
}
export interface Delivery {
  id: number; channel: string; category: string; recipientMasked: string
  memberId?: number | null; venueId?: number | null; subject?: string | null; content?: string | null
  status: 'SENT' | 'FAILED' | 'SKIPPED'; provider?: string | null
  providerMessageId?: string | null; error?: string | null; createdAt: string
}

export const getMessageChannels = () => request.get<ChannelView[]>('/v1/admin/messages/channels')
export const listDeliveries = () => request.get<Delivery[]>('/v1/admin/messages/deliveries')
export const sendMessage = (data: { memberId: number; channels?: string[]; category: string; subject: string; content: string }) =>
  request.post<Delivery[]>('/v1/admin/messages/send', data)
