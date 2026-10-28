package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 到场签到入参（POST /api/worker/order/{id}/arrive），提交方为维修师傅。
 * 定位为可选项，落库无对应列时写入流转日志备注；签到成功后记录到场时间并进入处理中，
 * 工单后续仍需师傅提交完工。
 */
@Data
@Schema(description = "到场签到入参")
public class ArriveOrderRequestVO {

    /** 十进制度，取值范围 -180 ~ 180；与 latitude 成对传入。 */
    @Schema(description = "经度")
    private BigDecimal longitude;

    /** 十进制度，取值范围 -90 ~ 90；与 longitude 成对传入。 */
    @Schema(description = "纬度")
    private BigDecimal latitude;
}