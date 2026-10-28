package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 房产信息出参：业主端“我的房产”列表（GET /api/property/list）与管理端查看业主房产共用。
 * fullAddress 为完整拼接地址，community / building / unit / room 为地址拆解字段。
 */
@Data
@Builder
@Schema(description = "房产信息")
public class PropertyResponseVO {

    @Schema(description = "房产 ID")
    private Long id;

    @Schema(description = "完整地址")
    private String fullAddress;

    /** 小区名称 */
    private String community;

    /** 楼栋号 */
    private String building;

    /** 单元号 */
    private String unit;

    /** 房间号 */
    private String room;

    @Schema(description = "面积(㎡)")
    private BigDecimal areaSize;

    @Schema(description = "0否 1默认")
    private Integer isDefault;

    @Schema(description = "0禁用 1正常")
    private Integer status;
}
