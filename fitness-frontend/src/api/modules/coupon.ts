import request from '@/utils/request'

export interface CouponTemplate {
  id: number
  venueId: number
  name: string
  couponType: 'FULL_REDUCTION' | 'DISCOUNT'
  thresholdCents: number
  discountCents: number
  discountRate?: number
  totalQuantity: number
  issuedQuantity: number
  validFrom: string
  validUntil: string
  status: string
  note?: string
}

export interface MemberCoupon {
  id: number
  templateId: number
  templateName: string
  couponType: 'FULL_REDUCTION' | 'DISCOUNT'
  thresholdCents: number
  discountCents: number
  discountRate?: number
  code: string
  status: string
  validFrom: string
  validUntil: string
  discountCentsUsed: number
  usedAt?: string
  usedOrderId?: number
  usable: boolean
  unusableReason?: string
}

export interface CouponQuoteOption { memberCouponId: number; name: string; discountCents: number; payable: number }
export interface CouponQuote {
  productId: number
  productName: string
  price: number
  priceCents: number
  usableCount: number
  options: CouponQuoteOption[]
}

export const listCouponTemplates = () => request.get<CouponTemplate[]>('/v1/commercial/coupons')
export const createCouponTemplate = (data: Partial<CouponTemplate> & { name: string; couponType: string; validFrom: string; validUntil: string }) =>
  request.post<CouponTemplate>('/v1/commercial/coupons', data)
export const updateCouponTemplate = (id: number, data: Partial<CouponTemplate> & { name: string; couponType: string; validFrom: string; validUntil: string }) =>
  request.put<CouponTemplate>(`/v1/commercial/coupons/${id}`, data)
export const issueCoupon = (id: number, memberIds: number[], quantityPerMember = 1) =>
  request.post<{ issued: number }>(`/v1/commercial/coupons/${id}/issue`, { memberIds, quantityPerMember })
export const listMemberCoupons = (memberId: number) => request.get<MemberCoupon[]>(`/v1/commercial/members/${memberId}/coupons`)
export const quoteCouponForMember = (memberId: number, productId: number) =>
  request.get<CouponQuote>(`/v1/commercial/members/${memberId}/coupon-quote?productId=${productId}`)
export const listMyCoupons = () => request.get<MemberCoupon[]>('/v1/commercial/me/coupons')
export const quoteMyCoupon = (productId: number) => request.get<CouponQuote>(`/v1/commercial/me/coupon-quote?productId=${productId}`)
