package com.ycc.zhixiucloud.common.result;

import lombok.Getter;

/**
 * 业务状态码枚举。
 *
 * <p>取值与开发文档「接口文档 · 1.3 状态码」一一对应，禁止私自增删：
 * 2xx 表示成功，4xx/5xx 对应通用 HTTP 语义，1xxx 为用户与认证错误，
 * 2xxx 为工单错误，3xxx 为 AI 相关错误。前端依据 code 做统一提示。</p>
 */
@Getter
public enum ResultCode {

    /** 200-成功 */
    SUCCESS(200, "success"),
    /** 400-参数校验失败 */
    PARAM_ERROR(400, "参数校验失败"),
    /** 401-未登录或 Token 失效 */
    UNAUTHORIZED(401, "未登录或 Token 已失效"),
    /** 403-无权限访问 */
    FORBIDDEN(403, "无权限访问"),
    /** 404-资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 409-当前状态与操作冲突 */
    CONFLICT(409, "状态冲突"),
    /** 413-上传文件超过大小限制 */
    FILE_TOO_LARGE(413, "上传文件过大"),
    /** 500-未预期的服务器异常 */
    SYSTEM_ERROR(500, "服务器内部错误"),

    /** 1001-注册时用户名已被占用 */
    USERNAME_EXISTS(1001, "用户名已存在"),
    /** 1002-注册时手机号已注册 */
    PHONE_EXISTS(1002, "手机号已注册"),
    /** 1003-登录时用户名或密码错误 */
    USERNAME_OR_PASSWORD_ERROR(1003, "用户名或密码错误"),
    /** 1004-账号被管理员禁用 */
    ACCOUNT_DISABLED(1004, "账号被禁用"),
    /** 1005-师傅尚未通过入驻审核，不能接单 */
    WORKER_NOT_AUDITED(1005, "师傅未通过审核"),

    /** 2001-工单不存在或已被删除 */
    ORDER_NOT_FOUND(2001, "工单不存在"),
    /** 2002-当前状态不允许执行该流转动作 */
    ORDER_STATUS_INVALID(2002, "工单状态不允许该操作"),
    /** 2003-并发抢单失败，工单已被其他师傅抢走 */
    ORDER_GRABBED(2003, "工单已被他人抢走"),
    /** 2004-操作的房产不属于当前业主 */
    PROPERTY_NOT_OWNED(2004, "该房产不属于当前业主"),

    /** 3001-调用 AI 服务失败 */
    AI_INVOKE_FAILED(3001, "AI 服务调用失败"),
    /** 3002-对话已结束但没有可用的工单草稿 */
    AI_DRAFT_EMPTY(3002, "对话已结束，无有效草稿"),
    /** 3003-槽位信息不足，无法生成工单 */
    AI_SLOT_INSUFFICIENT(3003, "槽位信息不足，无法生成工单");

    /** 业务状态码，写入响应体 code */
    private final int code;

    /** 默认提示信息，可被调用方覆盖 */
    private final String message;

    ResultCode(int code, String message) {
        // 业务状态码
        this.code = code;
        // 默认提示信息
        this.message = message;
    }
}