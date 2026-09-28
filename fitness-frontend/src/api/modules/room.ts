import request from '@/utils/request'

export interface Room {
  id: number; venueId: number; name: string; capacity: number; status: string; note?: string
}
export interface RoomSession {
  scheduleId: number; courseName?: string; coachName?: string
  start: string; end: string; enrolledCount?: number; maxStudents?: number
}
export interface RoomOccupancy {
  roomId: number; roomName: string; capacity: number; usedMinutes: number
  occupancyRate: number; sessions: RoomSession[]
}
export const listRooms = () => request.get<Room[]>('/v1/admin/rooms')
export const createRoom = (data: { name: string; capacity?: number; status?: string; note?: string; venueId?: number }) =>
  request.post<Room>('/v1/admin/rooms', data)
export const updateRoom = (id: number, data: { name: string; capacity?: number; status?: string; note?: string }) =>
  request.put<Room>(`/v1/admin/rooms/${id}`, data)
export const deleteRoom = (id: number) => request.delete<void>(`/v1/admin/rooms/${id}`)
export const getRoomOccupancy = (date?: string) =>
  request.get<RoomOccupancy[]>('/v1/admin/rooms/occupancy', { params: date ? { date } : {} })
