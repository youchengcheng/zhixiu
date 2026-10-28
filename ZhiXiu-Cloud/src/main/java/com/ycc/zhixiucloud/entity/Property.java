package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 房产表 property。
 * <p>
 * 保存业主名下的房产信息（小区、楼栋、单元、房号），供业主报修时选择报修地址，
 * 并支持设置默认房产与启用/禁用状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("property")
public class Property extends BaseEntity {

    /** 业主 user_id */
    private Long ownerId;

    /** 小区名称 */
    private String community;

    /** 楼栋号 */
    private String building;

    /** 单元号 */
    private String unit;

    /** 房号 */
    private String room;

    /** 完整地址：小区 + 栋 + 单元 + 房号 */
    private String fullAddress;

    /** 建筑面积(㎡) */
    private BigDecimal areaSize;

    /** 是否默认房产：0=否 1=是 */
    private Integer isDefault;

    /** 状态：0=禁用 1=正常 */
    private Integer status;
}
