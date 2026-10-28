package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色表 role：ID 固定 1=OWNER / 2=WORKER / 3=ADMIN。
 * <p>
 * 维护系统角色字典，user.role_id 关联本表，用于权限控制与角色展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class Role extends BaseEntity {

    /** 角色编码：OWNER=业主 / WORKER=维修师傅 / ADMIN=管理员 */
    private String roleCode;

    /** 角色显示名称 */
    private String roleName;

    /** 角色职责说明 */
    private String description;
}
