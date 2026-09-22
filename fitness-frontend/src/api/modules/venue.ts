import request from '@/utils/request'

export interface Venue {
  id: number
  name: string
  code: string
  parentVenueId?: number | null
  address?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  status: string
}

export interface VenueRequest {
  name: string
  code: string
  parentVenueId?: number | null
  address?: string
  contactPhone?: string
  contactEmail?: string
  status?: string
}

export const listVenues = () => request.get<Venue[]>('/v1/admin/venues')

export const createVenue = (data: VenueRequest) => request.post<Venue>('/v1/admin/venues', data)

export const updateVenue = (id: number, data: VenueRequest) =>
  request.put<Venue>(`/v1/admin/venues/${id}`, data)
