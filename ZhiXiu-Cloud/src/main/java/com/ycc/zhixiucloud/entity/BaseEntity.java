package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共基类：15 张业务表统一的主键与审计字段。
 * <p>
 * 主键 id 由 MyBatis-Plus 雪花算法自动生成；createTime、updateTime、deleted
 * 通过 {@link FieldFill} 交由 MetaObjectHandler 在插入/更新时自动填充，
 * 业务代码无需手动赋值，从而保证审计字段的一致性与完整性。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键，雪花 ID（插入时由 MyBatis-Plus 自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后更新时间（插入和更新时均自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=正常 1=已删除（插入时自动填充为 0） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
