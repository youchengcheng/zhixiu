package com.ycc.zhixiucloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单状态缓存内容：workorder:status:{orderId} 的 JSON 值，TTL 3600s。
 *
 * <p>用途：状态机流转后刷新缓存，读接口与 SLA 超时判定优先走缓存快路径；
 * 缓存缺失或状态落后时回退数据库权威判断（见 SlaServiceImpl#isAdvanced）。
 * 由 OrderCacheService 使用 Redis 的 JSON 序列化器读写。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderStatusDTO implements Serializable {

    /** 工单 ID，与缓存 Key 中的 orderId 一致 */
    private Long orderId;

    /** 工单号，便于日志与前端展示 */
    private String orderNo;

    /** 状态编码，取值见 OrderStatusEnum.code */
    private String status;

    /** 状态中文名，前端直接展示 */
    private String statusText;

    /** 状态最后更新时间 */
    private LocalDateTime updateTime;
}