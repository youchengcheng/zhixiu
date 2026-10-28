package com.ycc.zhixiucloud.dto;

import lombok.Data;

/**
 * AI 工单草稿表单：写入 ai_chat_message.form_draft（JSON），用户确认后转 work_order。
 * 字段与《开发文档》SSE 事件 draft.form 一致。
 *
 * <p>用途：AI 助手多轮对话中逐步填充的槽位快照，本身不直接落工单表；
 * 用户确认时由 AiAssistantServiceImpl 合并前端补充信息后转为建单请求，
 * 再走正常报修流程（来源标记为 AI，并记录来源草稿消息 ID 便于溯源）。
 * 字段允许缺省，建单逻辑会对影响范围等字段补默认值。</p>
 */
@Data
public class AiFormDraft {

    /** 报修类型 ID（二级） */
    private Long categoryId;

    /** 报修类型路径，如「电器维修 &gt; 空调」 */
    private String categoryPath;

    /** 工单标题 */
    private String title;

    /** 故障现象描述 */
    private String description;

    /** 紧急程度：1低 2中 3高 4紧急 */
    private Integer urgency;

    /** 故障持续时间，如「约1天」 */
    private String faultDuration;

    /** 影响范围：1本户 2本层 3整栋 */
    private Integer affectScope;

    /** 安全隐患标志，逗号分隔：涉电/涉水/涉气/高空 */
    private String safetyFlag;

    /** 设备品牌型号 */
    private String deviceInfo;
}
