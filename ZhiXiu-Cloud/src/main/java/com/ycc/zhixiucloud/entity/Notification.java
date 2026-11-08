package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内通知表 notification。
 *
 * <p>保存由 notification-topic 的 SITE 通道消费后落库的站内消息；eventId
 * 由原始业务事件、接收人和通道共同派生，并由数据库唯一索引保证重复投递幂等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** MQ 事件幂等标识 */
    private String eventId;

    /** 接收人 user.id */
    private Long receiverId;

    /** 接收人角色：OWNER / WORKER / ADMIN */
    private String receiverType;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 关联工单 ID，无关联时为空 */
    private Long orderId;

    /** 阅读状态：0=未读 1=已读 */
    private Integer isRead;

    /** 阅读时间，未读时为空 */
    private LocalDateTime readTime;
}
