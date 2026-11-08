package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 未读通知数量出参。
 *
 * @param count 未读数量，使用基本类型保证 JSON 输出为数字
 */
public record NotificationUnreadResponseVO(
        @Schema(description = "未读数量") long count) {
}
