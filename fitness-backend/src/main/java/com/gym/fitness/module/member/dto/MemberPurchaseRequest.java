package com.gym.fitness.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员自助购买请求。只允许指定商品，不允许指定金额或支付渠道——
 * 金额由服务端取商品标价，渠道固定为自助购买，防止会员端篡改资金。
 * idempotencyKey 由前端每次"购买意图"生成一个，连点或重试不会重复开卡。
 */
@Data
public class MemberPurchaseRequest {
    @NotNull
    private Long productId;
    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;
}
