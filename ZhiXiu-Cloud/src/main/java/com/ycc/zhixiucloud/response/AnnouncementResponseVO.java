package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告出参：列表（GET /api/announcement/list）与详情（GET /api/announcement/{id}）共用，登录用户均可访问。
 * 业主 / 师傅只能看到已发布且投放范围命中本人角色的公告；管理员可见全部（含草稿）。
 */
@Data
@Builder
@Schema(description = "公告")
public class AnnouncementResponseVO {

    @Schema(description = "公告 ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容，支持 Markdown")
    private String content;

    @Schema(description = "类型：SYSTEM / MAINTENANCE / ACTIVITY")
    private String type;

    @Schema(description = "投放范围：ALL / OWNER / WORKER")
    private String targetRole;

    @Schema(description = "发布人 ID")
    private Long publisherId;

    /** 展示用关联字段：取 user.real_name，非 announcement 表字段；关联用户不存在时为 null */
    @Schema(description = "发布人姓名")
    private String publisherName;

    @Schema(description = "发布时间，草稿为空")
    private LocalDateTime publishTime;

    @Schema(description = "是否置顶：0否 1置顶")
    private Integer isTop;

    @Schema(description = "状态：0草稿 1已发布")
    private Integer status;

    @Schema(description = "阅读量")
    private Integer viewCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
