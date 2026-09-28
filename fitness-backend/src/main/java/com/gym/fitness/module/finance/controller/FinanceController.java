package com.gym.fitness.module.finance.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.finance.dto.FinanceDtos.*;
import com.gym.fitness.module.finance.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor
public class FinanceController {
    private final EntitlementValueService values;
    private final RefundService refunds;
    @GetMapping("/entitlements/{id}/quote") @PreAuthorize("hasAuthority('REFUND_REQUEST')")
    public Result<Quote> quote(@PathVariable Long id){return Result.success(refunds.quote(id));}
    @PostMapping("/entitlements/{id}/reconcile") @PreAuthorize("hasAuthority('VALUE_RECONCILE')")
    public Result<Quote> reconcile(@PathVariable Long id,@Valid @RequestBody ReconcileRequest r){return Result.success(values.reconcile(id,r));}
    @PostMapping("/entitlements/{id}/verify-transfer") @PreAuthorize("hasAuthority('VALUE_TRANSFER_VERIFY')")
    public Result<Quote> verifyTransfer(@PathVariable Long id,@Valid @RequestBody TransferEvidenceRequest r){return Result.success(values.verifyTransfer(id,r));}
    @PostMapping("/refunds") @PreAuthorize("hasAuthority('REFUND_REQUEST')")
    public Result<Refund> request(@Valid @RequestBody RefundRequest r){return Result.success(refunds.request(r));}
    @GetMapping("/refunds") @PreAuthorize("hasAuthority('REFUND_REQUEST')")
    public Result<List<Refund>> list(){return Result.success(refunds.list());}
    @GetMapping("/me/refunds") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<MemberRefund>> mine(){return Result.success(refunds.mine());}
    @GetMapping("/me/entitlements/{id}/quote") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Quote> myQuote(@PathVariable Long id){return Result.success(refunds.memberQuote(id));}
    @PostMapping("/me/refunds") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Refund> myRequest(@Valid @RequestBody RefundRequest r){return Result.success(refunds.memberRequest(r));}
    @PostMapping("/me/refunds/{id}/cancel") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Refund> myCancel(@PathVariable Long id,@Valid @RequestBody DecisionRequest r){return Result.success(refunds.memberCancel(id,r.reason()));}
    @GetMapping("/me/refunds/blockers") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<Blocker>> myBlockers(@RequestParam Long entitlementId){return Result.success(refunds.memberBlockers(entitlementId));}
    @PostMapping("/me/refunds/clear-blockers") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<ClearBlockers> myClearBlockers(@RequestParam Long entitlementId){return Result.success(refunds.memberClearBlockers(entitlementId));}
    @PostMapping("/refunds/{id}/approve") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public Result<Refund> approve(@PathVariable Long id,@Valid @RequestBody DecisionRequest r){return Result.success(refunds.approve(id,r.reason()));}
    @PostMapping("/refunds/{id}/reject") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public Result<Refund> reject(@PathVariable Long id,@Valid @RequestBody DecisionRequest r){return Result.success(refunds.reject(id,r.reason()));}
    @PostMapping("/refunds/{id}/cancel") @PreAuthorize("hasAuthority('REFUND_REQUEST')")
    public Result<Refund> cancel(@PathVariable Long id,@Valid @RequestBody DecisionRequest r){return Result.success(refunds.cancel(id,r.reason()));}
    @PostMapping("/refunds/{id}/pay") @PreAuthorize("hasAuthority('REFUND_PAY')")
    public Result<Refund> pay(@PathVariable Long id,@Valid @RequestBody PayRequest r){return Result.success(refunds.pay(id,r));}
}
