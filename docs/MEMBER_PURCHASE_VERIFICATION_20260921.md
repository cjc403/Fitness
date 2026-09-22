# 会员自助购买会籍验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。

## 现象

管理员在「会籍与商业中心」能看到许多会员卡和课包，但会员登录后，会员中心只有「我的会籍」，
里面只显示已办理的权益，**没有任何购买入口**（空状态文案是"请联系前台"）。

## 根因

会员卡/课包的**购买能力只做了前台/店长侧**：

- `GET /api/v1/commercial/products` 需要 `PRODUCT_READ`（会员没有）；
- `POST /api/v1/commercial/orders` 需要 `SALE_WRITE`（会员没有）；
- 会员中心没有任何"浏览商品 + 购买"的页面。

也就是说，产品能力其实都在，只是**缺一个面向会员的自助入口**。

## 改动内容

### 后端

| 文件 | 改动 |
| --- | --- |
| `MembershipProductRepository.java` | 新增 `findByVenueIdAndStatusOrderByIdDesc` |
| `CommercialService.java` | 新增 `listSellableProducts()`（当前门店、上架中商品）；新增 `createSelfServiceSale(memberId, productId)`——**复用现有 `createSale`**，金额取服务端商品标价（`paidAmount=null`），支付渠道固定 `SELF_SERVICE`，备注"会员自助购买（未接入真实支付）" |
| `member/dto/MemberPurchaseRequest.java`（新增） | 只含 `productId`，**不允许会员指定金额或渠道** |
| `MemberPortalController.java` | 新增 `GET /api/v1/member/products`、`POST /api/v1/member/purchase`，鉴权 `MEMBER_SELF_SERVICE` |
| `MemberPortalService.java` | 注入 `CommercialService`，透传 `products()` / `purchase()` |

**关键设计**：会员自助购买**完全复用前台开卡的同一套闭环**（订单 → 权益 → V9 价值账户 → 支付流水 → 合同 → 收据），
只是入口和权限不同。金额由服务端决定，杜绝会员在请求里篡改。渠道新增 `SELF_SERVICE`（`payment_method VARCHAR(30)` 无 CHECK 约束，
无需迁移），在财务对账的渠道分组里会单列一栏，可追溯"这是自助购买、未接真实支付"。

### 前端

| 文件 | 改动 |
| --- | --- |
| `api/modules/memberPortal.ts` | 新增 `MemberProduct`/`MemberOrder` 类型与 `listMemberProducts()`/`purchaseMembership()` |
| `views/member/MemberHome.vue` | 新增「购买会籍」页签：列出本门店上架中的会员卡/课包（类型、价格、有效期/次数），点「立即购买」二次确认后下单；购买成功后刷新「我的会籍」；"我的会籍"空状态文案改为"可前往『购买会籍』自助办理" |
| `views/admin/CommercialCenter.vue` | `paymentText` 增加 `SELF_SERVICE:'会员自助'`，前台查看流水时能看到这个渠道 |

## 验证

- 后端 `mvn -B clean package`：**79 / 79 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS（fat jar 71,307,831 字节）。日志 `logs/wb-purchase-gate.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#memberCanPurchaseMembershipWithoutSettingPriceOrChannel`：
  1. 员工角色访问 `/api/v1/member/products` 返回 403；
  2. 会员取商品列表（本门店、上架中）；
  3. 只传 `productId` 下单 → `paidAmount` 等于商品标价、`paymentMethod` 等于 `SELF_SERVICE`；
  4. 下单后会员权益数 +1。
- 前端 `npm run build`（含 `vue-tsc`）退出 0；Node 测试 8/8。日志 `logs/wb-fe-build8.log`、`logs/wb-fe-tests9.log`。
- **线上只读验证**（本地正式库，日志 `logs/wb-purchase-online-check.txt`，未做任何真实下单）：

| 步骤 | 结果 |
| --- | --- |
| 会员 `GET /api/v1/member/products` | 返回 5 款：私教 12 节课包 ¥4680、健身 20 次卡 ¥1280、年度畅练卡 ¥2999、进阶季卡 ¥999、活力月卡 ¥399 |
| 店长访问同一接口 | `403` 权限不足 |
| 购买不存在的商品 | `商品不存在`（不落库） |

成功的下单链路由 79 项测试套件（H2）覆盖，未在正式库做真实下单。

## 说明与边界

- **"购买不需要真实付钱"**：本系统本就只做"支付登记"、不接真实支付网关。会员自助购买同样只是
  记录一条 `SELF_SERVICE` 渠道的流水，不会发起任何真实扣款。
- 财务对账里，自助购买会以 `SELF_SERVICE` 渠道单列，金额会进入"收款"统计——这是演示环境下的约定；
  若要用于真实运营，应接入支付网关后再让会员自助购买，并把渠道改成真实渠道。
- 与前台开卡一致，当前**没有幂等键**：会员连点两次会生成两张卡。前端已做"确认弹窗 + 提交中禁用按钮"，
  若需要更强的幂等保障，应给销售下单补一个幂等键（后续可做）。
