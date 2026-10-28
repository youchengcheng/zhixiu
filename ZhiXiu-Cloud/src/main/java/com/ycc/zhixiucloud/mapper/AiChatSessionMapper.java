package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.AiChatSession;

/**
 * ai_chat_session AI 会话表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责 AI 会话的创建、按业主查询会话列表
 * 以及更新会话解决状态与消息计数。
 */
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
