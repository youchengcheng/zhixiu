package com.ycc.zhixiucloud.common.exception;

import com.ycc.zhixiucloud.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>所有可预期的业务失败（参数不合法、状态冲突、资源不存在等）都通过本异常抛出，
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@code Result}；这类异常属于业务流程
 * 的一部分，日志只记 warn，不打印完整堆栈。</p>
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码，最终写入统一响应体的 code 字段 */
    private final int code;

    /** 对应的状态码枚举，便于捕获异常时判断具体业务错误 */
    private final ResultCode resultCode;

    /**
     * 使用状态码枚举中的默认提示信息构造异常。
     *
     * @param resultCode 业务状态码
     */
    public BizException(ResultCode resultCode) {
        // 用枚举默认提示作为异常消息
        super(resultCode.getMessage());
        // 记录业务状态码
        this.code = resultCode.getCode();
        // 保留状态码枚举，便于调用方细分处理
        this.resultCode = resultCode;
    }

    /**
     * 使用自定义提示信息构造异常。
     *
     * @param resultCode 业务状态码
     * @param message    面向用户的提示信息，会覆盖枚举中的默认文案
     */
    public BizException(ResultCode resultCode, String message) {
        // 使用调用方传入的提示信息
        super(message);
        // 记录业务状态码
        this.code = resultCode.getCode();
        // 保留状态码枚举
        this.resultCode = resultCode;
    }

    /**
     * 静态工厂：创建使用默认提示信息的业务异常。
     *
     * @param resultCode 业务状态码
     * @return 业务异常实例
     */
    public static BizException of(ResultCode resultCode) {
        // 委托构造函数创建异常
        return new BizException(resultCode);
    }

    /**
     * 静态工厂：创建带自定义提示信息的业务异常。
     *
     * @param resultCode 业务状态码
     * @param message    面向用户的提示信息
     * @return 业务异常实例
     */
    public static BizException of(ResultCode resultCode, String message) {
        // 委托构造函数创建异常
        return new BizException(resultCode, message);
    }
}