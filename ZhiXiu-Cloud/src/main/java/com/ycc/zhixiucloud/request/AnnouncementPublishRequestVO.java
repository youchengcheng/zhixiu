package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 公告发布 / 下架入参（PUT /api/admin/announcement/{id}/publish），提交方为管理员。
 * 通过 status 在草稿与已发布之间切换；首次发布时由服务端补充发布时间。
 */
@Data
@Schema(description = "公告发布或下架入参")
public class AnnouncementPublishRequestVO {

    /** 仅允许 0 / 1；置为 0 即下架回到草稿态。 */
    @NotNull(message = "请指定发布状态")
    @Schema(description = "0草稿 1发布", example = "1")
    private Integer status;
}
