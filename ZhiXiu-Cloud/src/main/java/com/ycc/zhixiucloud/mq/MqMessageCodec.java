package com.ycc.zhixiucloud.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * MQ 消息体编解码。
 * 统一使用容器内的 ObjectMapper（与接口 JSON 同一套规则），发送端编码为 UTF-8 JSON 字节，
 * 消费端按目标类型反序列化，不依赖 SDK 内部的消息转换器，避免类型推断差异。
 *
 * <p>序列化格式：UTF-8 编码的 JSON 文本，不带类名、版本号等类型信息，
 * 因此消息可被其他语言或后续版本读取，但也要求收发两端显式约定目标 DTO。</p>
 *
 * <p>兼容性约定：受全局 Jackson 配置影响，Long 会被序列化为字符串
 * （消费端取数值字段时需同时兼容 Number 与 String，见 OrderEventConsumer#longValue），
 * LocalDateTime 使用「yyyy-MM-dd HH:mm:ss」格式。消息只依赖字段名，
 * 新增字段时旧消费者会忽略未知字段（Spring Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES），
 * 但删除/改名字段会破坏旧消息，因此 DTO 字段应保持向后兼容。</p>
 */
@Component
@RequiredArgsConstructor
public class MqMessageCodec {

    private final ObjectMapper objectMapper;

    /**
     * 对象 -> UTF-8 JSON 字节。
     * 序列化失败说明消息体不可编码，属于代码/配置错误，直接抛业务异常由生产端处理
     * （通知类生产端会捕获并降级，工单事件生产端会触发事务回滚）。
     */
    public byte[] encode(Object payload) {
        try {
            // 直接用容器 ObjectMapper 写出字节数组
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            // 编码失败属系统错误，抛业务异常交由生产端处理
            throw BizException.of(ResultCode.SYSTEM_ERROR, "MQ 消息序列化失败");
        }
    }

    /**
     * UTF-8 JSON 字节 -> 对象。
     * 反序列化失败通常意味着消息格式与当前 DTO 不兼容或消息体已损坏，
     * 抛异常后由消费者返回 FAILURE，交给 RocketMQ 重试并最终进入死信。
     */
    public <T> T decode(byte[] body, Class<T> type) {
        try {
            // 按目标 DTO 类型反序列化 JSON
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            // 解码失败抛业务异常，由消费端返回 FAILURE 触发重试
            throw BizException.of(ResultCode.SYSTEM_ERROR, "MQ 消息反序列化失败：" + type.getSimpleName());
        }
    }

    /** 消费端消息体 -> 对象：先把 ByteBuffer 剩余内容复制为字节数组，再走统一反序列化 */
    public <T> T decode(ByteBuffer body, Class<T> type) {
        // 按剩余可读长度分配数组
        byte[] bytes = new byte[body.remaining()];
        // 把缓冲区剩余内容读入数组
        body.get(bytes);
        // 复用字节数组解码逻辑
        return decode(bytes, type);
    }

    /**
     * 消费端消息体 -> 原始文本，消费失败时便于打印排查。
     * 使用 duplicate() 复制游标，读取文本不会消耗原 ByteBuffer，不影响后续可能的解码/重试。
     */
    public String toText(ByteBuffer body) {
        // 用副本游标解码，避免消耗原 ByteBuffer
        return StandardCharsets.UTF_8.decode(body.duplicate()).toString();
    }
}