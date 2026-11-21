package com.ycc.zhixiucloud.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按 Exchange → Queue → Binding → 实际死信 policy → 生产就绪 → 监听器的顺序启动。
 * 插件缺失会在声明 x-delayed-message 时失败；声明冲突或策略缺失同样终止启动并保留存量消息。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RabbitStartupGate implements ApplicationRunner {

    private static final String DLX = "zhixiu.dead-letter";
    private static final List<String> BUSINESS_QUEUES = List.of(
            MqConstants.GROUP_ORDER_EVENT, MqConstants.GROUP_SLA_TIMEOUT,
            MqConstants.GROUP_NOTIFY_SITE, MqConstants.GROUP_NOTIFY_SMS,
            MqConstants.GROUP_NOTIFY_PUSH, MqConstants.GROUP_OPERATION_LOG, MqConstants.GROUP_AI_TASK);
    private final RabbitAdmin admin;
    private final RabbitListenerEndpointRegistry listeners;
    private final RabbitMessagePublisher publisher;
    private final RabbitProperties properties;
    private final ObjectMapper objectMapper;
    private final String managementUrl;

    public RabbitStartupGate(RabbitAdmin admin, RabbitListenerEndpointRegistry listeners,
                             RabbitMessagePublisher publisher, RabbitProperties properties,
                             ObjectMapper objectMapper,
                             @Value("${repair.rabbitmq.management-url:http://localhost:15672}") String managementUrl) {
        this.admin = admin;
        this.listeners = listeners;
        this.publisher = publisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.managementUrl = managementUrl;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 禁止自动删除重建：现有 durable/类型/arguments 冲突由 Broker 拒绝声明。
        for (Exchange exchange : exchanges()) {
            admin.declareExchange(exchange);
        }
        for (String name : BUSINESS_QUEUES) {
            Map<String, Object> arguments = name.equals(MqConstants.GROUP_ORDER_EVENT)
                    ? Map.of("x-queue-type", "quorum", "x-delivery-limit", -1, "x-single-active-consumer", true)
                    : Map.of("x-queue-type", "quorum", "x-delivery-limit", -1);
            admin.declareQueue(new Queue(name, true, false, false, arguments));
            admin.declareQueue(new Queue(name + ".dlq", true, false, false, Map.of("x-queue-type", "quorum")));
        }
        for (Binding binding : bindings()) {
            admin.declareBinding(binding);
        }
        // 新建 quorum 队列的管理 API 元数据可能短暂延迟；最多核验 4 次，间隔 1/2/3 秒后仍不符则失败。
        for (int attempt = 1; ; attempt++) {
            try {
                verifyPolicies();
                break;
            } catch (IllegalStateException e) {
                if (attempt == 4) {
                    throw e;
                }
                Thread.sleep(attempt * 1000L);
            }
        }
        publisher.markReady();
        listeners.start();
        log.info("RabbitMQ 启动门卫通过：6 Exchange、14 Queue、25 Binding；7 个有效死信 policy；监听器已启动");
    }

    private List<Exchange> exchanges() {
        return List.of(new DirectExchange(MqConstants.TOPIC_WORK_ORDER_EVENT, true, false),
                new CustomExchange(MqConstants.TOPIC_SLA_TIMEOUT, "x-delayed-message", true, false,
                        Map.of("x-delayed-type", "direct")),
                new DirectExchange(MqConstants.TOPIC_NOTIFICATION, true, false),
                new DirectExchange(MqConstants.TOPIC_OPERATION_LOG, true, false),
                new DirectExchange(MqConstants.TOPIC_AI_TASK, true, false),
                new DirectExchange(DLX, true, false));
    }

    private List<Binding> bindings() {
        List<Binding> bindings = new ArrayList<>();
        for (String key : List.of("CREATED", "ASSIGNED", "ACCEPTED", "ARRIVE", "PAUSE", "RESUME",
                "TRANSFER", "SUBMITTED", "COMPLETED", "CANCELED")) {
            bindings.add(binding(MqConstants.GROUP_ORDER_EVENT, MqConstants.TOPIC_WORK_ORDER_EVENT, key));
        }
        for (String key : List.of(MqConstants.SlaTimeout.RESPONSE, MqConstants.SlaTimeout.HANDLE)) {
            bindings.add(binding(MqConstants.GROUP_SLA_TIMEOUT, MqConstants.TOPIC_SLA_TIMEOUT, key));
        }
        bindings.add(binding(MqConstants.GROUP_NOTIFY_SITE, MqConstants.TOPIC_NOTIFICATION, MqConstants.Notify.SITE));
        bindings.add(binding(MqConstants.GROUP_NOTIFY_SMS, MqConstants.TOPIC_NOTIFICATION, MqConstants.Notify.SMS));
        bindings.add(binding(MqConstants.GROUP_NOTIFY_PUSH, MqConstants.TOPIC_NOTIFICATION, MqConstants.Notify.PUSH));
        bindings.add(binding(MqConstants.GROUP_OPERATION_LOG, MqConstants.TOPIC_OPERATION_LOG, MqConstants.OperationLog.ACCESS));
        for (String key : List.of(MqConstants.AiTask.VECTORIZE, MqConstants.AiTask.SUMMARIZE)) {
            bindings.add(binding(MqConstants.GROUP_AI_TASK, MqConstants.TOPIC_AI_TASK, key));
        }
        for (String name : BUSINESS_QUEUES) {
            bindings.add(binding(name + ".dlq", DLX, name));
        }
        return bindings;
    }

    private Binding binding(String queue, String exchange, String key) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, key, Map.of());
    }

    private void verifyPolicies() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String vhost = path(properties.getVirtualHost());
        JsonNode policies = get(client, "/api/policies/" + vhost);
        for (String name : BUSINESS_QUEUES) {
            JsonNode queue = get(client, "/api/queues/" + vhost + "/" + path(name));
            // 核验实际生效定义，不能只检查同名 policy 存在，避免被更高优先级策略覆盖。
            JsonNode effective = queue.path("effective_policy_definition");
            require(DLX.equals(effective.path("dead-letter-exchange").asText()), name, "dead-letter-exchange");
            require(name.equals(effective.path("dead-letter-routing-key").asText()), name, "dead-letter-routing-key");
            require("at-least-once".equals(effective.path("dead-letter-strategy").asText()), name, "dead-letter-strategy");
            require("reject-publish".equals(effective.path("overflow").asText()), name, "overflow");
            require(!effective.has("message-ttl") && !effective.has("expires"), name, "禁止 TTL/自动过期");
            JsonNode selected = null;
            for (JsonNode policy : policies) {
                if (queue.path("policy").asText().equals(policy.path("name").asText())) {
                    selected = policy;
                    break;
                }
            }
            require(selected != null, name, "有效 policy 不存在");
            // 精确锚定业务队列，不允许 DLQ 被同一个 policy 回灌。
            String pattern = selected.path("pattern").asText();
            require(pattern.startsWith("^") && pattern.endsWith("$")
                    && Pattern.matches(pattern, name) && !Pattern.matches(pattern, name + ".dlq"), name, "policy 精确匹配");
            require(pattern.equals("^" + name + "$") || pattern.equals("^" + name.replace("-", "\\-") + "$")
                    || pattern.equals("^\\Q" + name + "\\E$"), name, "policy 必须只匹配本队列");
            JsonNode deadQueue = get(client, "/api/queues/" + vhost + "/" + path(name + ".dlq"));
            // DLQ 独立核验，不能继承业务队列的死信/TTL 策略而自动回灌或删除等待人工处理的消息。
            JsonNode deadPolicy = deadQueue.path("effective_policy_definition");
            require(!deadPolicy.has("dead-letter-exchange") && !deadPolicy.has("message-ttl")
                    && !deadPolicy.has("expires"), name + ".dlq", "DLQ 禁止自动回灌/过期");
        }
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        // 复用 AMQP 凭证，仅经 Authorization header 发送；异常不记录凭证或管理 API 响应体。
        String authorization = Base64.getEncoder().encodeToString(
                (properties.getUsername() + ":" + properties.getPassword()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(managementUrl.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Basic " + authorization).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("RabbitMQ 管理 API 核验失败：HTTP " + response.statusCode() + " " + path);
        }
        return objectMapper.readTree(response.body());
    }

    private String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void require(boolean valid, String queue, String field) {
        if (!valid) {
            throw new IllegalStateException("RabbitMQ policy 不符合要求：" + queue + " / " + field);
        }
    }
}
