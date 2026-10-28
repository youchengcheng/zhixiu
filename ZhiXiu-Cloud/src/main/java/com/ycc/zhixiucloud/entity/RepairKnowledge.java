package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 维修知识库表 repair_knowledge。
 * <p>
 * 存储维修知识问答条目，正文会被分块并向量化，供 AI 客服在问答与故障排查时检索引用；
 * 同时记录来源与向量化状态，便于维护和重建索引。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_knowledge")
public class RepairKnowledge extends BaseEntity {

    /** 知识标题 */
    private String title;

    /** 关联报修类型 ID（repair_category.id） */
    private Long categoryId;

    /** 正文（会被分块向量化） */
    private String content;

    /** 标签，逗号分隔 */
    private String tags;

    /** 来源类型：MANUAL=手工录入 / IMPORT=批量导入 */
    private String sourceType;

    /** 状态：0=禁用 1=启用 */
    private Integer status;

    /** 向量化状态：0=未向量化 1=已向量化 */
    private Integer vectorized;

    /** 内容版本号（修改后需重新向量化），非乐观锁字段 */
    private Integer version;
}
