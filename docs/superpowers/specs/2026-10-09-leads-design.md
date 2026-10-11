# 潜客与体验课管理（本地第一版）

用户已批准新功能清单1—5，本轮开始第2项；2026-10-09明确选择独立体验预约。以真实门店运营为目标，本轮不发布线上。

## 业务边界

- 潜客独立于会员：咨询来源、姓名（最多50字符）、大陆11位手机号、意向、负责顾问、备注、下次联系时间。手机号在同一门店唯一，创建后不改；来源创建后固定，保持渠道归因。
- 顾问仅看本人且同门店的潜客；店长看本店；总部可跨店查看。前台、教练、会员暂不开放潜客资料。归属人只能为本店在职会籍顾问；只有店长/总部可更换顾问。
- 跟进结果：CONTACTED 已联系、NO_CONTACT 未联系上、VISITED 已到店、INTERESTED 有意向、DECLINED 已流失、REOPEN 重新跟进。记录不可覆盖；DECLINED 必须先处理待体验预约，CONVERTED不可再次跟进。
- 每个潜客最多一个BOOKED预约；15—180分钟，开始时间晚于当前上海时间。接待人是本店在职顾问，同一接待人不能有时间重叠的BOOKED体验预约。体验独立于会员团课、候补、扣课和教练排课，不预留教室/教练资源；录入体验内容，接待顾问负责协调实际活动。
- 预约结果 ATTENDED 已体验、NO_SHOW 未到店、CANCELLED 已取消。已体验/未到店只能在预约开始后登记；记录结果及说明。允许取消后重新预约，历史全部保留。
- 阶段由业务动作推进：CONSULTING 咨询中、VISITED 已到店、TRIAL_BOOKED 待体验、TRIAL_COMPLETED 已体验、LOST 已流失、CONVERTED 已开卡。体验取消后根据已发生事实恢复阶段。
- 转化有两种方式：①创建新会员并复用CommercialService完成真实记账开卡；②关联已经办理的NEW订单。关联必须同门店、同联系电话、同归属顾问（顾问操作时）、会员创建和订单支付均不早于潜客建档，并有正净实收的PAID/PARTIALLY_REFUNDED订单。一个订单不能归给多个潜客。不伪造支付，不接入新支付网关。
- 新会员自动生成编号LEAD-{潜客ID}，不自动开登录账号；已有会员资料/账号不覆盖。遇到同手机号会员时提供合格订单候选，若不合格提示使用已有会员流程。潜客建档不生成会员/订单/权益。
- 开卡失败：会员、订单、权益、转化状态全部回滚。重试同一请求只执行一次；更改内容必须使用新键。所有写请求必填idempotencyKey（最多100字符）；除创建外必填expectedVersion，避免多人覆盖。重试查验优先于“日期必须未来”和版本检查，但每次重新核验访问权限。
- 渠道统计以可见范围内已建档潜客为分母；到店、已体验人数去重；有效开卡以所关联NEW订单当前净实收>0为准。全额退款后仍保留CONVERTED历史，但有效转化数减少。过滤/分页不改变统计口径。

## 稳定API契约

路径前缀 `/api/v1/leads`；前端request前缀用`/v1/leads`。所有响应沿用Result包装。

GET `/workbench?venueId=&stage=ALL&filter=ALL&keyword=&page=1&size=20`

返回 `{summary,channels,leads:{records,total,page,size}}`。
summary `{totalLeads,newThisMonth,todayFollowUps,overdueFollowUps,todayTrials,convertedLeads,conversionRate}`。
channels `[{source,total,visited,trialCompleted,converted,conversionRate}]`（百分比0—100）。
filter `ALL|TODAY|OVERDUE|TRIAL_TODAY`；stage `ALL`或上述阶段；总部venueId可选，其余只能本店。

LeadView `{id,version,name,phone,source,intent,ownerId,ownerName,venueId,venueName,stage,note,nextFollowAt,visitedAt,trialCompletedAt,convertedMemberId,convertedOrderId,convertedAt,conversionNetAmount,createdAt,updatedAt,nextTrial}`。
TrialView `{id,leadId,hostId,hostName,startsAt,endsAt,content,status,resultNote,recordedAt,createdAt}`。
EventView `{id,type,actorName,content,outcome,nextFollowAt,createdAt}`。
Detail `{lead,events,trials}`。

GET `/{id}` → Detail。
GET `/options?venueId=` → `{venues:[{id,name}],advisors:[{id,name,venueId}],products:[{id,name,price,venueId,productType}]}`（产品仅本店在售，SALES顾问列表仅本人，但预约可见本店顾问姓名为必要接待选项，统一返回本店可选顾问，不包含电话号码）。
GET `/{id}/conversion-options` → `[{orderId,orderNo,memberId,memberNo,memberName,productName,netAmount,paidAt}]`，仅返回经校验的候选。

POST `/` `{name,phone,source,intent,ownerId,venueId,note?,nextFollowAt?,idempotencyKey}` → Detail。
source `WALK_IN|REFERRAL|DOUYIN|WECHAT|OTHER`，intent `HIGH|MEDIUM|LOW`。
POST `/{id}/profile` `{expectedVersion,name,intent,ownerId,note?,idempotencyKey}` → Detail；手机号、来源、门店不可改。
POST `/{id}/follow-ups` `{expectedVersion,method,content,outcome,nextFollowAt?,idempotencyKey}` → Detail；method `CALL|WECHAT|VISIT|OTHER`。
POST `/{id}/trials` `{expectedVersion,hostId,startsAt,endsAt,content,idempotencyKey}` → Detail。
POST `/{id}/trials/{trialId}/result` `{expectedVersion,status,note,idempotencyKey}` → Detail。
POST `/{id}/convert` `{expectedVersion,mode,productId?,paymentMethod?,paidAmount?,orderId?,idempotencyKey}` → Detail；mode `NEW|LINK`。NEW必填productId/paymentMethod/paidAmount，渠道只允许`CASH|WECHAT|ALIPAY|CARD`，金额至少0.01且最多两位小数；LINK必填orderId。转化时不能有待处理体验预约。直接开卡无需强制经历体验。

## 数据与验证

只加V20迁移与lead模块表：sales_lead、lead_event、lead_trial。既有V1—V19不变。数据库外键、状态约束、门店手机号唯一、请求键唯一、转化订单唯一、单潜客BOOKED部分唯一索引。

所有新功能通过真实HTTP集成测试覆盖角色/门店/归属、非法字段、时间与状态、预约冲突、同键重试与指纹冲突、不同键并发、转化回滚与订单一致性、退款统计。前端运行行为测试覆盖重复点击、重试键、旧请求/换账号/卸载、版本冲突提示；构建验证、桌面/390px浏览器验收。真实本地数据库只做结构迁移和只读验收；测试数据只在隔离数据库。

## 扩展点

独立module.lead与API，事件追加保存。后续可新增渠道配置、接待教练/教室资源联动、营销活动关联和前台受限登记权限；这些不在本轮扩展。保留现有角色端主题和动漫装饰。
