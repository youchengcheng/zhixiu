package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报修类型表 repair_category（两级树，parent_id = 0 为一级）。
 * <p>
 * 维护报修类型字典，业主报修时选择二级类型；同时作为师傅技能匹配、
 * SLA 规则和工单类型分布统计的分类依据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_category")
public class RepairCategory extends BaseEntity {

    /** 类型名称 */
    private String name;

    /** 父类型 ID：0 表示一级类型，否则指向同表的一级类型 */
    private Long parentId;

    /** 同级排序值，数值越小越靠前 */
    private Integer sort;

    /** 图标名称或图标 URL */
    private String icon;

    /** 状态：0=禁用 1=正常 */
    private Integer status;
}
