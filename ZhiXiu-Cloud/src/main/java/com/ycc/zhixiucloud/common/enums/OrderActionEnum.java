package com.ycc.zhixiucloud.common.enums;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import lombok.Getter;

/**
 * 工单状态机事件。
 *
 * <p>logCode 是写入 work_order_log.action 的取值，严格取自开发文档 DDL 中 action 列举的集合
 * （CREATE / ASSIGN / ACCEPT / ARRIVE / PAUSE / RESUME / TRANSFER / SYSTEM_SUBMIT / COMPLETE / CANCEL），
 * 不新增取值；同一 logCode 对应多个事件时，由日志的 fromStatus、toStatus 与 remark 区分。</p>
 *
 * <p>映射表（事件 -&gt; 日志 action）：</p>
 * <pre>
 * CREATE          -&gt; CREATE         创建工单（PENDING）
 * ASSIGN          -&gt; ASSIGN         派单 / 改派 / 同意转单（目标均为 ASSIGNED）
 * ASSIGN_TIMEOUT  -&gt; ASSIGN         超时未接单，系统回退到 PENDING
 * GRAB            -&gt; ACCEPT         师傅抢单（PENDING -&gt; ACCEPTED）
 * ACCEPT          -&gt; ACCEPT         师傅接受派单（ASSIGNED -&gt; ACCEPTED）
 * ARRIVE          -&gt; ARRIVE         到场签到（ACCEPTED -&gt; GOING）
 * PAUSE           -&gt; PAUSE          暂停（GOING -&gt; PAUSED）
 * RESUME          -&gt; RESUME         恢复（PAUSED -&gt; GOING）
 * TRANSFER_APPLY  -&gt; TRANSFER       申请转单（GOING -&gt; TRANSFER_PENDING）
 * TRANSFER_REJECT -&gt; TRANSFER       驳回转单（TRANSFER_PENDING -&gt; GOING）
 * SUBMIT          -&gt; SYSTEM_SUBMIT  提交完工（GOING -&gt; SUBMITTED）
 * CONFIRM         -&gt; COMPLETE       业主确认完工（SUBMITTED -&gt; COMPLETED）
 * AUTO_CONFIRM    -&gt; COMPLETE       超时自动确认（SUBMITTED -&gt; COMPLETED）
 * EVALUATE        -&gt; COMPLETE       提交评价（COMPLETED -&gt; CLOSED）
 * CANCEL          -&gt; CANCEL         取消工单（PENDING/ASSIGNED/ACCEPTED/GOING -&gt; CANCELED）
 * </pre>
 */
@Getter
public enum OrderActionEnum {

    /** 创建工单：写入 CREATE，投递 CREATED 事件 */
    CREATE("CREATE", MqConstants.OrderEvent.CREATED, "创建工单"),

    /** 派单 / 改派 / 同意转单：写入 ASSIGN，投递 ASSIGNED 事件 */
    ASSIGN("ASSIGN", MqConstants.OrderEvent.ASSIGNED, "派单"),

    /** 超时未接单回退：日志仍记为 ASSIGN，投递 ASSIGNED 事件，由状态字段区分 */
    ASSIGN_TIMEOUT("ASSIGN", MqConstants.OrderEvent.ASSIGNED, "超时未接单回退"),

    /** 抢单：写入 ACCEPT，投递 ACCEPTED 事件（PENDING -> ACCEPTED） */
    GRAB("ACCEPT", MqConstants.OrderEvent.ACCEPTED, "抢单"),

    /** 接受派单：写入 ACCEPT，投递 ACCEPTED 事件（ASSIGNED -> ACCEPTED） */
    ACCEPT("ACCEPT", MqConstants.OrderEvent.ACCEPTED, "接受派单"),

    /** 到场签到：写入 ARRIVE，投递 ARRIVE 事件（ACCEPTED -> GOING） */
    ARRIVE("ARRIVE", MqConstants.OrderEvent.ARRIVE, "到场签到"),

    /** 暂停工单：写入 PAUSE，投递 PAUSE 事件（GOING -> PAUSED） */
    PAUSE("PAUSE", MqConstants.OrderEvent.PAUSE, "暂停工单"),

    /** 恢复工单：写入 RESUME，投递 RESUME 事件（PAUSED -> GOING） */
    RESUME("RESUME", MqConstants.OrderEvent.RESUME, "恢复工单"),

    /** 申请转单：写入 TRANSFER，投递 TRANSFER 事件（GOING -> TRANSFER_PENDING） */
    TRANSFER_APPLY("TRANSFER", MqConstants.OrderEvent.TRANSFER, "申请转单"),

    /** 驳回转单：写入 TRANSFER，投递 TRANSFER 事件（TRANSFER_PENDING -> GOING） */
    TRANSFER_REJECT("TRANSFER", MqConstants.OrderEvent.TRANSFER, "驳回转单"),

    /** 提交完工：写入 SYSTEM_SUBMIT，投递 SUBMITTED 事件（GOING -> SUBMITTED） */
    SUBMIT("SYSTEM_SUBMIT", MqConstants.OrderEvent.SUBMITTED, "提交完工"),

    /** 业主确认完工：写入 COMPLETE，投递 COMPLETED 事件（SUBMITTED -> COMPLETED） */
    CONFIRM("COMPLETE", MqConstants.OrderEvent.COMPLETED, "确认完工"),

    /** 超时自动确认：写入 COMPLETE，投递 COMPLETED 事件，与人工确认共用日志码 */
    AUTO_CONFIRM("COMPLETE", MqConstants.OrderEvent.COMPLETED, "超时自动确认"),

    /** 提交评价：写入 COMPLETE，投递 COMPLETED 事件（COMPLETED -> CLOSED） */
    EVALUATE("COMPLETE", MqConstants.OrderEvent.COMPLETED, "提交评价"),

    /** 取消工单：写入 CANCEL，投递 CANCELED 事件 */
    CANCEL("CANCEL", MqConstants.OrderEvent.CANCELED, "取消工单");

    /** 写入 work_order_log.action 的取值 */
    private final String logCode;

    /** 投递到 workorder-event-topic 的 Tag */
    private final String eventTag;

    /** 事件中文名，用于日志 */
    private final String name;

    OrderActionEnum(String logCode, String eventTag, String name) {
        // 写入 work_order_log.action 的取值
        this.logCode = logCode;
        // 投递到 MQ 的事件 Tag
        this.eventTag = eventTag;
        // 事件中文名
        this.name = name;
    }
}