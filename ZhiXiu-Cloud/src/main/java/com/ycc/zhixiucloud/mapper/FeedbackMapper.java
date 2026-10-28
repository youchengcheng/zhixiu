package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.Feedback;

/**
 * feedback 问题反馈表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责反馈记录的提交、按状态/类型筛选，
 * 以及管理员处理状态的更新。
 */
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
