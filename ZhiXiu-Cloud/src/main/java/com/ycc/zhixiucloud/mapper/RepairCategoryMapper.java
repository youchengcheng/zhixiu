package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.RepairCategory;

/**
 * repair_category 报修类型表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责两级报修类型树的查询、维护与排序，
 * 为报修下单、师傅技能匹配和统计分类提供类型字典。
 */
public interface RepairCategoryMapper extends BaseMapper<RepairCategory> {
}
