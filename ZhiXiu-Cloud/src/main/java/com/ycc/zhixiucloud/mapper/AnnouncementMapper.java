package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.Announcement;

/**
 * announcement 公告表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责公告的发布、编辑、删除，
 * 以及按目标角色与发布状态筛选、更新浏览量等操作。
 */
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
