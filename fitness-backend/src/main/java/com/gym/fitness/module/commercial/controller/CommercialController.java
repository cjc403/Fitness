package com.gym.fitness.module.commercial.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.commercial.dto.CommercialDtos.*;
import com.gym.fitness.module.commercial.service.CommercialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/commercial")
@RequiredArgsConstructor
public class CommercialController {
    private final CommercialService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('SALE_READ','MEMBERSHIP_READ')")
    public Result<Map<String, Object>> overview() { return Result.success(service.overview()); }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public Result<List<ProductResponse>> products() { return Result.success(service.listProducts()); }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public Result<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return Result.success(service.createProduct(request));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public Result<ProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.success(service.updateProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        service.deleteProduct(id);
        return Result.success();
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('SALE_READ')")
    public Result<List<OrderResponse>> orders() { return Result.success(service.listOrders()); }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('SALE_WRITE')")
    public Result<OrderResponse> sale(@Valid @RequestBody SaleRequest request) { return Result.success(service.createSale(request)); }

    @GetMapping("/entitlements")
    @PreAuthorize("hasAuthority('MEMBERSHIP_READ')")
    public Result<List<EntitlementResponse>> entitlements(@RequestParam(required = false) Long memberId,
                                                          @RequestParam(defaultValue = "false") boolean remindersOnly) {
        return Result.success(service.listEntitlements(memberId, remindersOnly));
    }

    @PostMapping("/entitlements/{id}/freeze")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    public Result<EntitlementResponse> freeze(@PathVariable Long id, @RequestBody(required = false) NoteRequest request) {
        return Result.success(service.freeze(id, request));
    }

    @PostMapping("/entitlements/{id}/unfreeze")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    public Result<EntitlementResponse> unfreeze(@PathVariable Long id, @RequestBody(required = false) NoteRequest request) {
        return Result.success(service.unfreeze(id, request));
    }

    @PostMapping("/entitlements/{id}/renew")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE') and hasAuthority('SALE_WRITE')")
    public Result<EntitlementResponse> renew(@PathVariable Long id, @Valid @RequestBody RenewalRequest request) {
        return Result.success(service.renew(id, request));
    }

    @PostMapping("/entitlements/{id}/transfer")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    public Result<EntitlementResponse> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return Result.success(service.transfer(id, request));
    }

    @PostMapping("/entitlements/{id}/refund")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE') and hasAuthority('SALE_WRITE')")
    public Result<EntitlementResponse> refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        return Result.success(service.refund(id, request));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Result<List<PaymentResponse>> payments() { return Result.success(service.listPayments()); }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Result<List<DocumentResponse>> documents() { return Result.success(service.listDocuments()); }

    @PostMapping("/orders/{id}/invoice")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Result<DocumentResponse> invoice(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return Result.success(service.issueInvoice(id, request));
    }

    @PostMapping("/access/scan")
    @PreAuthorize("hasAuthority('ACCESS_WRITE')")
    public Result<AccessResponse> scan(@Valid @RequestBody AccessScanRequest request) {
        return Result.success(service.scanAccess(request));
    }

    @GetMapping("/access/records")
    @PreAuthorize("hasAuthority('ACCESS_WRITE')")
    public Result<List<AccessResponse>> accessRecords() { return Result.success(service.listAccessRecords()); }

    @GetMapping("/me/entitlements")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<EntitlementResponse>> myEntitlements() { return Result.success(service.myEntitlements()); }

    @GetMapping("/me/access-code")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<AccessCodeResponse> myAccessCode() { return Result.success(service.myAccessCode()); }
}
