import request from '@/utils/request'

export interface CommercialOverview { monthRevenue:number; monthOrders:number; activeEntitlements:number; expiringWithin30Days:number; todayEntries:number; generatedAt:string }
export interface Product { id:number; name:string; category:string; productType:string; description?:string; validityDays:number; totalUses?:number; accessAllowed:boolean; price:number; venueId:number; status:string }
export interface Order { id:number; orderNo:string; orderType:string; memberId:number; memberNo:string; memberName:string; productId:number; productName:string; venueId:number; originalAmount:number; paidAmount:number; refundedAmount:number; paymentMethod:string; status:string; remark?:string; paidAt:string; createdAt:string }
export interface Entitlement { id:number; entitlementNo:string; memberId:number; memberNo:string; memberName:string; productId:number; productName:string; productType:string; accessAllowed:boolean; sourceOrderId:number; venueId:number; validFrom:string; validUntil:string; totalUses?:number; remainingUses?:number; reservedUses:number; availableUses?:number; status:string; frozenAt?:string; accumulatedFrozenDays:number; daysRemaining:number; reminderLevel:string }
export interface Payment { id:number; transactionNo:string; orderId:number; orderNo:string; transactionType:string; amount:number; paymentMethod:string; status:string; occurredAt:string }
export interface BusinessDocument { id:number; documentNo:string; documentType:string; orderId:number; orderNo:string; memberName:string; title:string; taxNo?:string; amount:number; status:string; issuedAt:string }
export interface AccessRecord { id:number; memberId?:number; memberNo?:string; memberName?:string; entitlementId?:number; entitlementNo?:string; venueId:number; direction:string; result:string; reason:string; scannedAt:string }
export interface AccessCode { memberNo:string; memberName:string; token:string; qrPayload:string; generatedAt:string; expiresAt:string; ttlSeconds:number }

export const getCommercialOverview=()=>request.get<CommercialOverview>('/v1/commercial/overview')
export const listProducts=()=>request.get<Product[]>('/v1/commercial/products')
export const createProduct=(data:Partial<Product>)=>request.post<Product>('/v1/commercial/products',data)
export const updateProduct=(id:number,data:Partial<Product>)=>request.put<Product>(`/v1/commercial/products/${id}`,data)
export const deleteProduct=(id:number)=>request.delete<void>(`/v1/commercial/products/${id}`)
export const listOrders=()=>request.get<Order[]>('/v1/commercial/orders')
export const createSale=(data:{memberId:number;productId:number;paymentMethod:string;paidAmount?:number;remark?:string;idempotencyKey:string})=>request.post<Order>('/v1/commercial/orders',data)
export const listEntitlements=(params?:{memberId?:number;remindersOnly?:boolean})=>request.get<Entitlement[]>('/v1/commercial/entitlements',{params})
export const freezeEntitlement=(id:number,note='会员申请冻结')=>request.post<Entitlement>(`/v1/commercial/entitlements/${id}/freeze`,{note})
export const unfreezeEntitlement=(id:number,note='会员申请解冻')=>request.post<Entitlement>(`/v1/commercial/entitlements/${id}/unfreeze`,{note})
export const renewEntitlement=(id:number,data:{additionalDays?:number;additionalUses?:number;amount:number;paymentMethod:string;note?:string})=>request.post<Entitlement>(`/v1/commercial/entitlements/${id}/renew`,data)
export const transferEntitlement=(id:number,targetMemberId:number,note='会员转卡')=>request.post<Entitlement>(`/v1/commercial/entitlements/${id}/transfer`,{targetMemberId,note})
export const refundEntitlement=(id:number,amount:number,paymentMethod='ORIGINAL',reason='协商退款')=>request.post<Entitlement>(`/v1/commercial/entitlements/${id}/refund`,{amount,paymentMethod,reason})
export const listPayments=()=>request.get<Payment[]>('/v1/commercial/payments')
export const listDocuments=()=>request.get<BusinessDocument[]>('/v1/commercial/documents')
export const issueInvoice=(orderId:number,title:string,taxNo?:string)=>request.post<BusinessDocument>(`/v1/commercial/orders/${orderId}/invoice`,{title,taxNo})
export const scanAccess=(token:string,direction='IN')=>request.post<AccessRecord>('/v1/commercial/access/scan',{token,direction})
export const listAccessRecords=()=>request.get<AccessRecord[]>('/v1/commercial/access/records')
export const getMyEntitlements=()=>request.get<Entitlement[]>('/v1/commercial/me/entitlements')
export const getMyAccessCode=()=>request.get<AccessCode>('/v1/commercial/me/access-code')
