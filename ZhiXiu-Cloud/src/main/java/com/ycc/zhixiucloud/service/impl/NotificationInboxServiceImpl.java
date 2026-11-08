package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.entity.Notification;
import com.ycc.zhixiucloud.mapper.NotificationMapper;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.NotificationResponseVO;
import com.ycc.zhixiucloud.response.NotificationUnreadResponseVO;
import com.ycc.zhixiucloud.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 站内消息中心服务实现。
 *
 * <p>所有用户侧查询和更新都把 receiver_id 强制限定为当前登录用户，防止通过通知 ID
 * 横向访问他人消息；MQ 重投由 notification.event_id 唯一索引最终兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationInboxServiceImpl implements NotificationInboxService {

    /** 未读 */
    private static final int UNREAD = 0;

    /** 已读 */
    private static final int READ = 1;

    private final NotificationMapper notificationMapper;

    @Override
    public void save(NotifyMessage message) {
        // MQ 消息字段直接映射为站内通知，阅读状态初始化为未读
        Notification notification = new Notification();
        notification.setEventId(message.eventId());
        notification.setReceiverId(message.receiverId());
        notification.setReceiverType(message.receiverType());
        notification.setTitle(message.title());
        notification.setContent(message.content());
        notification.setOrderId(message.orderId());
        notification.setIsRead(UNREAD);
        try {
            // 唯一索引 uk_event_id 保证并发和重复投递下只插入一次
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException e) {
            // 同一事件已经落库时直接视为消费成功，避免 MQ 无意义重试
            log.info("重复站内通知已忽略：eventId={}", message.eventId());
        }
    }

    @Override
    public PageResult<NotificationResponseVO> page(PageRequestVO requestVO) {
        // 接收人固定取当前登录用户，不接受前端传入用户 ID
        Long userId = SecurityUtils.currentUserId();
        // 按创建时间倒序分页查询本人的通知
        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getReceiverId, userId)
                        .orderByDesc(Notification::getCreateTime));
        // 实体转换为列表出参
        return PageResult.of(page, this::toVO);
    }

    @Override
    public NotificationUnreadResponseVO unreadCount() {
        // 只统计当前登录用户未读且未逻辑删除的通知
        long count = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, SecurityUtils.currentUserId())
                .eq(Notification::getIsRead, UNREAD));
        // 基本类型 long 保持 JSON 数字格式
        return new NotificationUnreadResponseVO(count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void read(Long id) {
        // 所有权条件直接进入 UPDATE，避免先查后改期间发生越权或并发窗口
        Long userId = SecurityUtils.currentUserId();
        int affected = notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getId, id)
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, UNREAD)
                .set(Notification::getIsRead, READ)
                .set(Notification::getReadTime, LocalDateTime.now())
                .set(Notification::getUpdateTime, LocalDateTime.now()));
        if (affected > 0) {
            return;
        }
        // 更新数为 0 可能是通知不存在，也可能是已经读取；已读操作应保持幂等
        long exists = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getReceiverId, userId));
        if (exists == 0) {
            throw BizException.of(ResultCode.NOT_FOUND, "通知不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAll() {
        // 批量更新当前登录用户的未读消息，其他用户数据不会被触碰
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getReceiverId, SecurityUtils.currentUserId())
                .eq(Notification::getIsRead, UNREAD)
                .set(Notification::getIsRead, READ)
                .set(Notification::getReadTime, LocalDateTime.now())
                .set(Notification::getUpdateTime, LocalDateTime.now()));
    }

    /** 通知实体转列表出参。 */
    private NotificationResponseVO toVO(Notification notification) {
        return NotificationResponseVO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .orderId(notification.getOrderId())
                .isRead(notification.getIsRead())
                .readTime(notification.getReadTime())
                .createTime(notification.getCreateTime())
                .build();
    }
}
