package com.gym.fitness.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gym.fitness.common.constant.ErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一响应结果封装
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    private Result() {
        this.timestamp = LocalDateTime.now();
    }
    
    private Result(int code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }
    
    /**
     * 成功响应（有数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }
    
    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data);
    }
    
    /**
     * 失败响应
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
    
    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }
    
    /**
     * 失败响应（自定义消息 + 明细数据，用于回传字段级校验结果等诊断信息）
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message, data);
    }
    
    /**
     * 失败响应（自定义码和消息）
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
