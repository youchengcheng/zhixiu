package com.ycc.zhixiucloud.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 普通消息与延迟消息分开配置 mandatory；拓扑由启动门卫按顺序声明。
 * 两种模板共用连接工厂及全局 correlated confirm 配置，发送结果统一由发布器等待和检查。
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // 不在连接建立时隐式声明拓扑；启动门卫负责 Exchange → Queue → Binding 的固定顺序。
        admin.setAutoStartup(false);
        // 参数冲突必须暴露为启动失败，保留现有队列及消息，不能忽略异常或删除重建。
        admin.setIgnoreDeclarationExceptions(false);
        return admin;
    }

    @Bean
    @Primary
    public RabbitTemplate ordinaryRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 普通消息不可路由时必须收到 Return；仅 Broker ACK 不能判定发送成功。
        template.setMandatory(true);
        return template;
    }

    @Bean
    public RabbitTemplate delayedRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 延迟插件不支持 mandatory；confirm 仅表示插件接受本次投递。
        template.setMandatory(false);
        return template;
    }
}
