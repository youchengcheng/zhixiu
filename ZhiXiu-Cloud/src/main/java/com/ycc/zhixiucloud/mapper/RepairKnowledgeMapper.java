package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.RepairKnowledge;

/**
 * repair_knowledge 维修知识库表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责知识条目的维护、按报修类型/标签筛选，
 * 以及向量化状态与版本号的更新。
 */
public interface RepairKnowledgeMapper extends BaseMapper<RepairKnowledge> {
}
