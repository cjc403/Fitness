package com.gym.fitness.common.constant;

import lombok.Getter;

/**
 * 全局错误码枚举
 * 成功: 0
 * 系统错误: 1000+
 * 业务错误: 2000+
 */
@Getter
public enum ErrorCode {
    
    // 成功
    SUCCESS(0, "操作成功"),
    
    // 系统错误 1000-1999
    SYSTEM_ERROR(1000, "系统错误"),
    PARAM_ERROR(1001, "参数错误"),
    UNAUTHORIZED(1002, "未授权"),
    FORBIDDEN(1003, "权限不足"),
    NOT_FOUND(1004, "资源不存在"),
    METHOD_NOT_ALLOWED(1005, "请求方法不允许"),
    INTERNAL_SERVER_ERROR(1006, "服务器内部错误"),
    SERVICE_UNAVAILABLE(1007, "服务不可用"),
    MEDIA_TYPE_NOT_SUPPORTED(1008, "不支持的请求内容类型"),
    
    // 认证授权错误 1100-1199
    TOKEN_INVALID(1100, "Token无效"),
    TOKEN_EXPIRED(1101, "Token已过期"),
    LOGIN_FAILED(1102, "用户名或密码错误"),
    ACCOUNT_DISABLED(1103, "账号已被禁用"),
    ACCOUNT_LOCKED(1104, "账号已被锁定"),
    PASSWORD_CHANGE_REQUIRED(1105, "请先修改初始密码"),
    
    // 业务错误 2000+
    // 用户相关 2000-2099
    USER_NOT_FOUND(2000, "用户不存在"),
    USER_ALREADY_EXISTS(2001, "用户已存在"),
    USERNAME_ALREADY_EXISTS(2002, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(2003, "邮箱已存在"),
    PHONE_ALREADY_EXISTS(2004, "手机号已存在"),
    
    // 会员相关 2100-2199
    MEMBER_NOT_FOUND(2100, "会员不存在"),
    MEMBER_ALREADY_EXISTS(2101, "会员已存在"),
    MEMBER_CARD_EXPIRED(2102, "会员卡已过期"),
    MEMBER_CARD_INVALID(2103, "会员卡无效"),
    MEMBER_BLACKLISTED(2104, "会员已加入黑名单"),
    
    // 课程相关 2200-2299
    COURSE_NOT_FOUND(2200, "课程不存在"),
    SCHEDULE_NOT_FOUND(2201, "排课不存在"),
    SCHEDULE_FULL(2202, "课程已满员"),
    SCHEDULE_NOT_BOOKABLE(2203, "课程不可预约"),
    
    // 预约相关 2300-2399
    BOOKING_NOT_FOUND(2300, "预约不存在"),
    BOOKING_ALREADY_EXISTS(2301, "已存在预约"),
    BOOKING_CANCEL_TOO_LATE(2302, "取消预约时间已过"),
    BOOKING_EXPIRED(2303, "预约已过期"),
    
    // 合同相关 2400-2499
    CONTRACT_NOT_FOUND(2400, "合同不存在"),
    CONTRACT_ALREADY_SIGNED(2401, "合同已签署"),
    CONTRACT_NOT_APPROVED(2402, "合同未审批"),
    
    // 场馆相关 2500-2599
    VENUE_NOT_FOUND(2500, "场馆不存在"),
    VENUE_ALREADY_EXISTS(2501, "场馆已存在"),
    
    // 其他业务错误 2600+
    OPERATION_NOT_ALLOWED(2600, "操作不允许"),
    DATA_ALREADY_EXISTS(2601, "数据已存在"),
    INSUFFICIENT_PERMISSIONS(2602, "权限不足");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
