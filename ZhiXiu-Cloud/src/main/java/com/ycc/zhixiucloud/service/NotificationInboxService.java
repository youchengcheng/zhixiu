package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.NotificationResponseVO;
import com.ycc.zhixiucloud.response.NotificationUnreadResponseVO;

/**
 * 站内消息中心服务：MQ 消息落库、当前用户列表查询与已读状态维护。
 */
public interface NotificationInboxService {

    /** 将 SITE 通道消息幂等落库。 */
    void save(NotifyMessage message);

    /** 查询当前登录用户的通知列表。 */
    PageResult<NotificationResponseVO> page(PageRequestVO requestVO);

    /** 查询当前登录用户的未读数量。 */
    NotificationUnreadResponseVO unreadCount();

    /** 将当前登录用户的一条通知标记为已读。 */
    void read(Long id);

    /** 将当前登录用户的全部未读通知标记为已读。 */
    void readAll();
}
