package com.gym.fitness.common.exception;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.response.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * <p>分层约定：客户端错误（参数、方法、媒体类型、路径）必须映射到对应的 4xx 并给出可读原因，
 * 只有真正的服务端故障才落到 500。这样前端能显示准确提示，Prometheus/日志的 5xx 指标也不被污染。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }
    
    /**
     * 处理请求体参数校验异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(Exception e) {
        BindException bindException = e instanceof MethodArgumentNotValidException
                ? (MethodArgumentNotValidException) e
                : (BindException) e;
        Map<String, String> errors = new LinkedHashMap<>();
        bindException.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError
                    ? ((FieldError) error).getField()
                    : error.getObjectName();
            errors.putIfAbsent(fieldName, error.getDefaultMessage());
        });
        
        log.warn("参数校验失败: {}", errors);
        return Result.error(ErrorCode.PARAM_ERROR, "参数校验失败", errors);
    }
    
    /**
     * 处理方法级参数校验异常（@RequestParam / @PathVariable 上的约束）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            errors.putIfAbsent(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        
        log.warn("参数约束校验失败: {}", errors);
        return Result.error(ErrorCode.PARAM_ERROR, "参数校验失败", errors);
    }
    
    /**
     * 处理缺少必填查询参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingParameterException(MissingServletRequestParameterException e) {
        String message = "缺少必填参数：" + e.getParameterName();
        log.warn("缺少必填参数: {} ({})", e.getParameterName(), e.getParameterType());
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }
    
    /**
     * 处理参数类型转换失败（如日期、数字格式不合法）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = "参数 " + e.getName() + " 格式不正确";
        log.warn("参数类型不匹配: {} -> {}", e.getName(), String.valueOf(e.getValue()));
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }
    
    /**
     * 处理请求体无法解析（JSON 语法错误、编码错误等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR, "请求体格式不正确");
    }
    
    /**
     * 处理不支持的 HTTP 方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不允许: {}", e.getMethod());
        return Result.error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法 " + e.getMethod() + " 不被支持");
    }
    
    /**
     * 处理不支持的请求内容类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<?> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("请求内容类型不支持: {}", e.getContentType());
        return Result.error(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, ErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getMessage());
    }
    
    /**
     * 处理不存在的接口路径（认证后的请求才会走到这里；未认证请求由 Security 返回 401）
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFoundException(Exception e) {
        log.warn("请求的资源不存在: {}", e.getMessage());
        return Result.error(ErrorCode.NOT_FOUND, "请求的资源不存在");
    }
    
    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        if (e instanceof BadCredentialsException) {
            return Result.error(ErrorCode.LOGIN_FAILED);
        }
        return Result.error(ErrorCode.UNAUTHORIZED);
    }
    
    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(ErrorCode.FORBIDDEN);
    }
    
    /**
     * 处理其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统异常，请联系管理员");
    }
}
