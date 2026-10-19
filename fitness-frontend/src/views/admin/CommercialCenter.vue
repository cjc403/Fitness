<template>
  <div class="commercial" v-loading="loading">
    <WorkspaceHero compact>
      <template #heading><span>COMMERCIAL OPERATIONS</span><h1>会籍与商业中心</h1><p>从商品销售、支付开卡到权益履约、退款开票与入场核销的一体化业务闭环。</p></template>
      <template #actions><el-button @click="loadAll"><el-icon><Refresh/></el-icon> 刷新</el-button></template>
    </WorkspaceHero>
    <div class="summary-grid">
      <article><small>本月净收款</small><strong>¥{{ money(overview.monthRevenue) }}</strong><span>{{ overview.monthOrders }} 笔订单</span></article>
      <article><small>生效中权益</small><strong>{{ overview.activeEntitlements }}</strong><span>会员卡与课包</span></article>
      <article class="warning"><small>30 天内到期</small><strong>{{ overview.expiringWithin30Days }}</strong><span>需要续费跟进</span></article>
      <article><small>今日入场</small><strong>{{ overview.todayEntries }}</strong><span>二维码核销</span></article>
    </div>

    <el-tabs v-model="activeTab" class="business-tabs">
      <el-tab-pane label="会籍商品" name="products">
        <div class="tab-head"><div><h2>会员卡与课包</h2><p>统一管理期限卡、次卡和私教课包。</p></div><el-button v-if="can('PRODUCT_WRITE')" type="primary" @click="openProduct"><el-icon><Plus/></el-icon> 新增商品</el-button></div>
        <div class="product-grid">
          <article v-for="item in products" :key="item.id" class="product-card" data-agent-type="product" :data-agent-id="item.id" :data-agent-venue="item.venueId" :data-agent-label="item.name" :data-agent-entity="item.name">
            <div><el-tag size="small" :type="item.category==='CARD'?'primary':'success'">{{ item.category==='CARD'?'会员卡':'课程包' }}</el-tag><el-tag size="small" :type="item.status==='ACTIVE'?'success':'info'">{{ item.status==='ACTIVE'?'销售中':'已下架' }}</el-tag></div>
            <h3>{{ item.name }}</h3><p>{{ item.description }}</p><small v-if="isSuperAdmin" class="venue-name">{{ venueName(item.venueId) }}</small><strong>¥{{ money(item.price) }}</strong>
            <footer><span>{{ item.validityDays }} 天有效</span><span>{{ item.totalUses?`${item.totalUses} 次`:'不限次数' }}</span><el-button v-if="can('PRODUCT_WRITE')" link @click="toggleProductStatus(item)">{{ item.status==='ACTIVE'?'下架':'上架' }}</el-button><el-button v-if="can('PRODUCT_WRITE')" link @click="openScope(item)">跨店适用</el-button><el-button v-if="can('PRODUCT_WRITE')" link data-agent-action="edit" @click="editProduct(item)">编辑</el-button></footer>
          </article>
        </div>
      </el-tab-pane>

      <el-dialog v-model="scopeDialog" :title="`跨店适用：${scopeProduct?.name || ''}`" width="480px">
        <p class="scope-hint">勾选后，这些门店的会员也能购买本商品（收款与开卡仍记在 <b>{{ scopeProduct ? venueName(scopeProduct.venueId) : '' }}</b>）；不勾选 = 仅限本店，与历史行为一致。退款与结算仍回到开卡门店。</p>
        <el-select v-model="scopeForm" multiple filterable style="width:100%" placeholder="选择适用门店（可多选）">
          <el-option v-for="v in venues.filter(x=>x.id!==scopeProduct?.venueId)" :key="v.id" :label="v.name" :value="v.id"/>
        </el-select>
        <template #footer><el-button @click="scopeDialog=false">取消</el-button><el-button type="primary" :loading="scopeSaving" @click="saveScope">保存</el-button></template>
      </el-dialog>
      <el-tab-pane label="销售与开卡" name="sales">
        <div class="tab-head"><div><h2>销售订单</h2><p>完成收款后自动生成会员权益、合同和收据。</p></div><el-button v-if="can('SALE_WRITE')" type="primary" @click="saleVisible=true"><el-icon><Plus/></el-icon> 新销售开卡</el-button></div>
        <el-table :data="orders" class="data-card">
          <el-table-column label="订单/会员" min-width="210"><template #default="{row}"><b>{{ row.orderNo }}</b><small>{{ row.memberName }} · {{ row.memberNo }}</small></template></el-table-column>
          <el-table-column prop="productName" label="商品" min-width="150"/>
          <el-table-column label="实收/退款" width="140"><template #default="{row}">¥{{ money(row.paidAmount) }}<small v-if="row.refundedAmount">已退 ¥{{ money(row.refundedAmount) }}</small></template></el-table-column>
          <el-table-column label="支付方式" width="110"><template #default="{row}">{{ paymentText(row.paymentMethod) }}</template></el-table-column>
          <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.status==='PAID'?'success':'warning'">{{ orderStatus(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="支付时间" width="170"><template #default="{row}">{{ dateTime(row.paidAt) }}</template></el-table-column>
          <el-table-column v-if="can('FINANCE_READ')" label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="invoice(row)">开发票</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`会员权益 (${entitlements.length})`" name="entitlements">
        <div class="tab-head"><div><h2>在馆会员权益</h2><p>支持冻结、解冻、续费、转卡、退款和到期提醒。</p></div><el-switch v-model="remindersOnly" active-text="只看提醒" @change="loadEntitlements"/></div>
        <el-table :data="entitlements" class="data-card">
          <el-table-column label="会员" min-width="145"><template #default="{row}"><b>{{ row.memberName }}</b><small>{{ row.memberNo }}</small></template></el-table-column>
          <el-table-column label="权益" min-width="190"><template #default="{row}"><b>{{ row.productName }}</b><small>{{ row.entitlementNo }}</small></template></el-table-column>
          <el-table-column label="有效期" min-width="190"><template #default="{row}">{{ row.validFrom }} 至 {{ row.validUntil }}<small>累计冻结 {{ row.accumulatedFrozenDays }} 天</small></template></el-table-column>
          <el-table-column label="剩余" width="100"><template #default="{row}">{{ row.remainingUses==null?'不限次':`${row.remainingUses} 次` }}</template></el-table-column>
          <el-table-column label="状态/提醒" width="150"><template #default="{row}"><el-tag :type="entitlementType(row.status)">{{ entitlementStatus(row.status) }}</el-tag><small v-if="row.reminderLevel!=='NONE'" class="reminder">{{ reminderText(row.reminderLevel) }}</small></template></el-table-column>
          <el-table-column v-if="can('MEMBERSHIP_WRITE') || can('REFUND_REQUEST')" label="业务操作" width="270"><template #default="{row}">
            <template v-if="can('MEMBERSHIP_WRITE')"><el-button v-if="row.status==='ACTIVE'" link @click="freeze(row)">冻结</el-button><el-button v-if="row.status==='FROZEN'" link type="success" @click="unfreeze(row)">解冻</el-button><el-button link type="primary" @click="openRenew(row)">续费</el-button><el-button link @click="openTransfer(row)">转卡</el-button></template><el-button v-if="can('REFUND_REQUEST')" link type="danger" @click="openFinanceRefund(row)">退款</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="can('FINANCE_READ')" label="财务与单据" name="finance">
        <el-tabs type="card"><el-tab-pane label="支付流水"><el-table :data="payments" class="data-card"><el-table-column prop="transactionNo" label="流水号" min-width="210"/><el-table-column prop="orderNo" label="订单号" min-width="200"/><el-table-column label="类型" width="100"><template #default="{row}"><el-tag :type="row.transactionType==='PAYMENT'?'success':'warning'">{{ row.transactionType==='PAYMENT'?'收款':'退款' }}</el-tag></template></el-table-column><el-table-column label="金额" width="130"><template #default="{row}">¥{{ money(row.amount) }}</template></el-table-column><el-table-column label="渠道" width="110"><template #default="{row}">{{ paymentText(row.paymentMethod) }}</template></el-table-column><el-table-column label="时间" width="180"><template #default="{row}">{{ dateTime(row.occurredAt) }}</template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane label="合同/收据/发票"><el-table :data="documents" class="data-card"><el-table-column prop="documentNo" label="单据编号" min-width="210"/><el-table-column label="类型" width="100"><template #default="{row}">{{ documentText(row.documentType) }}</template></el-table-column><el-table-column prop="memberName" label="会员" width="120"/><el-table-column prop="title" label="抬头/名称" min-width="180"/><el-table-column label="金额" width="120"><template #default="{row}">¥{{ money(row.amount) }}</template></el-table-column><el-table-column label="开具时间" width="180"><template #default="{row}">{{ dateTime(row.issuedAt) }}</template></el-table-column></el-table></el-tab-pane></el-tabs>
      </el-tab-pane>

      <el-tab-pane v-if="can('ACCESS_WRITE')" label="二维码入场" name="access">
        <div class="scan-panel"><div><span>QR ACCESS</span><h2>扫码入场核销</h2><p>使用扫码枪扫描会员中心二维码，或粘贴二维码内容。</p></div><el-input v-model="scanToken" size="large" placeholder="FITCHAIN://ACCESS/…" @keyup.enter="doScan"><template #append><el-button :loading="scanning" @click="doScan">核验入场</el-button></template></el-input><div v-if="scanResult" :class="['scan-result',scanResult.result.toLowerCase()]"><strong>{{ scanResult.result==='APPROVED'?'允许入场':'拒绝入场' }}</strong><span>{{ scanResult.memberName || '未知会员' }} · {{ scanResult.reason }}</span></div></div>
        <div class="tab-head"><div><h2>最近入场记录</h2><p>最多显示最近 100 条核销记录。</p></div></div>
        <el-table :data="accessRecords" class="data-card"><el-table-column label="会员" min-width="150"><template #default="{row}"><b>{{ row.memberName||'未知会员' }}</b><small>{{ row.memberNo||'—' }}</small></template></el-table-column><el-table-column prop="entitlementNo" label="使用权益" min-width="190"/><el-table-column label="结果" width="110"><template #default="{row}"><el-tag :type="row.result==='APPROVED'?'success':'danger'">{{ row.result==='APPROVED'?'通过':'拒绝' }}</el-tag></template></el-table-column><el-table-column prop="reason" label="说明" min-width="180"/><el-table-column label="时间" width="180"><template #default="{row}">{{ dateTime(row.scannedAt) }}</template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane v-if="can('MEMBERSHIP_READ')" label="私教消课与结算" name="training"><TrainingPanel @changed="loadAll" @go="onTrainingGuide" /></el-tab-pane>
      <el-tab-pane v-if="can('REFUND_REQUEST')" label="退款申请 / 审批" name="refunds"><RefundPanel ref="refundPanel" :entitlements="entitlements" :orders="orders" @changed="loadAll" /></el-tab-pane>
      <el-tab-pane v-if="can('FINANCE_REPORT_READ')" label="财务对账报表" name="finance-report"><FinanceReportPanel /></el-tab-pane>
      <el-tab-pane v-if="can('COUPON_READ')" label="优惠券" name="coupons">
        <div class="tab-head"><div><h2>优惠券</h2><p>建券并发放给会员；会员开卡时可直接抵扣，折后实收金额进精确价值账（退款按实付）。</p></div><el-button v-if="can('COUPON_WRITE')" type="primary" @click="openCoupon()"><el-icon><Plus/></el-icon> 新建优惠券</el-button></div>
        <el-table :data="couponTemplates" stripe>
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column label="力度" min-width="190"><template #default="{row}">{{ couponText(row) }}</template></el-table-column>
          <el-table-column label="有效期" min-width="180"><template #default="{row}">{{ row.validFrom }} ~ {{ row.validUntil }}</template></el-table-column>
          <el-table-column label="已发 / 总量" width="110"><template #default="{row}">{{ row.issuedQuantity }} / {{ row.totalQuantity || '不限' }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ row.status==='ACTIVE'?'启用':'停用' }}</el-tag></template></el-table-column>
          <el-table-column v-if="can('COUPON_WRITE')" label="操作" width="200" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openIssue(row)">发放</el-button><el-button link @click="openCoupon(row)">编辑</el-button><el-button link :type="row.status==='ACTIVE'?'danger':'primary'" @click="toggleCoupon(row)">{{ row.status==='ACTIVE'?'停用':'启用' }}</el-button></template></el-table-column>
        </el-table>
        <el-empty v-if="!couponTemplates.length" description="还没有优惠券，点右上角新建" :image-size="70" />
      </el-tab-pane>

    </el-tabs>

    <el-dialog v-model="productVisible" :title="editingProduct?'编辑会籍商品':'新增会籍商品'" width="570px">
      <el-form :model="productForm" label-width="90px" data-agent-type="product" :data-agent-id="editingProduct?.id || 'new'" :data-agent-venue="productForm.venueId" :data-agent-label="productForm.name">
        <el-form-item v-if="isSuperAdmin" label="所属场馆"><el-select v-model="productForm.venueId" style="width:100%"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id"/></el-select></el-form-item>
        <el-form-item label="商品名称"><el-input v-model="productForm.name"/></el-form-item><el-form-item label="商品分类"><el-radio-group v-model="productForm.category"><el-radio-button value="CARD">期限卡</el-radio-button><el-radio-button value="PACKAGE">次卡/课包</el-radio-button></el-radio-group></el-form-item><el-form-item label="商品类型"><el-select v-model="productForm.productType" style="width:100%"><el-option label="月卡" value="MONTH_CARD"/><el-option label="季卡" value="QUARTER_CARD"/><el-option label="年卡" value="YEAR_CARD"/><el-option label="次卡" value="VISIT_CARD"/><el-option label="私教课包" value="PRIVATE_PACKAGE"/></el-select></el-form-item><el-form-item label="售价" data-agent-field="price"><el-input-number v-model="productForm.price" :min="0" :precision="2" style="width:100%"/></el-form-item><el-form-item label="有效天数"><el-input-number v-model="productForm.validityDays" :min="1" style="width:100%"/></el-form-item><el-form-item v-if="productForm.category==='PACKAGE'" label="总次数"><el-input-number v-model="productForm.totalUses" :min="1" style="width:100%"/></el-form-item><el-form-item label="允许入场"><el-switch v-model="productForm.accessAllowed"/></el-form-item>        <el-form-item label="商品说明"><el-input v-model="productForm.description" type="textarea"/></el-form-item>
        <div class="form-hint">已有订单或权益的商品不能删除，请改用卡片上的「下架」；下架后会员端立即不再展示。</div>
      </el-form><template #footer><div class="dialog-footer"><el-button v-if="editingProduct && can('PRODUCT_WRITE')" type="danger" plain @click="removeProduct">删除商品</el-button><span class="footer-right"><el-button @click="productVisible=false">取消</el-button><el-button type="primary" @click="saveProduct">保存商品</el-button></span></div></template>
    </el-dialog>

    <el-dialog v-model="saleVisible" title="销售开卡" width="520px"><el-form label-width="90px"><el-form-item label="会员"><el-select v-model="saleForm.memberId" filterable style="width:100%" @change="syncMemberProducts"><el-option v-for="m in members" :key="m.id" :label="`${m.realName} · ${m.memberNo}`" :value="m.id"/></el-select></el-form-item><el-form-item label="商品"><el-select v-model="saleForm.productId" style="width:100%" @change="syncSaleAmount"><el-option v-for="p in activeProducts" :key="p.id" :label="`${p.name} · ¥${p.price}`" :value="p.id"/></el-select></el-form-item><el-form-item label="优惠券"><el-select v-model="saleForm.memberCouponId" clearable style="width:100%" :placeholder="saleCouponOptions.length ? '不使用优惠券' : '该会员暂无可用券'"><el-option v-for="o in saleCouponOptions" :key="o.memberCouponId" :label="`${o.name}（减 ¥${(o.discountCents/100).toFixed(2)}，实收 ¥${Number(o.payable).toFixed(2)}）`" :value="o.memberCouponId" /></el-select></el-form-item><el-form-item label="实收金额"><el-input-number v-model="saleForm.paidAmount" :min="0.01" :precision="2" :disabled="!!saleForm.memberCouponId" style="width:100%"/><div v-if="saleForm.memberCouponId" class="form-hint">已选优惠券：实收金额由服务端按分计算，这里填什么都不影响结果</div></el-form-item><el-form-item label="支付方式"><el-select v-model="saleForm.paymentMethod" style="width:100%"><el-option label="微信" value="WECHAT"/><el-option label="支付宝" value="ALIPAY"/><el-option label="银行卡" value="CARD"/><el-option label="现金" value="CASH"/></el-select></el-form-item><el-form-item label="备注"><el-input v-model="saleForm.remark"/></el-form-item></el-form><template #footer><el-button @click="saleVisible=false">取消</el-button><el-button type="primary" :loading="saleSubmitting" :disabled="saleSubmitting" @click="submitSale">确认收款并开卡</el-button></template></el-dialog>
    <el-dialog v-model="couponDialog" :title="editingCouponId ? '编辑优惠券' : '新建优惠券'" width="540px">
      <el-form label-width="120px">
        <el-form-item label="名称"><el-input v-model="couponForm.name" placeholder="例如：新客体验券 / 续费九折" /></el-form-item>
        <el-form-item label="类型"><el-radio-group v-model="couponForm.couponType"><el-radio-button value="FULL_REDUCTION">满减</el-radio-button><el-radio-button value="DISCOUNT">折扣</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="使用门槛（元）"><el-input-number v-model="couponForm.thresholdYuan" :min="0" :precision="2" /><span class="form-hint">0 表示无门槛</span></el-form-item>
        <el-form-item v-if="couponForm.couponType==='FULL_REDUCTION'" label="减免（元）"><el-input-number v-model="couponForm.discountYuan" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item v-else label="折扣（%）"><el-input-number v-model="couponForm.ratePercent" :min="1" :max="99" /><span class="form-hint">90 表示九折</span></el-form-item>
        <el-form-item label="有效期"><el-date-picker v-model="couponDates" type="daterange" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="发放总量"><el-input-number v-model="couponForm.totalQuantity" :min="0" /><span class="form-hint">0 表示不限量</span></el-form-item>
        <el-form-item label="备注"><el-input v-model="couponForm.note" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="couponDialog=false">取消</el-button><el-button type="primary" :loading="couponSaving" @click="saveCoupon">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="issueDialog" title="发放优惠券" width="540px">
      <p class="muted">选择要发券的会员（可多选），同一会员可发多张。</p>
      <el-select v-model="issueForm.memberIds" multiple filterable style="width:100%" placeholder="选择会员"><el-option v-for="m in members" :key="m.id" :label="`${m.realName} · ${m.memberNo}`" :value="m.id" /></el-select>
      <el-form label-width="120px" style="margin-top:14px"><el-form-item label="每人张数"><el-input-number v-model="issueForm.quantityPerMember" :min="1" :max="10" /></el-form-item></el-form>
      <template #footer><el-button @click="issueDialog=false">取消</el-button><el-button type="primary" :loading="couponSaving" @click="submitIssue">确认发放</el-button></template>
    </el-dialog>

    <el-dialog v-model="renewVisible" title="会员续费" width="500px"><el-form label-width="100px"><el-form-item label="顺延天数"><el-input-number v-model="renewForm.additionalDays" :min="1" style="width:100%"/></el-form-item><el-form-item label="增加次数"><el-input-number v-model="renewForm.additionalUses" :min="0" style="width:100%"/></el-form-item><el-form-item label="实收金额"><el-input-number v-model="renewForm.amount" :min="0.01" :precision="2" style="width:100%"/></el-form-item><el-form-item label="支付方式"><el-select v-model="renewForm.paymentMethod" style="width:100%"><el-option label="微信" value="WECHAT"/><el-option label="支付宝" value="ALIPAY"/><el-option label="银行卡" value="CARD"/><el-option label="现金" value="CASH"/></el-select></el-form-item></el-form><template #footer><el-button @click="renewVisible=false">取消</el-button><el-button type="primary" :loading="renewSubmitting" :disabled="renewSubmitting" @click="submitRenew">确认续费</el-button></template></el-dialog>

    <el-dialog v-model="transferVisible" title="会员转卡" width="480px"><el-form label-width="90px"><el-form-item label="转给会员"><el-select v-model="transferMemberId" filterable style="width:100%"><el-option v-for="m in transferCandidates" :key="m.id" :label="`${m.realName} · ${m.memberNo}`" :value="m.id"/></el-select></el-form-item></el-form><template #footer><el-button @click="transferVisible=false">取消</el-button><el-button type="primary" @click="submitTransfer">确认转卡</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/modules/auth'
import TrainingPanel from '@/components/TrainingPanel.vue'
import { refundIntent } from '@/utils/financeUi.mjs'
import { createCouponTemplate, issueCoupon, listCouponTemplates, quoteCouponForMember, updateCouponTemplate, type CouponQuoteOption, type CouponTemplate } from '@/api/modules/coupon'
import RefundPanel from '@/components/RefundPanel.vue'
import FinanceReportPanel from '@/components/FinanceReportPanel.vue'
import { listMembers, type Member } from '@/api/modules/member'
import { listVenues, type Venue } from '@/api/modules/venue'
import { createProduct, createSale, deleteProduct, freezeEntitlement, getCommercialOverview, issueInvoice, listAccessRecords, listDocuments, listEntitlements, listOrders, listPayments, listProducts, renewEntitlement, scanAccess, transferEntitlement, unfreezeEntitlement, updateProduct, type AccessRecord, type BusinessDocument, type CommercialOverview, type Entitlement, type Order, type Payment, type Product, getProductVenueScope, setProductVenueScope } from '@/api/modules/commercial'

const auth=useAuthStore(); const can=(p:string)=>auth.userInfo?.permissions?.includes(p)
const isSuperAdmin=computed(()=>auth.userInfo?.role==='SUPER_ADMIN')
const loading=ref(false), activeTab=ref('products'), remindersOnly=ref(false)
const overview=ref<CommercialOverview>({monthRevenue:0,monthOrders:0,activeEntitlements:0,expiringWithin30Days:0,todayEntries:0,generatedAt:''})
const products=ref<Product[]>([]),orders=ref<Order[]>([]),entitlements=ref<Entitlement[]>([]),payments=ref<Payment[]>([]),documents=ref<BusinessDocument[]>([]),accessRecords=ref<AccessRecord[]>([]),members=ref<Member[]>([]),venues=ref<Venue[]>([])
const scopeMap=ref<Record<number, number[]>>({}),scopeDialog=ref(false),scopeProduct=ref<Product|null>(null),scopeSaving=ref(false),scopeForm=ref<number[]>([])
const saleSubmitting=ref(false),renewSubmitting=ref(false)
let renewalIntent: { fingerprint: string; idempotencyKey: string } | undefined
const productVisible=ref(false),saleVisible=ref(false),renewVisible=ref(false),transferVisible=ref(false),editingProduct=ref<Product>(),actionEntitlement=ref<Entitlement>(),transferMemberId=ref<number>()
const productForm=reactive<any>({name:'',category:'CARD',productType:'MONTH_CARD',description:'',validityDays:30,totalUses:undefined,accessAllowed:true,price:399,status:'ACTIVE',venueId:auth.userInfo?.organizationId})
const saleForm=reactive<any>({memberId:undefined,productId:undefined,paymentMethod:'WECHAT',paidAmount:undefined,remark:'',memberCouponId:undefined})
const renewForm=reactive<any>({additionalDays:30,additionalUses:0,amount:399,paymentMethod:'WECHAT'})
const scanToken=ref(''),scanning=ref(false),scanResult=ref<AccessRecord>()
const refundPanel=ref<{startFor:(id:number)=>Promise<void>}>()
const activeProducts=computed(()=>{const member=members.value.find(m=>m.id===saleForm.memberId);return products.value.filter(p=>p.status==='ACTIVE'&&(!member||p.venueId===member.venueId||(scopeMap.value[p.id]||[]).includes(member.venueId)))})
const transferCandidates=computed(()=>members.value.filter(m=>m.id!==actionEntitlement.value?.memberId))

// ---- 优惠券 ----
const couponTemplates=ref<CouponTemplate[]>([]),saleCouponOptions=ref<CouponQuoteOption[]>([])
const couponDialog=ref(false),issueDialog=ref(false),couponSaving=ref(false)
const editingCouponId=ref<number|null>(null),issueTemplateId=ref<number|null>(null)
const couponForm=reactive({name:'',couponType:'FULL_REDUCTION',thresholdYuan:0,discountYuan:20,ratePercent:90,totalQuantity:0,note:''})
const couponDates=ref<[string,string]|undefined>()
const issueForm=reactive({memberIds:[] as number[],quantityPerMember:1})
const couponText=(row:CouponTemplate)=>row.couponType==='DISCOUNT'
  ? `${(Number(row.discountRate||1)*10).toFixed(1)} 折`+(row.thresholdCents?`（满 ¥${(row.thresholdCents/100).toFixed(2)} 可用）`:'')
  : `满 ¥${((row.thresholdCents||0)/100).toFixed(2)} 减 ¥${((row.discountCents||0)/100).toFixed(2)}`
const loadCoupons=async()=>{if(!can('COUPON_READ'))return;try{couponTemplates.value=(await listCouponTemplates()).data}catch{couponTemplates.value=[]}}
const loadSaleCoupons=async()=>{saleCouponOptions.value=[];if(!can('COUPON_READ')||!saleForm.memberId||!saleForm.productId)return;try{saleCouponOptions.value=(await quoteCouponForMember(saleForm.memberId,saleForm.productId)).data.options}catch{saleCouponOptions.value=[]}}
const openCoupon=(row?:CouponTemplate)=>{editingCouponId.value=row?.id??null;couponForm.name=row?.name??'';couponForm.couponType=row?.couponType??'FULL_REDUCTION';couponForm.thresholdYuan=row?Number(row.thresholdCents||0)/100:0;couponForm.discountYuan=row?Number(row.discountCents||0)/100:20;couponForm.ratePercent=row?Math.round(Number(row.discountRate||0.9)*100):90;couponForm.totalQuantity=row?.totalQuantity??0;couponForm.note=row?.note??'';couponDates.value=row?[row.validFrom,row.validUntil]:undefined;couponDialog.value=true}
const couponBody=()=>({name:couponForm.name.trim(),couponType:couponForm.couponType,thresholdCents:Math.round(couponForm.thresholdYuan*100),discountCents:couponForm.couponType==='FULL_REDUCTION'?Math.round(couponForm.discountYuan*100):0,discountRate:couponForm.couponType==='DISCOUNT'?couponForm.ratePercent/100:undefined,totalQuantity:couponForm.totalQuantity,validFrom:couponDates.value![0],validUntil:couponDates.value![1],note:couponForm.note,status:'ACTIVE'})
const saveCoupon=async()=>{
  if(!couponForm.name.trim())return ElMessage.warning('请填写优惠券名称')
  if(!couponDates.value)return ElMessage.warning('请选择有效期')
  if(couponForm.couponType==='FULL_REDUCTION'&&couponForm.discountYuan<=0)return ElMessage.warning('请填写减免金额')
  couponSaving.value=true
  try{
    if(editingCouponId.value)await updateCouponTemplate(editingCouponId.value,couponBody() as any)
    else await createCouponTemplate(couponBody() as any)
    ElMessage.success('优惠券已保存');couponDialog.value=false;await loadCoupons()
  }finally{couponSaving.value=false}
}
const toggleCoupon=async(row:CouponTemplate)=>{couponSaving.value=true;try{await updateCouponTemplate(row.id,{...row,thresholdCents:row.thresholdCents,discountCents:row.discountCents,discountRate:row.discountRate,status:row.status==='ACTIVE'?'INACTIVE':'ACTIVE'} as any);await loadCoupons()}finally{couponSaving.value=false}}
const openIssue=(row:CouponTemplate)=>{issueTemplateId.value=row.id;issueForm.memberIds=[];issueForm.quantityPerMember=1;issueDialog.value=true}
const submitIssue=async()=>{if(!issueForm.memberIds.length)return ElMessage.warning('请选择会员');couponSaving.value=true;try{const r=await issueCoupon(issueTemplateId.value!,issueForm.memberIds,issueForm.quantityPerMember);ElMessage.success(`已发放 ${r.data.issued} 张`);issueDialog.value=false;await loadCoupons()}finally{couponSaving.value=false}}
const loadScopes=async()=>{try{const entries=await Promise.all(products.value.map(p=>getProductVenueScope(p.id)));const map:Record<number,number[]>={};products.value.forEach((p,i)=>{map[p.id]=entries[i].data||[]});scopeMap.value=map}catch{scopeMap.value={}}}
const openScope=async(row:Product)=>{scopeProduct.value=row;scopeForm.value=(await getProductVenueScope(row.id)).data||[];scopeDialog.value=true}
const saveScope=async()=>{if(!scopeProduct.value)return;scopeSaving.value=true;try{const r=await setProductVenueScope(scopeProduct.value.id,scopeForm.value);scopeMap.value={...scopeMap.value,[scopeProduct.value.id]:r.data};ElMessage.success('适用范围已更新');scopeDialog.value=false}finally{scopeSaving.value=false}}
const loadAll=async()=>{
  loading.value=true;try{const jobs:any[]=[getCommercialOverview(),listProducts(),listOrders(),listEntitlements(),listMembers({page:1,size:100})];if(can('FINANCE_READ'))jobs.push(listPayments(),listDocuments());if(can('ACCESS_WRITE'))jobs.push(listAccessRecords());const [o,p,so,e,m,pay,doc,access]=await Promise.all(jobs);overview.value=o.data;products.value=p.data
  await loadScopes();orders.value=so.data;entitlements.value=e.data;members.value=m.data.items;if(pay)payments.value=pay.data;if(doc)documents.value=doc.data;if(access)accessRecords.value=access.data;if(isSuperAdmin.value)venues.value=(await listVenues()).data;await loadCoupons()}finally{loading.value=false}}
const loadEntitlements=async()=>{entitlements.value=(await listEntitlements({remindersOnly:remindersOnly.value})).data}
const openProduct=()=>{editingProduct.value=undefined;Object.assign(productForm,{name:'',category:'CARD',productType:'MONTH_CARD',description:'',validityDays:30,totalUses:undefined,accessAllowed:true,price:399,status:'ACTIVE',venueId:auth.userInfo?.organizationId||venues.value[0]?.id});productVisible.value=true}
const editProduct=(p:Product)=>{editingProduct.value=p;Object.assign(productForm,p);productVisible.value=true}
const saveProduct=async()=>{if(!productForm.name)return ElMessage.warning('请输入商品名称');if(isSuperAdmin.value&&!productForm.venueId)return ElMessage.warning('请选择所属场馆');editingProduct.value?await updateProduct(editingProduct.value.id,productForm):await createProduct(productForm);ElMessage.success('商品已保存');productVisible.value=false;await loadAll()}
const removeProduct=async()=>{
  if(!editingProduct.value)return
  try{await ElMessageBox.confirm(`确认删除会籍商品「${editingProduct.value.name}」？该操作不可恢复。`,'删除商品',{type:'warning',confirmButtonText:'确认删除',cancelButtonText:'取消'})}catch{return}
  await deleteProduct(editingProduct.value.id)
  ElMessage.success('商品已删除')
  productVisible.value=false
  await loadAll()
}
const toggleProductStatus=async(item:Product)=>{
  const next=item.status==='ACTIVE'?'INACTIVE':'ACTIVE'
  if(next==='INACTIVE'){
    try{await ElMessageBox.confirm(`确认下架「${item.name}」？下架后会员端将不再展示该商品，已售出的权益不受影响。`,'下架商品',{type:'warning',confirmButtonText:'确认下架',cancelButtonText:'取消'})}catch{return}
  }
  await updateProduct(item.id,{name:item.name,category:item.category,productType:item.productType,description:item.description,validityDays:item.validityDays,totalUses:item.totalUses,accessAllowed:item.accessAllowed,price:item.price,status:next,venueId:item.venueId})
  ElMessage.success(next==='ACTIVE'?'商品已上架':'商品已下架')
  await loadAll()
}
const syncMemberProducts=()=>{saleForm.productId=undefined;saleForm.paidAmount=undefined;saleForm.memberCouponId=undefined;loadSaleCoupons()}
const syncSaleAmount=()=>{saleForm.paidAmount=products.value.find(p=>p.id===saleForm.productId)?.price;saleForm.memberCouponId=undefined;loadSaleCoupons()}
const submitSale=async()=>{
  if(saleSubmitting.value)return
  if(!saleForm.memberId||!saleForm.productId||!saleForm.paidAmount)return ElMessage.warning('请完整填写销售资料')
  try{await ElMessageBox.confirm(`确认收款 ¥${saleForm.paidAmount} 并立即开卡？`,'销售确认',{type:'success'})}catch{return}
  let intent:{idempotencyKey:string}
  try{intent=refundIntent()}catch{ElMessage.error('无法生成安全随机申请编号，请检查浏览器后重试');return}
  saleSubmitting.value=true
  try{
    await createSale({...saleForm,idempotencyKey:intent.idempotencyKey})
    ElMessage.success('收款成功，会员权益、合同和收据已生成')
    saleVisible.value=false
    await loadAll();saleForm.memberCouponId=undefined;saleCouponOptions.value=[]
  }finally{saleSubmitting.value=false}
}
const freeze=async(e:Entitlement)=>{await ElMessageBox.confirm(`冻结 ${e.memberName} 的“${e.productName}”？`,'冻结权益',{type:'warning'});await freezeEntitlement(e.id);ElMessage.success('权益已冻结');await loadAll()}
const unfreeze=async(e:Entitlement)=>{await unfreezeEntitlement(e.id);ElMessage.success('权益已解冻，有效期已自动顺延');await loadAll()}
const openRenew=(e:Entitlement)=>{renewalIntent=undefined;actionEntitlement.value=e;const p=products.value.find(p=>p.id===e.productId);Object.assign(renewForm,{additionalDays:p?.validityDays||30,additionalUses:p?.totalUses||0,amount:p?.price||399,paymentMethod:'WECHAT'});renewVisible.value=true}
const submitRenew=async()=>{
  if(!actionEntitlement.value||renewSubmitting.value)return
  const id=actionEntitlement.value.id
  const body={additionalDays:renewForm.additionalDays,additionalUses:renewForm.additionalUses||undefined,amount:renewForm.amount,paymentMethod:renewForm.paymentMethod,note:renewForm.note}
  const fingerprint=JSON.stringify({id,...body})
  try{if(renewalIntent?.fingerprint!==fingerprint)renewalIntent={fingerprint,...refundIntent()}}catch{ElMessage.error('无法生成安全随机申请编号，请检查浏览器后重试');return}
  renewSubmitting.value=true
  try{
    await renewEntitlement(id,{...body,idempotencyKey:renewalIntent.idempotencyKey})
    renewalIntent=undefined
    ElMessage.success('续费成功，权益已更新');renewVisible.value=false;await loadAll()
  }finally{renewSubmitting.value=false}
}
const openTransfer=(e:Entitlement)=>{actionEntitlement.value=e;transferMemberId.value=undefined;transferVisible.value=true}
const submitTransfer=async()=>{if(!actionEntitlement.value||!transferMemberId.value)return ElMessage.warning('请选择接收会员');await transferEntitlement(actionEntitlement.value.id,transferMemberId.value);ElMessage.success('转卡成功');transferVisible.value=false;await loadAll()}
const openFinanceRefund=async(e:Entitlement)=>{activeTab.value='refunds';await nextTick();await refundPanel.value?.startFor(e.id)}
const onTrainingGuide=(target:string)=>{if(target==='shop')activeTab.value='products'}
const invoice=async(o:Order)=>{const {value}=await ElMessageBox.prompt('请输入发票抬头','开具发票',{inputValue:o.memberName});await issueInvoice(o.id,value);ElMessage.success('发票记录已生成');await loadAll()}
const doScan=async()=>{if(!scanToken.value)return ElMessage.warning('请扫描或输入二维码');scanning.value=true;try{scanResult.value=(await scanAccess(scanToken.value)).data;scanResult.value.result==='APPROVED'?ElMessage.success('核验通过，允许入场'):ElMessage.error(scanResult.value.reason);scanToken.value='';await loadAll()}finally{scanning.value=false}}
const money=(v:number)=>Number(v||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const dateTime=(v:string)=>v?new Date(v).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'—'
const paymentText=(v:string)=>({WECHAT:'微信',ALIPAY:'支付宝',CARD:'银行卡',CASH:'现金',ORIGINAL:'原路退回',SELF_SERVICE:'会员自助'}[v]||v)
const orderStatus=(v:string)=>({PAID:'已支付',PARTIALLY_REFUNDED:'部分退款',REFUNDED:'已退款'}[v]||v)
const entitlementStatus=(v:string)=>({ACTIVE:'生效中',FROZEN:'冻结中',EXPIRED:'已到期',USED_UP:'已用完',REFUNDED:'已退款',TRANSFERRED:'已转让'}[v]||v)
const entitlementType=(v:string)=>v==='ACTIVE'?'success':v==='FROZEN'?'warning':'info'
const reminderText=(v:string)=>({LOW_BALANCE:'余次不足',EXPIRED:'已到期',EXPIRING_7_DAYS:'7天内到期',EXPIRING_30_DAYS:'30天内到期'}[v]||v)
const documentText=(v:string)=>({CONTRACT:'合同',RECEIPT:'收据',INVOICE:'发票'}[v]||v)
const venueName=(id:number)=>venues.value.find(v=>v.id===id)?.name||`场馆 ${id}`
onMounted(loadAll)
</script>

<style scoped>
.commercial{max-width:1500px;margin:0 auto}.page-head,.tab-head{display:flex;align-items:center;justify-content:space-between}.page-head>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-head h1{margin:7px 0;color:#172033;font-size:29px}.page-head p,.tab-head p{margin:0;color:#8792a5}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:15px;margin:24px 0}.summary-grid article{padding:22px;border:1px solid #e5ebf4;border-radius:17px;background:#fff}.summary-grid small,.summary-grid strong,.summary-grid span{display:block}.summary-grid small{color:#8792a5}.summary-grid strong{margin:9px 0 5px;color:#172033;font-size:28px}.summary-grid span{color:#9aa4b5;font-size:12px}.summary-grid .warning strong{color:#e98728}.business-tabs{padding:8px 22px 25px;border:1px solid #e5ebf4;border-radius:18px;background:#fff}.business-tabs :deep(.el-tabs__header){margin-bottom:22px}.tab-head{margin-bottom:18px}.tab-head h2{margin:0 0 5px;color:#1c2940;font-size:20px}.tab-head p{font-size:12px}.product-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:15px}.product-card{padding:21px;border:1px solid #e5ebf4;border-radius:16px;background:linear-gradient(145deg,#fff,#f8faff)}.product-card>div{display:flex;gap:7px}.product-card h3{margin:17px 0 7px;color:#1a263b}.product-card p{height:38px;margin:0;color:#7e899d;font-size:12px;line-height:1.6}.product-card>strong{display:block;margin:16px 0;color:#2568f5;font-size:24px}.product-card footer{display:flex;align-items:center;gap:12px;padding-top:13px;border-top:1px solid #edf0f5;color:#8691a3;font-size:11px}.product-card footer .el-button{margin-left:auto}.data-card{border:1px solid #edf0f5;border-radius:13px;overflow:hidden}.data-card b,.data-card small{display:block}.data-card small{margin-top:4px;color:#939dad}.reminder{color:#e58325!important}.scan-panel{display:grid;grid-template-columns:1fr 1.2fr;align-items:center;gap:25px;margin-bottom:28px;padding:25px;border-radius:17px;background:linear-gradient(135deg,#152039,#254b86);color:#fff}.scan-panel span{color:#8cb2ff;font-size:10px;font-weight:800;letter-spacing:2px}.scan-panel h2{margin:7px 0}.scan-panel p{margin:0;color:#b9c6db;font-size:12px}.scan-result{grid-column:2;padding:12px;border-radius:10px}.scan-result strong,.scan-result span{display:block}.scan-result.approved{background:#def8eb;color:#18885e}.scan-result.denied{background:#ffe7e7;color:#c54949}@media(max-width:1050px){.summary-grid{grid-template-columns:repeat(2,1fr)}.product-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.product-grid,.summary-grid,.scan-panel{grid-template-columns:1fr}.scan-result{grid-column:1}.page-head p{display:none}}
.product-card .venue-name{display:block;margin-top:9px;color:#71809a}
.dialog-footer{display:flex;align-items:center}.dialog-footer .footer-right{display:flex;margin-left:auto}.form-hint{margin-top:2px;color:#96a1b3;font-size:12px;line-height:1.5}
.scope-hint{margin:0 0 12px;color:#5f6d85;font-size:13px;line-height:1.7}.scope-hint b{color:#243249}</style>
