package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表 user。
 * <p>
 * 统一保存业主、维修师傅和管理员账号，通过 roleId 关联 role 表区分角色；
 * 维修师傅的扩展业务属性见 maintenance_worker 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 登录用户名（唯一） */
    private String username;

    /** 登录密码（BCrypt 密文） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号（登录/联系用） */
    private String phone;

    /** 邮箱，可为空 */
    private String email;

    /** 头像 URL，可为空 */
    private String avatar;

    /** 性别：0=未知 1=男 2=女 */
    private Integer gender;

    /** 角色 ID（role.id）：1=业主 2=维修师傅 3=管理员 */
    private Long roleId;

    /** 账号状态：0=禁用 1=正常 */
    private Integer status;

    /** 最后登录时间，从未登录时为空 */
    private LocalDateTime lastLoginTime;
}
