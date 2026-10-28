package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告新增 / 修改入参（POST /api/admin/announcement、PUT /api/admin/announcement/{id}），
 * 提交方为管理员。新增后为草稿态，需再调用发布接口才对业主 / 师傅可见。
 */
@Data
@Schema(description = "公告新增或修改入参")
public class AnnouncementSaveRequestVO {

    @NotBlank(message = "请填写公告标题")
    @Size(max = 200, message = "标题不超过 200 个字")
    @Schema(description = "标题", example = "3 栋供水管道计划检修")
    private String title;

    @NotBlank(message = "请填写公告内容")
    @Schema(description = "内容，支持 Markdown")
    private String content;

    /** 不传默认 SYSTEM；仅支持描述中的三个枚举值。 */
    @Schema(description = "类型：SYSTEM系统通知 / MAINTENANCE维护公告 / ACTIVITY社区活动")
    private String type;

    /** 不传默认 ALL；决定公告在哪个角色端的列表中展示。 */
    @Schema(description = "投放范围：ALL全部 / OWNER业主 / WORKER维修师傅")
    private String targetRole;

    /** 不传默认 0（不置顶）；置顶公告在列表中优先展示。 */
    @Schema(description = "是否置顶：0否 1置顶")
    private Integer isTop;
}
