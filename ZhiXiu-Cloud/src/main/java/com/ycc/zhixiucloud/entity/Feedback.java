package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 问题反馈表 feedback。
 * <p>
 * 记录业主或维修师傅提交的 Bug、建议与投诉，以及管理员的处理状态与回复内容。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("feedback")
public class Feedback extends BaseEntity {

    /** 提交人 user_id */
    private Long userId;

    /** 提交人角色：OWNER=业主 / WORKER=维修师傅 */
    private String userRole;

    /** 反馈类型：BUG=缺陷 / SUGGESTION=建议 / COMPLAINT=投诉 */
    private String type;

    /** 反馈标题 */
    private String title;

    /** 反馈详细内容 */
    private String content;

    /** 联系方式（手机号或邮箱），便于管理员回访 */
    private String contact;

    /** 处理状态：0=待处理 1=处理中 2=已处理 3=关闭 */
    private Integer status;

    /** 管理员回复内容 */
    private String reply;

    /** 回复管理员 user_id */
    private Long replierId;

    /** 回复时间，未回复时为空 */
    private LocalDateTime replyTime;
}
