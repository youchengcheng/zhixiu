package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.Property;

/**
 * property 房产表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责业主房产信息的维护、按业主查询房产列表
 * 以及默认房产的设置。
 */
public interface PropertyMapper extends BaseMapper<Property> {
}
