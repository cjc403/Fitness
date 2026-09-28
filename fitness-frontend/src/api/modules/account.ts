import request from '@/utils/request'
import type { PageResult } from './member'

export interface EmployeeAccount {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  roleCode: string
  roleName: string
  venueId: number
  venueName: string
  status: string
  mustChangePassword: boolean
  lastLoginAt?: string
  createdAt?: string
}

export interface EmployeeAccountRequest {
  username: string
  password: string
  realName: string
  phone?: string
  email?: string
  roleCode: string
  venueId?: number
}

export interface MemberRegistrationRequest {
  username: string
  password: string
  realName: string
  phone: string
  email?: string
  venueCode: string
}

export interface VenueOption { code: string; name: string }

/** 当前登录人可创建的角色（由后端给出，避免前后端各维护一份）。 */
export interface RoleOption { code: string; name: string }
export const listCreatableRoles = () => request.get<RoleOption[]>('/v1/admin/accounts/roles')
export const listEmployees = (params: { keyword?: string; roleCode?: string; status?: string; venueId?: number; page: number; size: number }) =>
  request.get<PageResult<EmployeeAccount>>('/v1/admin/accounts/employees', { params })
export const createEmployee = (data: EmployeeAccountRequest) => request.post<EmployeeAccount>('/v1/admin/accounts/employees', data)
export const updateEmployee = (id: number, data: Omit<EmployeeAccountRequest, 'username' | 'password'>) =>
  request.put<EmployeeAccount>(`/v1/admin/accounts/employees/${id}`, data)
export const updateEmployeeStatus = (id: number, status: 'ACTIVE' | 'DISABLED') =>
  request.put<EmployeeAccount>(`/v1/admin/accounts/employees/${id}/status`, { status })
export const resetEmployeePassword = (id: number, newPassword: string) =>
  request.put<EmployeeAccount>(`/v1/admin/accounts/employees/${id}/password`, { newPassword })
export const registerMember = (data: MemberRegistrationRequest) => request.post('/v1/auth/register/member', data)
export const listPublicVenues = () => request.get<VenueOption[]>('/v1/auth/venues')
