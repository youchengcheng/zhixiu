package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.SlaConfig;

/**
 * sla_config SLA 配置表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责 SLA 规则的查询与维护，
 * 供工单创建时按类型、紧急程度、安全隐患等维度匹配超时配置。
 */
public interface SlaConfigMapper extends BaseMapper<SlaConfig> {
}
