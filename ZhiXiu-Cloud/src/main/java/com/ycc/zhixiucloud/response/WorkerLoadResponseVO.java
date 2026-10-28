package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 师傅负载行出参（GET /api/admin/worker/load，管理端派单参考）。
 * 仅返回审核通过的师傅，按 currentLoad 降序、ID 升序排列。
 */
@Data
@Builder
@Schema(description = "师傅负载")
public class WorkerLoadResponseVO {

    @Schema(description = "师傅 ID")
    private Long workerId;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "技能标签")
    private String skillTags;

    /** 0审核中 / 1在线 / 2离线 / 3忙碌 / 4禁用 */
    @Schema(description = "师傅状态")
    private Integer workStatus;

    /** 由 workStatus 翻译得到的中文展示文本 */
    @Schema(description = "师傅状态文本")
    private String workStatusText;

    /** 当前进行中的工单数，数值越大负载越高 */
    @Schema(description = "当前进行中工单数")
    private Integer currentLoad;

    /** 历史评价均分；尚无评价时可能为 null */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;
}