package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题反馈出参：业主 / 师傅“我的反馈”（GET /api/feedback/my）与管理端反馈列表（GET /api/admin/feedback/list）共用。
 * 列表按提交时间倒序；未回复时 reply、replierId、replierName、replyTime 均为 null。
 */
@Data
@Builder
@Schema(description = "问题反馈")
public class FeedbackResponseVO {

    @Schema(description = "反馈 ID")
    private Long id;

    @Schema(description = "提交人 ID")
    private Long userId;

    /** 展示用关联字段：取 user.real_name，非 feedback 表字段；关联用户不存在时为 null */
    @Schema(description = "提交人姓名")
    private String userName;

    @Schema(description = "提交人角色：OWNER / WORKER")
    private String userRole;

    @Schema(description = "类型：BUG / SUGGESTION / COMPLAINT")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "详细内容")
    private String content;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "处理状态：0待处理 1处理中 2已处理 3关闭")
    private Integer status;

    @Schema(description = "管理员回复")
    private String reply;

    @Schema(description = "回复人 ID")
    private Long replierId;

    /** 展示用关联字段：取 user.real_name，非 feedback 表字段；关联用户不存在时为 null */
    @Schema(description = "回复人姓名")
    private String replierName;

    @Schema(description = "回复时间")
    private LocalDateTime replyTime;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
