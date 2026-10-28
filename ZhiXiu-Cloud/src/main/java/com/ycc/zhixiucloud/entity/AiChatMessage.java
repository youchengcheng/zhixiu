package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 消息表 ai_chat_message。
 * <p>
 * 记录 AI 客服会话中的每一条消息（业主提问与 AI 回复），同时保存意图识别结果、
 * 知识库引用、工单草稿槽位收集进度以及 Token 消耗等过程数据，便于回溯与统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class AiChatMessage extends BaseEntity {

    /** 所属会话 ID（ai_chat_session.id） */
    private Long sessionId;

    /** 发送方：USER=业主 / AI=AI 助手 */
    private String sender;

    /** 消息内容（Markdown 格式） */
    private String content;

    /** 意图：QA=问答 / TROUBLESHOOT=故障排查 / FILL_FORM=收集信息 */
    private String intent;

    /** 引用的知识库记录 ID（repair_knowledge.id），逗号分隔 */
    private String citations;

    /** 工单草稿（JSON 字符串），仅收集信息阶段使用 */
    private String formDraft;

    /** 已填充槽位名，逗号分隔 */
    private String filledSlots;

    /** 缺失槽位名，逗号分隔 */
    private String missingSlots;

    /** 输入（提示词）Token 数，模型未返回时为空 */
    private Integer promptTokens;

    /** 输出（回复）Token 数，模型未返回时为空 */
    private Integer completionTokens;

    /** 本次 AI 调用耗时(ms) */
    private Integer costMs;
}
