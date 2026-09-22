import request from '@/utils/request'

export interface Member {
  id: number
  memberNo: string
  realName: string
  phone: string
  email?: string | null
  idCard?: string | null
  gender?: string | null
  birthday?: string | null
  address?: string | null
  salesUserId?: number | null
  coachId?: number | null
  venueId: number
  status: string
  blacklistFlag: boolean
  accountOpened: boolean
  accountUsername?: string | null
  accountStatus?: string | null
}

export interface MemberRequest {
  memberNo: string
  realName: string
  phone: string
  email?: string
  idCard?: string
  gender?: string
  birthday?: string
  address?: string
  venueId?: number | null
  status?: string
  blacklistFlag?: boolean
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export const listMembers = (params: { keyword?: string; page: number; size: number; venueId?: number }) =>
  request.get<PageResult<Member>>('/v1/admin/members', { params })

export const createMember = (data: MemberRequest) => request.post<Member>('/v1/admin/members', data)

export const updateMember = (id: number, data: MemberRequest) =>
  request.put<Member>(`/v1/admin/members/${id}`, data)

export const pauseMember = (id: number) => request.post<Member>(`/v1/admin/members/${id}/pause`)

export const renewMember = (id: number) => request.post<Member>(`/v1/admin/members/${id}/renew`)

export const openMemberAccount = (id: number, username: string, password: string) =>
  request.post(`/v1/admin/members/${id}/account`, { username, password })
export const resetMemberPassword = (id: number, newPassword: string) =>
  request.put(`/v1/admin/members/${id}/account/password`, { newPassword })
export const updateMemberAccountStatus = (id: number, status: 'ACTIVE' | 'DISABLED') =>
  request.put(`/v1/admin/members/${id}/account/status`, { status })
