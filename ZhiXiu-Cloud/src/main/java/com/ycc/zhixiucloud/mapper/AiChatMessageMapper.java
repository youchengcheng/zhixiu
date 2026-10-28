package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.AiChatMessage;

/**
 * ai_chat_message AI 消息表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查与分页能力，主要用于按会话 ID 查询历史消息、
 * 保存业主提问与 AI 回复，以及统计 Token 消耗。
 */
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
