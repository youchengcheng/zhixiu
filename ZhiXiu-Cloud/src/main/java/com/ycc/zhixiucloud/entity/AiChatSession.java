package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话表 ai_chat_session。
 * <p>
 * 保存业主与 AI 客服的一次完整会话，用于串联多条 ai_chat_message，
 * 并记录会话最终是否通过 AI 自助解决或转为工单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_session")
public class AiChatSession extends BaseEntity {

    /** 业主 user_id */
    private Long userId;

    /** 会话标题（取首条消息摘要） */
    private String title;

    /** 会话内消息总条数（业主与 AI 消息均计入） */
    private Integer messageCount;

    /** 会话结果：0=进行中 1=自助解决 2=转工单 */
    private Integer resolved;
}
