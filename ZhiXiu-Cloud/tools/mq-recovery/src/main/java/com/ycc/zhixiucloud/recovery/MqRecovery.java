package com.ycc.zhixiucloud.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.SessionCredentialsProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.producer.Producer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 一次性 SLA 恢复工具，供停写切换及回滚时人工执行。
 * <p>数据库仅执行 SELECT，任务只使用原 deadline，不调用业务服务、不修改期限或超时标记。
 * 默认 preview 生成固定批次，只有显式 publish 才发送；停写、普通消息排空及新旧消费者互斥由操作人员控制。
 * RabbitMQ 延迟分段由新消费者继续处理；RocketMQ 使用原生绝对投递时间，并依据运行中 Broker 配置拒绝超限回滚。
 * 两种客户端只属于独立工具，不能把它们引入主应用。
 */
public final class MqRecovery {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TOPIC = "sla-timeout-topic";
    private static final long SEGMENT_MS = 86_400_000L;
    private static final Set<String> RESPONSE_STATES = Set.of("PENDING", "ASSIGNED");
    private static final Set<String> HANDLE_STATES = Set.of("PENDING", "ASSIGNED", "ACCEPTED", "GOING", "PAUSED", "TRANSFER_PENDING");
    private static final Set<String> ALL_STATES = Set.of("PENDING", "ASSIGNED", "ACCEPTED", "GOING", "PAUSED", "TRANSFER_PENDING", "SUBMITTED", "COMPLETED", "CLOSED", "CANCELED");
    private static final String COLUMNS = "id,status,deleted,sla_response_deadline,sla_handle_deadline,sla_response_breached,sla_handle_breached,accepted_time,submitted_time";

    /** 同时保留秒精度数据库文本和上海时区 epoch；字符串 Long 与现有 SLA DTO 的 JSON 格式一致。 */
    public record Task(String orderId, String type, String deadline, String deadlineEpochMillis) {
        String key() { return orderId + ":" + type; }
    }
    public record Anomaly(String orderId, String type, String reason) { }
    public record BrokerConfig(String capturedAtEpochMillis, String brokerAddress, long timerMaxDelaySec, boolean timerWheelEnable) { }
    /** 固定输入批次，不包含凭证；重跑必须保留原始文件字节，否则无法使用原结果摘要。 */
    public record Manifest(int version, String batchId, String target, String zone, String batchTimestamp,
                           BrokerConfig brokerConfig, List<Task> tasks, List<Anomaly> anomalies) { }
    /** 一个任务可以追加多个结果，恢复时使用最后一条；CONFIRMED 只在确认且结果落盘后成立。 */
    public record Result(String manifestSha256, String batchId, String key, String target, String status,
                         String timestamp, String reason, String brokerMessageId) { }
    private record Order(long id, String status, int deleted, LocalDateTime response, LocalDateTime handle,
                         int responseBreached, int handleBreached, LocalDateTime accepted, LocalDateTime submitted) { }
    private record Outcome(String status, String reason, String brokerMessageId) { }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        try {
            Map<String, String> options = options(args);
            if (options.containsKey("help")) {
                System.out.println("java -jar mq-recovery-1.0.0.jar [preview|publish] --manifest <file> [--target rabbitmq|rocketmq]");
                return;
            }
            Path manifestPath = Path.of(required(options, "manifest")).toAbsolutePath().normalize();
            if ("publish".equals(options.get("mode"))) publish(manifestPath, options.get("target"));
            else preview(manifestPath, options.getOrDefault("target", "rabbitmq"));
        } catch (Exception e) {
            // 不打印第三方异常文本/堆栈，连接串或服务端信息可能含凭证。
            String reason = e instanceof SafeFailure ? e.getMessage() : "操作失败（" + e.getClass().getSimpleName() + "），凭证及第三方异常已隐藏";
            System.err.println(reason);
            System.exit(1);
        }
    }

    /** 只读扫描当前有效记录并固化批次；异常记录单列，不把 preview 成功等同于异常已处理。 */
    private static void preview(Path path, String target) throws Exception {
        checkTarget(target);
        if (Files.exists(path)) throw new SafeFailure("Manifest 已存在；使用新文件名保存固定批次");
        List<Task> tasks = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        long batchTimestamp = System.currentTimeMillis();
        try (Connection connection = database()) {
            long afterId = 0;
            while (true) {
                int count = 0;
                // 按主键向后扫描，每页 500 条；正式切换在停写条件下执行，避免扫描集合持续变化。
                try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM work_order WHERE deleted=0 AND id>? ORDER BY id LIMIT 500")) {
                    statement.setLong(1, afterId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            Order order = order(rows);
                            afterId = order.id();
                            count++;
                            inspect(order, tasks, anomalies);
                        }
                    }
                }
                if (count < 500) break;
            }
        }
        BrokerConfig config = "rocketmq".equals(target) ? effectiveBrokerConfig() : null;
        Manifest manifest = new Manifest(1, UUID.randomUUID().toString(), target, ZONE.getId(), Long.toString(batchTimestamp), config, tasks, anomalies);
        writeNew(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        System.out.printf("PREVIEW：target=%s，tasks=%d，anomalies=%d，manifest=%s%n", target, tasks.size(), anomalies.size(), path);
        if (!anomalies.isEmpty()) System.out.println("状态/里程碑异常已单列，请在停写切换验收中处理；工具不会修改原记录");
        if (config != null) checkRocketLimit(manifest.tasks(), config);
    }

    /** 状态、里程碑与 breached 一起判断有效性；不以 sla_escalated 过滤，避免遗漏尚需恢复的另一类 SLA。 */
    private static void inspect(Order order, List<Task> tasks, List<Anomaly> anomalies) {
        if (!ALL_STATES.contains(order.status())) {
            anomalies.add(new Anomaly(Long.toString(order.id()), "ALL", "UNKNOWN_STATUS"));
            return;
        }
        boolean early = RESPONSE_STATES.contains(order.status());
        boolean active = HANDLE_STATES.contains(order.status());
        boolean responseConflict = early && order.accepted() != null;
        boolean handleConflict = active && order.submitted() != null;
        if (responseConflict) anomalies.add(new Anomaly(Long.toString(order.id()), "RESPONSE", "STATUS_CONFLICT_ACCEPTED_TIME"));
        if (handleConflict) anomalies.add(new Anomaly(Long.toString(order.id()), "HANDLE", "STATUS_CONFLICT_SUBMITTED_TIME"));
        if (!early && !"CANCELED".equals(order.status()) && order.accepted() == null)
            anomalies.add(new Anomaly(Long.toString(order.id()), "RESPONSE", "PROGRESSED_WITHOUT_ACCEPTED_TIME"));
        if (Set.of("SUBMITTED", "COMPLETED", "CLOSED").contains(order.status()) && order.submitted() == null)
            anomalies.add(new Anomaly(Long.toString(order.id()), "HANDLE", "PROGRESSED_WITHOUT_SUBMITTED_TIME"));
        if (early && order.responseBreached() == 0 && !responseConflict) {
            if (order.response() == null) anomalies.add(new Anomaly(Long.toString(order.id()), "RESPONSE", "MISSING_DEADLINE"));
            else tasks.add(task(order.id(), "RESPONSE", order.response()));
        }
        if (active && order.handleBreached() == 0 && !handleConflict) {
            if (order.handle() == null) anomalies.add(new Anomaly(Long.toString(order.id()), "HANDLE", "MISSING_DEADLINE"));
            else tasks.add(task(order.id(), "HANDLE", order.handle()));
        }
    }

    /** 将数据库已保存的秒精度期限转换成同一时区的 epoch，不重新计算 SLA 时限。 */
    private static Task task(long orderId, String type, LocalDateTime deadline) {
        return new Task(Long.toString(orderId), type, TIME.format(deadline), Long.toString(epoch(deadline)));
    }

    /** 读取固定批次、复核当前记录并逐条确认；无法确定发送结果时保留 UNKNOWN，允许人工核对后重跑。 */
    private static void publish(Path path, String explicitTarget) throws Exception {
        byte[] manifestBytes = Files.readAllBytes(path);
        Manifest manifest = JSON.readValue(manifestBytes, Manifest.class);
        validate(manifest);
        if (explicitTarget != null && !explicitTarget.equals(manifest.target())) throw new SafeFailure("目标与 Manifest 不一致");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(manifestBytes));
        Path resultPath = path.resolveSibling(path.getFileName() + ".results.jsonl");
        // 排他锁覆盖检查和发布，避免同一批次被两个进程同时重放。
        try (FileChannel journal = FileChannel.open(resultPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var ignored = journal.tryLock()) {
            if (ignored == null) throw new SafeFailure("该批次已有发布进程运行");
            Map<String, Result> results = readResults(journal, hash, manifest);
            List<Task> candidates = new ArrayList<>();
            try (Connection connection = database()) {
                for (Task task : manifest.tasks()) {
                    Result previous = results.get(task.key());
                    // 已确认项直接跳过；SKIPPED 仍重查，FAILED/UNKNOWN 重投可能重复，依靠消费者的数据库门卫处理。
                    if (previous != null && "CONFIRMED".equals(previous.status())) continue;
                    if (eligible(connection, task)) candidates.add(task);
                    else append(journal, result(manifest, hash, task, new Outcome("SKIPPED", "CURRENT_STATE_OR_DEADLINE_CHANGED", null)));
                }
                BrokerConfig effective = null;
                if ("rocketmq".equals(manifest.target())) {
                    effective = effectiveBrokerConfig();
                    // 整批预检先于发送；超限时一条也不投递。
                    checkRocketLimit(candidates, effective);
                    Path evidence = path.resolveSibling(path.getFileName() + ".rocket-effective." + System.currentTimeMillis() + ".json");
                    writeNew(evidence, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(effective));
                }
                if (!candidates.isEmpty()) {
                    try (Publisher publisher = "rabbitmq".equals(manifest.target()) ? new RabbitPublisher() : new RocketPublisher()) {
                        for (Task task : candidates) {
                            Outcome outcome;
                            // 整批筛选到实际发送之间仍可能发生变化，所以每条发出前再次核对原 deadline 和里程碑。
                            if (!eligible(connection, task)) outcome = new Outcome("SKIPPED", "CURRENT_STATE_OR_DEADLINE_CHANGED", null);
                            else {
                                if (effective != null) checkRocketLimit(List.of(task), effective);
                                outcome = publisher.send(task, manifest.batchTimestamp(), messageId(manifest, task));
                            }
                            try {
                                append(journal, result(manifest, hash, task, outcome));
                            } catch (IOException e) {
                                System.err.printf("UNKNOWN：key=%s，结果落盘失败；停止本批次，重跑可能重复投递%n", task.key());
                                throw new SafeFailure("结果记录失败，必须按 UNKNOWN 核对 Broker 与消费状态");
                            }
                            System.out.printf("%s：key=%s，reason=%s%n", outcome.status(), task.key(), outcome.reason());
                        }
                    }
                }
            }
            Map<String, Result> latest = readResults(journal, hash, manifest);
            long confirmed = latest.values().stream().filter(r -> "CONFIRMED".equals(r.status())).count();
            long skipped = latest.values().stream().filter(r -> "SKIPPED".equals(r.status())).count();
            // 这里只统计任务条目，manifest.anomalies 必须另行验收，退出码为 0 不代表异常已经消除。
            long pending = manifest.tasks().size() - confirmed - skipped;
            System.out.printf("PUBLISH：CONFIRMED=%d，SKIPPED=%d，未确认=%d，results=%s%n", confirmed, skipped, pending, resultPath);
            if (pending > 0) throw new SafeFailure("批次存在 FAILED / UNKNOWN / 尚未发布条目，保持停写并处理后重新核对");
        }
    }

    /** 校验版本、时区、唯一任务键及 deadline 的两种表示，防止人工编辑把消息期限与数据库期限分离。 */
    private static void validate(Manifest manifest) throws SafeFailure {
        if (manifest.version() != 1 || !ZONE.getId().equals(manifest.zone()) || manifest.tasks() == null || manifest.anomalies() == null)
            throw new SafeFailure("Manifest 版本/时区/集合非法");
        checkTarget(manifest.target());
        UUID.fromString(manifest.batchId());
        if (Long.parseLong(manifest.batchTimestamp()) <= 0) throw new SafeFailure("批次时间非法");
        Set<String> keys = new java.util.HashSet<>();
        for (Task task : manifest.tasks()) {
            if (Long.parseLong(task.orderId()) <= 0 || !Set.of("RESPONSE", "HANDLE").contains(task.type())
                    || epoch(LocalDateTime.parse(task.deadline(), TIME)) != Long.parseLong(task.deadlineEpochMillis()) || !keys.add(task.key()))
                throw new SafeFailure("Manifest 任务非法或重复");
        }
    }

    /** 发布只接受仍符合原恢复条件且 deadline 完全相同的记录；已删除、达标、终态或变更期限均跳过。 */
    private static boolean eligible(Connection connection, Task task) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM work_order WHERE id=?")) {
            statement.setLong(1, Long.parseLong(task.orderId()));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                Order order = order(rows);
                if (order.deleted() != 0) return false;
                boolean response = "RESPONSE".equals(task.type());
                LocalDateTime deadline = response ? order.response() : order.handle();
                return deadline != null && TIME.format(deadline).equals(task.deadline()) && epoch(deadline) == Long.parseLong(task.deadlineEpochMillis())
                        && (response ? RESPONSE_STATES.contains(order.status()) && order.accepted() == null && order.responseBreached() == 0
                                     : HANDLE_STATES.contains(order.status()) && order.submitted() == null && order.handleBreached() == 0);
            }
        }
    }

    private static Order order(ResultSet row) throws SQLException {
        return new Order(row.getLong("id"), row.getString("status"), row.getInt("deleted"), row.getObject("sla_response_deadline", LocalDateTime.class),
                row.getObject("sla_handle_deadline", LocalDateTime.class), row.getInt("sla_response_breached"), row.getInt("sla_handle_breached"),
                row.getObject("accepted_time", LocalDateTime.class), row.getObject("submitted_time", LocalDateTime.class));
    }

    /** 直接查询运行中的 Broker，只保留 timer 配置安全摘要；查询失败或字段缺失不回退到静态三天假设。 */
    private static BrokerConfig effectiveBrokerConfig() throws Exception {
        String docker = env("DOCKER_EXE", "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe");
        String address = env("ROCKETMQ_BROKER_ADDRESS", "localhost:10911");
        Process process = new ProcessBuilder(docker, "exec", env("ROCKETMQ_BROKER_CONTAINER", "rocketmq-broker"),
                "sh", "mqadmin", "getBrokerConfig", "-b", address).redirectErrorStream(true).start();
        // 输出异步读取，避免子进程管道填满后阻塞；不将 Broker 其他配置写入日志。
        var output = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return process.getInputStream().readAllBytes(); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new SafeFailure("RocketMQ 有效配置查询超时，禁止简单回滚");
        }
        String text = new String(output.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new SafeFailure("RocketMQ 有效配置查询失败，禁止简单回滚");
        var max = Pattern.compile("(?m)^\\s*timerMaxDelaySec\\s*(?:=|:)\\s*(\\d+)\\s*$").matcher(text);
        var enabled = Pattern.compile("(?m)^\\s*timerWheelEnable\\s*(?:=|:)\\s*(true|false)\\s*$").matcher(text);
        if (!max.find() || !enabled.find()) throw new SafeFailure("RocketMQ 有效 timer 配置缺失，禁止简单回滚");
        long seconds = Long.parseLong(max.group(1));
        if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000) throw new SafeFailure("RocketMQ 有效延迟上限非法");
        return new BrokerConfig(Long.toString(System.currentTimeMillis()), address, seconds, Boolean.parseBoolean(enabled.group(1)));
    }

    /** 旧消费者没有分段期限门卫；整批任何任务超限都必须停止回滚，不能提前分段或缩短原 deadline。 */
    private static void checkRocketLimit(List<Task> tasks, BrokerConfig config) throws SafeFailure {
        if (!config.timerWheelEnable()) throw new SafeFailure("RocketMQ timerWheelEnable=false，禁止简单回滚");
        long now = System.currentTimeMillis();
        for (Task task : tasks) {
            long delivery = Math.max(now + 1000, Long.parseLong(task.deadlineEpochMillis()));
            if (delivery - now > config.timerMaxDelaySec() * 1000)
                throw new SafeFailure("任务 " + task.key() + " 超出 RocketMQ 有效延迟上限 " + config.timerMaxDelaySec() + " 秒；保持停写并修复 RabbitMQ，不缩短 deadline");
        }
    }

    private interface Publisher extends AutoCloseable {
        Outcome send(Task task, String timestamp, String messageId);
        @Override void close() throws Exception;
    }

    /** 只发送到既有延迟拓扑，不声明队列；确认仅表示插件接收，不能保证未来到期路由成功。 */
    private static final class RabbitPublisher implements Publisher {
        private final com.rabbitmq.client.Connection connection;
        private Channel channel;
        RabbitPublisher() throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(env("RABBITMQ_HOST", "localhost"));
            factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
            factory.setUsername(env("RABBITMQ_USERNAME", "youcc"));
            factory.setPassword(requiredEnv("RABBITMQ_PASSWORD"));
            factory.setVirtualHost(env("RABBITMQ_VHOST", "/"));
            factory.setConnectionTimeout(3000);
            factory.setAutomaticRecoveryEnabled(false);
            connection = factory.newConnection("zhixiu-sla-recovery");
        }
        public Outcome send(Task task, String timestamp, String messageId) {
            boolean attempted = false;
            try {
                // 每条确认使用独立通道，超时后不会把迟到 ACK 错认成下一条的 ACK。
                channel = connection.createChannel();
                channel.confirmSelect();
                // 恢复原 deadline 和批次 timestamp；过期任务延迟 1ms，长任务首段最多 24h，由新消费者续投。
                long delay = Math.max(1, Math.min(Long.parseLong(task.deadlineEpochMillis()) - System.currentTimeMillis(), SEGMENT_MS));
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType("application/json").contentEncoding("UTF-8")
                        .deliveryMode(2).messageId(messageId).headers(Map.of("x-delay", delay)).build();
                byte[] body = payload(task, timestamp);
                attempted = true;
                // 延迟插件不支持 mandatory；不能用 Return 回调把等待消息判断为不可路由。
                channel.basicPublish(TOPIC, task.type(), false, properties, body);
                return channel.waitForConfirms(3000) ? new Outcome("CONFIRMED", "PLUGIN_ACK", messageId) : new Outcome("FAILED", "BROKER_NACK", null);
            } catch (TimeoutException e) {
                return new Outcome("UNKNOWN", "CONFIRM_TIMEOUT", null);
            } catch (Exception e) {
                // 一旦进入发送调用，连接异常无法证明 Broker 未接收；标记 UNKNOWN，不把它当成明确失败。
                return new Outcome(attempted ? "UNKNOWN" : "FAILED", attempted ? "SEND_RESULT_UNCERTAIN" : "BEFORE_SEND_FAILURE", null);
            } finally {
                if (channel != null) { try { channel.abort(); } catch (Exception ignored) { } channel = null; }
            }
        }
        public void close() throws Exception { connection.close(); }
    }

    /** 回滚使用旧 Topic/Tag 和原生绝对投递时间；关闭 SDK 内部重试，结果不确定交由固定批次记录处理。 */
    private static final class RocketPublisher implements Publisher {
        private final ClientServiceProvider provider = ClientServiceProvider.loadService();
        private final Producer producer;
        RocketPublisher() throws Exception {
            var builder = ClientConfiguration.newBuilder().setEndpoints(env("ROCKETMQ_ENDPOINTS", "localhost:8081"))
                    .enableSsl(Boolean.parseBoolean(env("ROCKETMQ_SSL_ENABLED", "false"))).setRequestTimeout(Duration.ofSeconds(3));
            String access = env("ROCKETMQ_ACCESS_KEY", "");
            String secret = env("ROCKETMQ_SECRET_KEY", "");
            if (!access.isEmpty() || !secret.isEmpty()) {
                if (access.isEmpty() || secret.isEmpty()) throw new SafeFailure("RocketMQ access/secret 必须同时配置");
                SessionCredentialsProvider credentials = new StaticSessionCredentialsProvider(access, secret);
                builder.setCredentialProvider(credentials);
            }
            producer = provider.newProducerBuilder().setClientConfiguration(builder.build()).setTopics(TOPIC).setMaxAttempts(1).build();
        }
        public Outcome send(Task task, String timestamp, String messageId) {
            boolean attempted = false;
            try {
                long deliveryTimestamp = Math.max(System.currentTimeMillis() + 1000, Long.parseLong(task.deadlineEpochMillis()));
                var message = provider.newMessageBuilder().setTopic(TOPIC).setTag(task.type()).setKeys(messageId)
                        .setBody(payload(task, timestamp)).setDeliveryTimestamp(deliveryTimestamp).build();
                attempted = true;
                var receipt = producer.send(message);
                return new Outcome("CONFIRMED", "BROKER_RECEIPT", receipt.getMessageId().toString());
            } catch (Exception e) {
                return new Outcome(attempted ? "UNKNOWN" : "FAILED", attempted ? "SEND_RESULT_UNCERTAIN" : "BEFORE_SEND_FAILURE", null);
            }
        }
        public void close() throws Exception { producer.close(); }
    }

    /** 保留现有 SlaTask 的四字段 JSON 契约，不能把 manifest 的运维字段写入业务消息。 */
    private static byte[] payload(Task task, String timestamp) throws IOException {
        return JSON.writeValueAsBytes(Map.of("orderId", task.orderId(), "type", task.type(), "deadline", task.deadlineEpochMillis(), "timestamp", timestamp));
    }

    private static Result result(Manifest manifest, String hash, Task task, Outcome outcome) {
        return new Result(hash, manifest.batchId(), task.key(), manifest.target(), outcome.status(), Long.toString(System.currentTimeMillis()), outcome.reason(), outcome.brokerMessageId());
    }

    /** 在同一排他锁通道内重读结果，校验原批次摘要；损坏尾部保留原样，禁止自动截断掩盖 UNKNOWN。 */
    private static Map<String, Result> readResults(FileChannel journal, String hash, Manifest manifest) throws Exception {
        Map<String, Result> results = new HashMap<>();
        Set<String> keys = new java.util.HashSet<>();
        manifest.tasks().forEach(task -> keys.add(task.key()));
        if (journal.size() > Integer.MAX_VALUE) throw new SafeFailure("结果文件过大，需先归档人工核对");
        ByteBuffer bytes = ByteBuffer.allocate((int) journal.size());
        journal.position(0);
        while (bytes.hasRemaining()) { if (journal.read(bytes) < 0) break; }
        for (String line : new String(bytes.array(), StandardCharsets.UTF_8).split("\\r?\\n")) {
            if (line.isBlank()) continue;
            Result result;
            try { result = JSON.readValue(line, Result.class); }
            catch (Exception e) { throw new SafeFailure("结果文件存在不完整记录；保留文件，人工核对该条 UNKNOWN 后再恢复，不自动覆盖"); }
            if (!hash.equals(result.manifestSha256()) || !manifest.batchId().equals(result.batchId()) || !manifest.target().equals(result.target())
                    || !keys.contains(result.key()) || !Set.of("CONFIRMED", "FAILED", "UNKNOWN", "SKIPPED").contains(result.status()))
                throw new SafeFailure("结果文件与固定 Manifest 不一致或记录非法");
            results.put(result.key(), result);
        }
        return results;
    }

    /** 追加完整 UTF-8/CRLF 结果并强制落盘；确认后写盘失败仍是不确定窗口，调用方立即停止批次。 */
    private static void append(FileChannel journal, Result result) throws IOException {
        byte[] bytes = (JSON.writeValueAsString(result) + "\r\n").getBytes(StandardCharsets.UTF_8);
        journal.position(journal.size());
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) journal.write(buffer);
        journal.force(true);
    }

    /** CREATE_NEW 防止覆盖已 Review 的固定批次；文本无 BOM、CRLF，并等待文件落盘。 */
    private static void writeNew(Path path, byte[] bytes) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n", "\r\n") + "\r\n";
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
    }

    /** 凭证仅来自当前进程环境；工具所有 SQL 都是 SELECT，部署时应优先使用仅有 SELECT 权限的账号。 */
    private static Connection database() throws SQLException, SafeFailure {
        return DriverManager.getConnection(env("DB_URL", "jdbc:mysql://localhost:3307/zhixiu?serverTimezone=Asia/Shanghai"),
                env("DB_USERNAME", "root"), requiredEnv("DB_PASSWORD"));
    }
    private static long epoch(LocalDateTime time) { return time.atZone(ZONE).toInstant().toEpochMilli(); }
    private static String messageId(Manifest manifest, Task task) { return "SLA-RECOVERY:" + manifest.batchId() + ":" + task.key(); }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
    private static String requiredEnv(String key) throws SafeFailure { String value = env(key, ""); if (value.isEmpty()) throw new SafeFailure("缺少环境变量 " + key); return value; }
    private static void checkTarget(String target) throws SafeFailure { if (!Set.of("rabbitmq", "rocketmq").contains(target)) throw new SafeFailure("target 只能为 rabbitmq 或 rocketmq"); }
    private static String required(Map<String, String> options, String key) throws SafeFailure { String value = options.get(key); if (value == null || value.isBlank()) throw new SafeFailure("缺少参数 --" + key); return value; }
    private static Map<String, String> options(String[] args) throws SafeFailure {
        Map<String, String> options = new HashMap<>();
        options.put("mode", "preview");
        int index = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            if (!Set.of("preview", "publish").contains(args[0])) throw new SafeFailure("模式只能为 preview 或 publish");
            options.put("mode", args[0]); index++;
        }
        while (index < args.length) {
            String key = args[index++];
            if ("--help".equals(key)) { options.put("help", "true"); continue; }
            if (!Set.of("--manifest", "--target").contains(key) || index >= args.length) throw new SafeFailure("参数非法，使用 --help 查看用法");
            if (options.put(key.substring(2), args[index++]) != null) throw new SafeFailure("参数重复 " + key);
        }
        return options;
    }
    private static final class SafeFailure extends Exception { SafeFailure(String message) { super(message); } }
}
