package com.ycc.zhixiucloud.common.exception;

import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>统一把 Controller / Service 抛出的异常转换为 {@link Result}，避免每个接口
 * 重复写 try-catch。约定：业务异常和参数校验失败属于可预期问题，记录 warn 并返回
 * HTTP 200 + 业务码；只有鉴权类异常给出真实的 HTTP 403；未预期异常记录 error
 * 和完整堆栈，并返回 500 业务码，不向前端暴露内部细节。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常：可预期，记 warn 即可。
     *
     * @param ex 业务异常
     * @return 带业务码和提示信息的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        // 业务异常属于可预期问题，只记 warn
        log.warn("业务异常 code={} message={}", ex.getCode(), ex.getMessage());
        // 返回带业务码与提示的失败响应
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理 @RequestBody 参数校验失败。
     *
     * @param ex 参数校验异常
     * @return 汇总所有字段错误后的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        // 多个字段同时校验失败时，用“；”拼接成一条提示
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        // 记录参数校验失败日志
        log.warn("参数校验失败：{}", message);
        // 校验信息为空时回退枚举默认提示
        return Result.fail(ResultCode.PARAM_ERROR, message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    /**
     * 处理表单参数绑定失败。
     *
     * @param ex 绑定异常
     * @return 汇总所有字段错误后的失败响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        // 汇总所有字段错误，用「；」拼接成一条提示
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        // 记录参数绑定失败日志
        log.warn("参数绑定失败：{}", message);
        // 校验信息为空时回退枚举默认提示
        return Result.fail(ResultCode.PARAM_ERROR, message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    /**
     * 处理方法参数约束校验失败（@RequestParam / @PathVariable 上的校验注解）。
     *
     * @param ex 约束校验异常
     * @return 汇总所有约束错误后的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        // 汇总所有约束错误，用「；」拼接成一条提示
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        // 记录参数约束失败日志
        log.warn("参数约束失败：{}", message);
        // 校验信息为空时回退枚举默认提示
        return Result.fail(ResultCode.PARAM_ERROR, message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    /**
     * 处理缺少必填参数、参数类型不匹配、请求体格式错误等通用参数问题。
     *
     * @param ex 请求参数相关异常
     * @return 统一参数错误响应
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> handleRequestParamException(Exception ex) {
        // 记录请求参数异常日志
        log.warn("请求参数异常：{}", ex.getMessage());
        // 统一返回参数错误
        return Result.fail(ResultCode.PARAM_ERROR);
    }

    /**
     * 处理不支持的 HTTP 请求方法。
     *
     * @param ex 请求方法异常
     * @return 带具体请求方法的失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        // 记录请求方法不支持日志
        log.warn("请求方法不支持：{}", ex.getMessage());
        // 提示信息中带上实际请求方法
        return Result.fail(ResultCode.PARAM_ERROR, "请求方法不支持：" + ex.getMethod());
    }

    /**
     * 处理上传文件超过大小限制。
     *
     * @param ex 上传超限异常
     * @return 文件过大响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // 记录上传超限日志
        log.warn("上传文件过大：{}", ex.getMessage());
        // 返回文件过大响应
        return Result.fail(ResultCode.FILE_TOO_LARGE);
    }

    /**
     * 处理静态资源或请求路径不存在。
     *
     * @param ex      资源不存在异常
     * @param request 当前请求，用于记录请求方法和 URI
     * @return 资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        // 记录请求方法与 URI，便于定位丢失的资源
        log.warn("资源不存在：{} {}", request.getMethod(), request.getRequestURI());
        // 返回资源不存在响应
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 处理方法级鉴权失败，返回真实 HTTP 403，前端据此提示无权限。
     *
     * @param ex 权限不足异常
     * @return 无权限响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        // 记录无权限访问日志
        log.warn("无权限访问：{}", ex.getMessage());
        // 返回无权限响应（HTTP 403）
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 兜底处理所有未预期异常。
     *
     * @param ex      未预期异常
     * @param request 当前请求，用于定位出错的接口
     * @return 统一的服务器内部错误响应，不暴露异常细节
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        // 这里必须带异常对象输出堆栈，便于排查线上问题
        log.error("系统异常：{} {}", request.getMethod(), request.getRequestURI(), ex);
        // 返回统一的服务器错误响应，不向前端暴露异常细节
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}