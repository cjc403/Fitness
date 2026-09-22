# 会籍商品删除 / 下架 + 会员端商品同步 验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。

本轮包含两件事（第二件是上一轮被打断、未完成的需求）。

## 一、会籍商品没有删除功能

### 数据约束（先查清楚）

| 关系 | 约束 |
| --- | --- |
| `sales_order.product_id → membership_product` | NO ACTION |
| `member_entitlement.product_id → membership_product` | NO ACTION |

**一旦卖过就不能物理删除**——订单、权益、以及依赖订单的财务报表都需要这条商品记录。

本地正式库实况（11 个商品）：**9 个已被订单与权益引用，只有 2 个（滨江店的「健身 20 次卡」「私教 12 节课包」）从未卖出。**

所以只做"删除"是不够的：9/11 的商品会永远删不掉。**「下架」才是卖过之后把商品从销售列表拿掉的正确做法**，
于是本轮同时补上删除与下架/上架。

### 改动

**后端**

| 文件 | 改动 |
| --- | --- |
| `CommercialController.java` | 新增 `DELETE /api/v1/commercial/products/{id}`（`PRODUCT_WRITE`） |
| `CommercialService.java` | 新增 `deleteProduct`：有订单或权益时**拒绝**并说明条数、提示改用下架 |
| `SalesOrderRepository.java` / `MemberEntitlementRepository.java` | 各加 `countByProductId` |

**前端**

| 文件 | 改动 |
| --- | --- |
| `api/modules/commercial.ts` | 新增 `deleteProduct` |
| `views/admin/CommercialCenter.vue` | 编辑弹窗左下角新增「删除商品」（危险色 + 二次确认）；商品卡片新增「下架 / 上架」；弹窗底部加说明"已有订单或权益的商品不能删除，请改用卡片上的「下架」" |

下架/上架**复用已有的 `PUT /api/v1/commercial/products/{id}`**（`ProductRequest` 本来就带 `status`），
没有新增后端语义，前端只补了入口。

## 二、会员端与员工端的在售商品不一致（上一轮遗留）

会员端此前只返回**本人门店**的商品（5 款），员工端能看到**全部门店**（10 款），两边对不上。

**改动**：`CommercialService.listMemberShopProducts()` 改为返回**全部门店的"上架中"商品**，
每条附带 `venueName` 与由服务端判定的 `purchasable`。
会员**只能购买本人所属门店的商品**（`createSale` 要求"会员与商品必须属于同一场馆"这一规则没有放宽），
其他门店的商品在会员端**只展示、按钮禁用**并标注「仅限该店会员购买」。

新增 `CommercialDtos.MemberProductView(id, name, productType, description, validityDays, totalUses, price, venueId, venueName, purchasable)`；
`GET /api/v1/member/products` 返回类型随之调整。前端会员端会籍商城显示场馆名与"仅限该店会员购买"标记。

**实时同步**：会员端在切到「购买会籍」页签时**每次都会重新拉取**，
所以员工端新增商品或上下架后，会员端刷新该页签即可看到最新列表（下架的会立刻消失）。

## 验证

- 后端 `mvn -B clean package`：**81 / 81 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS（fat jar 71,310,870 字节）。日志 `logs/wb-pdel-gate4.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#productDeletionIsRefusedOnceSoldAndOffSaleHidesItFromTheMemberShop`：
  教练无 `PRODUCT_WRITE` 被拒；从未卖出的商品可删除且从列表消失；**已有订单的商品删除被拒（2600）**；
  **下架后会员端不再展示、上架后恢复**（测试结束还原为 ACTIVE）。
- 已有测试 `#memberCanPurchaseMembershipWithoutSettingPriceOrChannel` 已同步调整：
  会员端现在返回全部在售商品，必须挑 `purchasable=true` 的那款下单，并校验每条都带 `purchasable` 与 `venueName`。
- 前端 `npm run build`（含 `vue-tsc`）退出 0；Node 测试 8/8。日志 `logs/wb-fe-build10.log`、`logs/wb-fe-tests11.log`。
- **线上只读验证**（本地正式库，未删除任何商品，日志 `logs/wb-pdel-online-check.txt`）：

| 步骤 | 结果 |
| --- | --- |
| 员工端（超管）商品 | 11 个，全部「销售中」（总部 6 个、滨江店 5 个） |
| 会员端（cjc2，总部）商品 | **11 款**，其中本门店可购买 6 款，滨江店 5 款标注「仅展示（非本门店）」 |
| **两端一致性** | **员工端在售 11 款 = 会员端展示 11 款 ✓** |
| 删除已被引用的商品 | `2600`「该商品已有 1 笔订单、1 份权益，不能删除；请改为下架」 |
| 删除不存在的商品 | `1004 商品不存在` |
| 商品总数 | 验证前后一致，**0 条被改动** |

期间两次构建失败均为**我写测试时的疏漏**（都是漏字段，与产品代码无关）：
① 超管创建商品漏了 `venueId`（服务端要求必须指定场馆）；
② 用 `PUT` 还原商品状态时漏了 `totalUses`（服务端要求"课包/次卡必须填写总次数"）。补齐后全绿。

## 说明

- 删除是**物理删除**，仅限从未产生订单与权益的商品（例如刚建错的模板）。这类商品删掉不影响任何历史。
- 已售商品的正确操作是**下架**：会员端立即不再展示，但既有会员的权益、订单、财务报表完全不受影响，随时可以再上架。
- 没有做"批量删除"，也没有放宽"会员可购买其他门店商品"的规则——后者属于业务规则变更，需要另行确认。
