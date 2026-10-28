package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 师傅详情出参（GET /api/admin/worker/{id}，管理端）。
 * 汇总账号资料、审核与工作状态、累计接单 / 完工数、评分和当前负载；idCard 已脱敏。
 */
@Data
@Builder
@Schema(description = "师傅详情")
public class WorkerDetailResponseVO {

    @Schema(description = "师傅 ID")
    private Long id;

    @Schema(description = "关联用户 ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "工号")
    private String workerNo;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "技能标签")
    private String skillTags;

    @Schema(description = "身份证号（脱敏）")
    private String idCard;

    /** 0审核中 / 1在线 / 2离线 / 3忙碌 / 4禁用 */
    @Schema(description = "师傅状态")
    private Integer workStatus;

    /** 由 workStatus 翻译得到的中文展示文本 */
    @Schema(description = "师傅状态文本")
    private String workStatusText;

    /** 0待审核 / 1已通过 / 2已驳回 */
    @Schema(description = "审核状态")
    private Integer auditStatus;

    /** 由 auditStatus 翻译得到的中文展示文本 */
    @Schema(description = "审核状态文本")
    private String auditStatusText;

    @Schema(description = "审核备注")
    private String auditRemark;

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