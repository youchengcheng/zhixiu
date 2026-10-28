package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.User;

/**
 * user 用户表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查与分页能力，负责用户账号的查询、注册、资料更新，
 * 以及登录时间、账号状态等字段的维护。
 */
public interface UserMapper extends BaseMapper<User> {
}
