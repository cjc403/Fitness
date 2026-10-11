# 潜客与独立体验预约：最终集成代码审查

日期：2026-10-09。审查当前共享工作区的未提交内容，依据 AGENTS.md、批准设计、实施计划、操作说明及前后端评审记录。只读审查；除本报告外未改文件，未启动构建、访问数据库或服务器。本报告不等同于运行验收。

## 结论

初审发现一项新增 NEW 转化路径的 Important / P2 并发一致性问题（F3），现已在下述限定复审中关闭。未发现 Critical 问题。前次评审的会话资料残留、总部建档门店列表、选项与候选查询范围、跨店名称 N+1 修复在当前源码中仍成立。随后全量测试暴露的潜客/会员时间精度问题，修复源码已通过本报告末尾的限定复审。源码审查发现已全部关闭，但最终验收仍需完整构建、浏览器和本地迁移/激活证据。既有 Retention 偶发失败不能遗漏：协调者报告已定位测试时间精度边界、仅调整测试 fixture，并通过 Retention 13/13；本审查未独立重跑该套测试。新增两项精度回归后的最终 193 项全量构建仍由协调者执行验证。

## F3 [P2] NEW 转化预加载商品，使锁后开卡继续使用等待前的在售状态与价格（已修复，保留原发现）

- 定位：`fitness-backend/src/main/java/com/gym/fitness/module/lead/service/LeadService.java:186-187`，后续第 196 行调用 CommercialService。
- 证据：LeadService 在取得门店锁之前调用 `products.findById` 并校验商品，将商品实体留在当前持久化上下文。`CommercialService.createSale:167` 随后可能等待门店锁；锁后第 181-182 行再次调用 `accessibleProduct`，其第 775 行仍是 `findById`，没有刷新。两个服务默认 REQUIRED 事务共用一级缓存，所以第二次读取可以直接返回等待前的实体。后续第 194-203 行的在售与标价校验、权益签发均使用该实体。
- 可触发时序：事务 A 持有本店 `NO KEY UPDATE` 门店锁；顾问 B 发起潜客 NEW 转化，预加载 ACTIVE 商品后等待门店锁；店长 C 用现有商品编辑接口把商品改为 INACTIVE（或降低标价）并提交；释放 A。B 获锁后仍可按旧 ACTIVE/旧标价开卡，虽然商品编辑已经完成。商品编辑 `CommercialService.updateProduct:110-121` 不等待该门店锁，故该时序可成立。
- 新增影响：普通 createSale 在门店锁后首次查询商品；潜客路径新增的锁前预加载使“等待期间已经提交的商品变化”对开卡不可见。违反本轮 NEW 仅使用本店在售商品及复用实际记账校验的要求。该结论为源码和 JPA 持久化上下文语义推导，本审查未运行并发复现。
- 最小回归：复用 `LeadIntegrationTest` 的 gatedVenue 方式，在隔离 PG 持有门店锁；等潜客转化确实等待该锁后，另一事务把对应商品改为 INACTIVE 并提交，再放行；断言转化被拒、潜客仍未转化，且没有新增会员、订单、付款或权益。另可用价格从 399 降至 299、实收仍 399 验证锁后标价校验。
- 修正方向：避免在外层事务中预缓存将由锁后财务路径读取的可变商品实体，或在正确锁顺序下重新读取并校验当前商品。不要仅再次调用 findById；应确保实际绕过旧一级缓存，并保留潜客“商品必须本店”的额外限制。需按具体修正补回归后复审。

## 验收脚本问题（已修复，不作为产品回归）

`.superpowers/sdd/2026-10-09-leads/http-qa.ps1:78` 将超标价回滚场景的期望业务码写为 1004。当前 `CommercialService.createSale:203` 抛 PARAM_ERROR，`ErrorCode:19` 对应 1001；已有 `LeadIntegrationTest.failedNewSaleRollsBackMemberOrderEntitlementAndLead` 也期待 1001。脚本会在这一步提前中断，需修正期望后再声称完成 HTTP 闭环。已通知协调者。

限定复审已重新读取第 78 行，当前期望已改为 1001，本项关闭。此确认不表示已运行完整 HTTP 脚本。

## 核对范围与其余结论

- 权限与契约：controller 的 GET/POST 分别要求 LEAD_READ/WRITE；角色目录仅为总部、店长、顾问赋权，前端路由与菜单均加同样角色交集。scope/access/锁后 visible 检查覆盖门店、顾问本人以及重放访问；请求字段、版本、枚举与接口前缀匹配批准契约。
- 幂等与财务：操作者锁串行化同账号的请求键检查；潜客锁与 expectedVersion 防异键覆盖；历史事件存指纹并追加。重放先于版本和未来时间检查，但访问校验仍先执行。NEW 事务嵌套调用实际开卡，LINK 锁会员、门店后刷新订单并重新校验手机号、会员创建/支付时间、NEW、正净实收及顾问归属；未发现可证明的重复订单/权益提交路径。
- 状态与时间：待体验限制、流失重新跟进、取消/未到店按事实恢复、15—180 分钟、同接待顾问重叠和开始后登记结果均有对应服务检查；独立体验不绑定团课容量、教室或教练。统计基于授权 cohort 且有效转化使用订单当前净实收。
- 前端异步：sessionGeneration 和用户/角色/组织/权限范围同步失效清空已加载资料，模板停止渲染；请求序列、冻结 payload、重试键与版本冲突重新加载路径一致。全门店选择器独立于 scoped 顾问选项。已检查当前相关回归源码，未独立执行前端测试或挂载模板。
- V20 仅创建 lead 三表与索引/约束；存在同门店手机唯一、订单归因唯一、单潜客待体验部分唯一索引与转化字段一致性约束。原数据保持结论仍须实际迁移前后比对证据，不能只凭 DDL 推断验收完成。
- 隔离验收脚本明确限制 HTTP 8188、PG 55439 的 leads_qa；模拟开始时间的写入针对隔离预约。runtime 将 demo 指向隔离 leads_qa，恢复副本 migration_qa 使用 prod。真实本地激活必须继续使用 prod-only，并验证已有数据；这不是线上发布授权。
- `CommercialService.lockOperator:241-252` 的 PG NO KEY UPDATE 仍互斥同操作者，同时兼容 FK KEY SHARE；未从此次改动发现新增锁顺序倒置。既有 commercial/auth/theme 脏改动不作为本轮新增缺陷泛化审查。

## 证据边界

本报告独立做的是源码、跨模块调用和已有运行日志审查，没有重新运行构建或数据库测试。前端 92 项测试及构建通过为实施者/协调者提供的结果。Retention 时间 fixture 修正及 13/13 是协调者报告，本审查未扩大范围复验；最终全量构建、HTTP/浏览器/390px及本地迁移激活仍须由协调者的新鲜证据完成验收，不能以本报告替代。

## F3 限定复审（2026-10-09）

**状态：ADDRESSED。** 本次只检查 F3 修复、两个回归及对应 RED/GREEN 日志，并核对上述 HTTP 脚本一行修正；除本报告外未改文件，未运行测试、构建或访问数据库。

- `LeadService.java:186-189` 已去除 `products.findById`，使用 `lookup.activeProductExists(productId, venueId)`。`LeadLookupRepository.java:14-16` 是 `select count(p.id)` 标量查询，带商品 ID、本店 ID 和 ACTIVE 条件，不把商品实体载入持久化上下文。
- NEW 创建会员到调用 `commercial.createSale` 的其余步骤不预加载商品；`CommercialService.createSale` 保留先获取门店锁、后首次读取商品实体并检查当前状态/价格的顺序。这修复了原 F3 指定的锁等待期间已提交下架/降价后仍命中旧实体的路径，未放宽预检的同店/在售条件或改变交易幂等、会员创建及回滚范围。
- `LeadIntegrationTest.java:50-86` 新增两个独立 HTTP 回归：真实连接持有门店锁，用 PG `pg_stat_activity` / H2 `information_schema.sessions` 确认转化已在等待锁，修改商品并提交后才让转化继续。断言下架返回 2600、降价返回 1001，同时会员/订单/权益计数不变、潜客仍 CONSULTING/version 0、事件仅建档一条；finally 恢复商品并清理该测试数据。不是只靠固定 sleep 假定竞争时序。
- 独立读取 `%TEMP%/fitchain-leads-review-fix-20261009/product-red-pg.log`：两个回归均失败，旧实现分别实际返回 0，而应为 1001 / 2600。原源码推断现已有实施者的真实 PG RED 证据。
- 独立读取同目录 `product-green-pg.log`：LeadIntegrationTest 27 项、0 失败、0 错误、0 跳过，BUILD SUCCESS；`product-green-h2.log` 同样 27 项全绿。此为检查已有执行日志，未声称本审查再次执行测试。

本次限定修复范围未发现新的可证明缺陷。F3 与脚本期望码问题均关闭；最终 191 项完整构建及产品运行验收仍以协调者后续记录为准。

## 时间精度修复限定复审（2026-10-09）

**源码状态：通过限定复审，未发现新增可证明缺陷。** 范围仅为协调者全量测试发现的 LINK 时间精度问题、`LeadService.now()` 微秒对齐、两个确定性回归与测试时钟配置；本次仍未执行构建或访问数据库。

- 独立读取 `precision-probe.log`：同一固定瞬间 `.123456900` 下，旧潜客 API/内存保留纳秒，数据库变成 `.123457`；Hibernate 生成的会员/订单 createdAt 为 `.123456`。这直接支持“后创建的会员被误判为早于潜客”的原因。订单 paidAt 为手动业务赋值，日志显示其旧纳秒值在数据库同样上舍入；不能把订单 paidAt 描述为 Hibernate 自动生成字段。
- `LeadService.java:257-260` 仅将业务时钟产生的当前时间 `truncatedTo(MICROS)`，仍使用 Asia/Shanghai。潜客及事件创建/更新、到店、结果、转化时间在写入前与数据库微秒精度一致，无修改现有历史时间或迁移旧数据。
- LINK 候选 JPQL 仍要求 `m.createdAt >= lead.createdAt` 和 `o.paidAt >= lead.createdAt`；实际 LINK 的 qualifies 仍用 `isBefore` 排除早于建档的订单/会员。门店、手机号、顾问归属、订单类型/状态、正净实收及归因唯一检查未变，没有新增分钟/秒级容差或放宽历史订单资格。
- 重放指纹、事件键和权限检查、锁顺序及版本校验位置未变；未来时间仍用严格 `isAfter(now())`，开始后登记仍比较实际预约开始时间。业务当前时刻统一为微秒分辨率，精度损失小于一微秒，未改变上海日历口径或增加业务时间宽限期。
- `LeadIntegrationTest.java:63-102` 使用固定 `.123456900` 驱动 LeadService 与实际 Hibernate CreationTimestamp。第一项检查 API 与数据库的潜客、事件、到店、预约结果等生成时间一致；第二项检查真实自动生成的 member/order createdAt，并显式设置同微秒 paidAt、清持久化缓存后验证候选和实际 LINK 成功。未用“把会员时间手动改成未来”来避开原问题。
- 可控 Hibernate clock 与 `HibernatePropertiesCustomizer` 只存在 `src/test/.../LeadIntegrationTest.java:27-40` 的显式 Import / `@TestConfiguration`；对 `src/main` 和 pom 的搜索未发现该测试时钟配置。两个固定时钟测试均在 finally 恢复服务业务时钟和共享 TIMESTAMP_CLOCK 原来源；测试类事务回滚数据。没有引入生产时钟 Bean 或固定生产时间。
- 已独立读取 `precision-red-h2.log`：2 项失败，分别为 `.123456900` 未对齐预期 `.123456`、候选预期 1 实际 0。已读取 `precision-green-pg.log` 和 `precision-green-h2.log`：各 29 项，均为 0 失败/错误/跳过，BUILD SUCCESS。这是独立核对实施者执行日志，不是本审查再次运行测试；最终完整构建结果仍由协调者记录。

本次结论只覆盖精度修复，不能把先前全量失败忽略或标为全量已通过；需最终 193 项完整验证与运行验收证据。
