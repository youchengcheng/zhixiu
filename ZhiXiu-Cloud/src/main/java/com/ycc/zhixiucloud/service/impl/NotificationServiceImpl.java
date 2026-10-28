package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mq.producer.NotifyProducer;
import com.ycc.zhixiucloud.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现：解析接收人后按通道扇出。
 * 通知是旁路动作，不参与工单事务，失败只记日志。
 *
 * <p>发送策略与失败处理：</p>
 * <ul>
 *   <li>渠道选择：当前业务统一向站内信、短信、推送三个通道各投递一条消息，
 *       不按用户偏好或消息等级裁剪；三条消息进入 notification-topic 下各自
 *       的目的地，由三个独立消费组分别处理，互不阻塞；</li>
 *   <li>异步解耦：本类只解析接收人并调用 {@link NotifyProducer} 投递 RocketMQ，
 *       各通道由独立消费者处理（当前消费者为日志占位实现，尚未接入短信/推送服务商），
 *       因此外部通道的耗时和故障不会拖慢工单主流程；</li>
 *   <li>失败处理：本类不声明事务，也不向调用方抛出异常。接收人无法解析时
 *       （师傅不存在、管理员角色未初始化、无可用管理员）记 warn 日志后返回；
 *       单条消息发送异常由生产者捕获并记 error 日志，其余通道继续投递；消费者
 *       处理失败会返回 FAILURE 交由 RocketMQ 重试，重试耗尽后进入死信队列。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 用户状态：正常 */
    private static final int USER_STATUS_NORMAL = 1;

    private final NotifyProducer notifyProducer;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    /**
     * 通知指定业主：以 OWNER 作为接收人类型，向三个通道扇出。
     *
     * @param ownerId  业主用户 ID
     * @param title    通知标题
     * @param content  通知内容
     * @param orderId  关联工单 ID，可为空
     */
    @Override
    public void notifyOwner(Long ownerId, String title, String content, Long orderId) {
        // 接收人类型固定为 OWNER，走统一扇出
        fanOut(ownerId, OperatorTypeEnum.OWNER.getCode(), title, content, orderId);
    }

    /**
     * 通知指定师傅。
     *
     * <p>入参是师傅记录 ID（maintenance_worker.id），与通知接收人所需的用户 ID
     * （user_id）不同，需先查出档案再转换；师傅不存在或 ID 为空时只记告警并返回，
     * 不影响发起通知的业务流程。</p>
     *
     * @param workerId 师傅记录 ID
     * @param title    通知标题
     * @param content  通知内容
     * @param orderId  关联工单 ID，可为空
     */
    @Override
    public void notifyWorker(Long workerId, String title, String content, Long orderId) {
        // 师傅记录 ID 需先查出档案，才能拿到接收人用户 ID
        MaintenanceWorker worker = workerId == null ? null : maintenanceWorkerMapper.selectById(workerId);
        // 师傅不存在时无法解析接收人
        if (worker == null) {
            // 师傅不存在只告警，不影响发起通知的主流程
            log.warn("通知师傅失败，师傅不存在：workerId={}, orderId={}", workerId, orderId);
            return;
        }
        // 用师傅绑定的用户 ID 作为接收人
        fanOut(worker.getUserId(), OperatorTypeEnum.WORKER.getCode(), title, content, orderId);
    }

    /**
     * 通知全部可用的管理员账号。
     *
     * <p>按角色编码 ADMIN 解析角色，只选取账号状态正常的用户，逐个扇出；
     * 角色未初始化或没有可用管理员时记 warn 日志后返回。每个管理员的三通道
     * 投递彼此独立，某个接收人失败不会被循环中断。</p>
     *
     * @param title   通知标题
     * @param content 通知内容
     * @param orderId 关联工单 ID，可为空
     */
    @Override
    public void notifyAdmins(String title, String content, Long orderId) {
        // 按角色编码解析管理员角色，避免硬编码角色 ID
        Role adminRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, RoleEnum.ADMIN.getCode()));
        // 角色缺失说明未初始化
        if (adminRole == null) {
            // 角色未初始化，无法定位管理员
            log.warn("通知管理员失败，角色未初始化：orderId={}", orderId);
            return;
        }
        // 取该角色下状态正常的全部管理员
        List<User> admins = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getRoleId, adminRole.getId())
                .eq(User::getStatus, USER_STATUS_NORMAL));
        // 管理员列表为空时提前返回
        if (admins.isEmpty()) {
            // 无可用管理员账号，直接返回
            log.warn("通知管理员失败，暂无可用管理员账号：orderId={}", orderId);
            return;
        }
        // 遍历管理员列表逐个通知
        for (User admin : admins) {
            // 逐个管理员扇出三通道
            fanOut(admin.getId(), OperatorTypeEnum.ADMIN.getCode(), title, content, orderId);
        }
    }

    /**
     * 三通道扇出：站内信 / 短信 / 推送各投递一条消息。
     *
     * <p>三条消息分别带各自的 channel 和相同的业务内容进入 MQ，由三个消费组
     * 独立消费，因此某通道投递失败不影响另外两条。接收人 ID 为空时生产者会
     * 直接忽略，本方法无需重复判空。</p>
     *
     * @param receiverId   接收人用户 ID
     * @param receiverType 接收人类型：OWNER / WORKER / ADMIN
     * @param title        通知标题
     * @param content      通知内容
     * @param orderId      关联工单 ID，可为空
     */
    private void fanOut(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // 站内信通道
        notifyProducer.sendSite(receiverId, receiverType, title, content, orderId);
        // 短信通道
        notifyProducer.sendSms(receiverId, receiverType, title, content, orderId);
        // 推送通道
        notifyProducer.sendPush(receiverId, receiverType, title, content, orderId);
    }
}