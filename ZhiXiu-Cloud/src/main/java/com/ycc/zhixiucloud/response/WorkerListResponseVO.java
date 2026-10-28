package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 师傅列表行出参（GET /api/admin/worker/list，管理端），按查询条件分页返回。
 * workStatus 与 auditStatus 相互独立：审核通过后才可能进入在线 / 忙碌等可接单状态。
 */
@Data
@Builder
@Schema(description = "师傅列表行")
public class WorkerListResponseVO {

    @Schema(description = "师傅 ID")
    private Long id;

    @Schema(description = "关联用户 ID")
    private Long userId;

    @Schema(description = "工号")
    private String workerNo;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "技能标签")
    private String skillTags;

    @Schema(description = "师傅状态：0审核中 1在线 2离线 3忙碌 4禁用")
    private Integer workStatus;

    @Schema(description = "师傅状态文本")
    private String workStatusText;

    @Schema(description = "审核状态：0待审 1通过 2驳回")
    private Integer auditStatus;

    @Schema(description = "审核状态文本")
    private String auditStatusText;

    /** 累计接单数，包含尚未完工的工单 */
    @Schema(description = "累计接单数")
    private Integer totalOrders;

    /** 累计已完工工单数 */
    @Schema(description = "累计完工数")
    private Integer finishOrders;

    /** 历史评价均分；尚无评价时可能为 null */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;

    @Schema(description = "当前进行中工单数")
    private Integer currentLoad;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}