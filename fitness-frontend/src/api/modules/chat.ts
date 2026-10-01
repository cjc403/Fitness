import request from '@/utils/request'

export interface ChatContact {
  userId: number; name: string; roleCode?: string | null; roleName?: string | null
  venueName?: string | null; member: boolean; unread: number
}
export interface ChatContactGroups { staff: ChatContact[]; members: ChatContact[] }
export interface ChatConversation {
  userId: number; name: string; roleName?: string | null; roleCode?: string | null
  lastContent: string; lastAt: string; unread: number
}
export interface ChatMessage {
  id: number; mine: boolean; senderName: string; content: string; createdAt: string
}
export interface ChatThread {
  userId: number; name: string; roleName?: string | null; roleCode?: string | null; messages: ChatMessage[]
}
export interface ChatSummary { unreadTotal: number; conversationCount: number }

export const getChatContacts = () => request.get<ChatContactGroups>('/v1/messages/contacts')
export const getChatConversations = () => request.get<ChatConversation[]>('/v1/messages/conversations')
export const getChatSummary = () => request.get<ChatSummary>('/v1/messages/summary')
export const getChatThread = (userId: number) => request.get<ChatThread>(`/v1/messages/thread/${userId}`)
export const sendChatMessage = (recipientUserId: number, content: string) =>
  request.post<ChatMessage>('/v1/messages/send', { recipientUserId, content })
