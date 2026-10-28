package com.ycc.zhixiucloud.common.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>固定结构为 { code, message, data }：code=200 表示业务成功，其余为业务失败码。
 * 业务失败也返回该结构（HTTP 200，由全局异常处理器转换），只有鉴权失败由安全层
 * 直接给出 401/403。前端只需判断 code 即可统一处理提示。</p>
 */
@Getter
@Schema(description = "统一响应体")
public class Result<T> implements Serializable {

    @Schema(description = "业务状态码，200 表示成功")
    private final int code;

    @Schema(description = "提示信息")
    private final String message;

    @Schema(description = "业务数据")
    private final T data;

    private Result(int code, String message, T data) {
        // 业务状态码
        this.code = code;
        // 提示信息
        this.message = message;
        // 业务数据
        this.data = data;
    }

    /**
     * 成功响应，无业务数据。
     *
     * @param <T> 数据类型
     * @return code=200、data=null 的响应体
     */
    public static <T> Result<T> ok() {
        // 成功且无业务数据
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应，携带业务数据。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=200 的响应体
     */
    public static <T> Result<T> ok(T data) {
        // 成功并携带业务数据
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应，携带业务数据和自定义提示信息。
     *
     * @param data    业务数据
     * @param message 自定义提示信息
     * @param <T>     数据类型
     * @return code=200 的响应体
     */
    public static <T> Result<T> ok(T data, String message) {
        // 成功并自定义提示信息
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应，使用状态码枚举中的默认提示。
     *
     * @param resultCode 业务状态码
     * @param <T>        数据类型
     * @return 失败响应体，data 为 null
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        // 失败响应，data 为空
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败响应，仅覆盖提示信息、沿用枚举中的状态码。
     *
     * @param resultCode 业务状态码
     * @param message    自定义提示信息
     * @param <T>        数据类型
     * @return 失败响应体，data 为 null
     */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        // 沿用状态码，仅覆盖提示信息
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 失败响应，直接指定状态码与提示信息。
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败响应体，data 为 null
     */
    public static <T> Result<T> fail(int code, String message) {
        // 直接指定状态码与提示信息
        return new Result<>(code, message, null);
    }

    /**
     * 判断是否为成功响应，仅供服务端断言使用，不输出到 JSON。
     *
     * @return code 等于成功码时返回 true
     */
    @JsonIgnore
    public boolean isSuccess() {
        // code 等于成功码即视为成功
        return code == ResultCode.SUCCESS.getCode();
    }
}