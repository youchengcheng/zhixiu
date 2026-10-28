package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.Role;

/**
 * role 角色表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责角色字典的查询与维护，
 * 为登录鉴权和角色展示提供数据。
 */
public interface RoleMapper extends BaseMapper<Role> {
}
