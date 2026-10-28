package com.ycc.zhixiucloud.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.dto.AiFormDraft;
import com.ycc.zhixiucloud.entity.AiChatMessage;
import com.ycc.zhixiucloud.entity.AiChatSession;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.AiChatMessageMapper;
import com.ycc.zhixiucloud.mapper.AiChatSessionMapper;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.request.AiChatRequestVO;
import com.ycc.zhixiucloud.request.AiDraftConfirmRequestVO;
import com.ycc.zhixiucloud.request.AiSessionCreateRequestVO;
import com.ycc.zhixiucloud.request.CreateOrderRequestVO;
import com.ycc.zhixiucloud.response.AiDraftResponseVO;
import com.ycc.zhixiucloud.response.AiMessageResponseVO;
import com.ycc.zhixiucloud.response.AiSessionCreateResponseVO;
import com.ycc.zhixiucloud.response.AiSessionResponseVO;
import com.ycc.zhixiucloud.response.OrderCreateResponseVO;
import com.ycc.zhixiucloud.service.AiAssistantService;
import com.ycc.zhixiucloud.service.WorkOrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能维修助手实现。
 *
 * <p>处理链路（与开发文档「七、Spring AI 使用规范」一致）：
 * 每轮先给解决方案 → 用户反馈已解决或继续排查 → 按需生成 15 分钟草稿 → 确认转工单。</p>
 *
 * <p>降级策略：未配置大模型 API Key 时 ChatClient/VectorStore 不存在，仍按 SSE 契约逐事件返回，
 * 回答改为提示用户走手动报修，保证前端交互链路可用。</p>
 *
 * <p>关键设计：ChatClient、VectorStore 通过 ObjectProvider 延迟获取，未配置 API Key 时二者为空，
 * 服务仍可启动并工作；会话归属校验统一收敛到 requireOwnedSession，防止越权访问；
 * SSE 事件 data 均为 JSON 字符串；会话写操作通过同一分布式锁串行化，短事务完成后才释放锁，
 * 模型调用位于 SSE 线程且不占用事务连接。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    /** 消息发送方：用户 */
    private static final String SENDER_USER = "USER";

    /** 消息发送方：AI */
    private static final String SENDER_AI = "AI";

    /** 意图：问答 */
    private static final String INTENT_QA = "QA";

    /** 意图：自助排查 */
    private static final String INTENT_TROUBLESHOOT = "TROUBLESHOOT";

    /** 意图：收集信息生成工单 */
    private static final String INTENT_FILL_FORM = "FILL_FORM";

    /** 会话解决状态：已转工单 */
    private static final int RESOLVED_TRANSFER = 2;

    /** 会话进行中 */
    private static final int RESOLVED_ONGOING = 0;

    /** 用户明确反馈已解决 */
    private static final int RESOLVED_SELF = 1;

    private static final String ACTION_CHAT = "CHAT";
    private static final String ACTION_UNRESOLVED = "UNRESOLVED";
    private static final String ACTION_GENERATE_DRAFT = "GENERATE_DRAFT";
    private static final String ACTION_RESOLVED = "RESOLVED";

    /** 从草稿消息创建时间起计算，查看和继续聊天不续期 */
    private static final Duration DRAFT_VALIDITY = Duration.ofMinutes(15);

    /** RAG 检索条数 */
    private static final int RAG_TOPK = 3;

    /** 注入模型的最近对话条数 */
    private static final int HISTORY_LIMIT = 10;

    /** 会话标题截断长度 */
    private static final int TITLE_MAX = 40;

    /** 工单标题截断长度 */
    private static final int ORDER_TITLE_MAX = 100;

    /** AI 未启用时的降级提示 */
    private static final String DEGRADE_TIP = "AI 助手当前未启用（未配置大模型 API Key），请改用「手动提交报修」提交工单。";

    /** 故障持续时间识别正则 */
    private static final Pattern DURATION_PATTERN = Pattern.compile("(约)?\\d+\\s*(分钟|小时|天|周|个月|月)");

    /** 槽位提取时注入的最大对话条数，控制提示词长度与 token 成本 */
    private static final int DRAFT_CONTEXT_LIMIT = 20;

    /** 安全隐患白名单，与开发文档「safetyFlag」枚举取值一致 */
    private static final List<String> SAFETY_FLAGS = List.of("涉电", "涉水", "涉气", "高空");

    /**
     * 工单草稿提取的系统提示词。
     *
     * <p>与对话回答使用的主提示词分开：这里只做「读完整对话 → 归纳成工单字段」的抽取任务，
     * 明确要求结合助手的排查结论、忽略无关话题、禁止照抄业主原话，从源头修正草稿照搬原文的问题。</p>
     */
    private static final String DRAFT_SYSTEM_PROMPT = """
            你是物业报修系统的工单信息提取器，需要把业主与助手的完整对话整理成一张工单草稿。

            请先通读整段对话，判断业主最终真正要报修的是哪一件事：
            1. 对话中可能夹杂与报修无关的话题，直接忽略；
            2. 业主一次提到多个问题时，只保留助手已经确认受理的那一项；
            3. 助手的排查分析与结论是判断「报修哪一项」的重要依据，不能只看业主的第一句话。

            【信息来源规则】
            1. 只有「业主：」之后的内容才是业主陈述的事实；「助手：」是系统回答。
            2. 助手回答里的排查步骤、安全提醒、举例的类型、建议方案都不代表业主做过或同意，
               禁止据此填写 description、safetyFlag、deviceInfo、faultDuration。
            3. 助手明确表示「按某类型为您建单」时，才可以作为 categoryId 的依据。

            字段要求：
            - categoryId：只能取下方「可选的报修类型」中列出的 ID；没有合适的类型必须留空，禁止编造。
            - title：不超过 30 字，写成「故障对象 + 现象」，例如「卧室空调不制冷且外机异响」，禁止照抄业主原话。
            - description：60 到 150 字，综合整段对话写成连贯描述，包含故障现象、具体位置、出现或持续的时间、
              业主已尝试的处理。只写业主明确陈述的事实：助手给出的自助排查建议不代表业主已经做过，
              不得写成「业主已尝试」；没提到的一律不写，禁止推测，禁止给维修建议，禁止原样复制句子。
            - urgency：1 低、2 中、3 高、4 紧急。涉及漏电、触电、燃气泄漏、跑水、电梯困人等安全隐患取 4；
              业主明确催促取 3；无法判断取 2。
            - faultDuration：对话中提到的持续时间，如「约 2 天」；没有提到就留空。
            - affectScope：1 本户、2 本层、3 整栋；无法判断取 1。
            - safetyFlag：只能从 涉电、涉水、涉气、高空 中选择，多项用英文逗号连接；没有就留空。
            - deviceInfo：对话中提到的品牌、型号或设备规格；没有提到就留空，禁止臆测。
            """;

    private final AiChatSessionMapper sessionMapper;

    private final AiChatMessageMapper messageMapper;

    private final RepairCategoryMapper categoryMapper;

    /** 工单 Mapper：按 ai_draft_id 反查会话已生成的工单，供前端展示「查看工单」 */
    private final WorkOrderMapper workOrderMapper;

    private final WorkOrderService workOrderService;

    private final RedissonClient redissonClient;

    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper;

    /** 对话客户端可能未装配（AI 未启用） */
    private final ObjectProvider<ChatClient> chatClientProvider;

    /** 向量库可能未装配（AI 未启用） */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /** SSE 专用线程池，Bean 名 sseExecutor */
    @Resource(name = "sseExecutor")
    private ThreadPoolTaskExecutor sseExecutor;

    /** 槽位追问上限，默认 2 */
    @Value("${repair.ai.follow-up-limit:2}")
    private int followUpLimit;

    /** 系统提示词，启动时一次性读入内存 */
    private String systemPrompt = "";

    /**
     * 启动时加载系统提示词模板到内存。
     *
     * <p>提示词属于静态资源，启动后不会变化，缓存可避免每轮对话重复读磁盘；
     * 读取失败只记录告警并退化为空提示词，不阻断应用启动。</p>
     */
    @PostConstruct
    void loadSystemPrompt() {
        // 读取模板失败只告警，不阻断应用启动
        try {
            // 从 classpath 读取提示词模板
            ClassPathResource resource = new ClassPathResource("prompts/assistant-system.txt");
            // 按 UTF-8 读入字符串并缓存
            systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            // 读取失败退化为空提示词
        } catch (IOException e) {
            // 读取失败不阻断启动，退化为空提示词
            log.warn("系统提示词读取失败，使用空提示词", e);
        }
    }

    // ------------------------------------------------------------------ 会话

    /**
     * 创建 AI 会话。
     *
     * <p>标题可空：为空时由首条用户消息摘要回填，避免创建前额外调用模型。会话状态初始为进行中，
     * 消息数初始为 0；插入后回填主键并立即返回，便于前端马上发起 SSE 对话。</p>
     *
     * @param requestVO 创建请求，可为 null；title 有值时去除首尾空格后保存
     * @return 仅含新会话 ID 的响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiSessionCreateResponseVO createSession(AiSessionCreateRequestVO requestVO) {
        // 当前登录业主 ID
        Long userId = SecurityUtils.currentUserId();
        // 组装会话实体
        AiChatSession session = new AiChatSession();
        // 归属当前登录业主
        session.setUserId(userId);
        // 标题：传了用传入值，否则留空待首条消息生成摘要
        session.setTitle(requestVO == null || !StringUtils.hasText(requestVO.getTitle())
                ? null : requestVO.getTitle().trim());
        // 初始消息数为 0
        session.setMessageCount(0);
        // 初始状态为进行中
        session.setResolved(RESOLVED_ONGOING);
        // 落库并回填主键
        sessionMapper.insert(session);
        // 记录会话创建信息
        log.info("创建 AI 会话：sessionId={}, userId={}", session.getId(), userId);
        // 返回会话 ID
        return new AiSessionCreateResponseVO(session.getId());
    }

    /**
     * 发送用户消息并启动 SSE 流式回答。
     *
     * <p>请求线程获取登录态并传给异步线程；异步线程先取得会话锁，短事务保存用户消息，
     * 再生成回答，避免并发按钮操作或模型调用长时间占用事务连接。</p>
     *
     * @param sessionId 会话 ID
     * @param requestVO 普通消息或最新回答的按钮动作
     * @param emitter   SSE 发射器，由控制器持有并返回前端
     * @throws BizException 会话不存在（NOT_FOUND）或不属于当前用户（FORBIDDEN）时抛出
     */
    @Override
    public void chat(Long sessionId, AiChatRequestVO requestVO, SseEmitter emitter) {
        Long userId = SecurityUtils.currentUserId();
        requireOwnedSession(sessionId, userId);
        if (ACTION_CHAT.equals(requestVO.getAction()) && !StringUtils.hasText(requestVO.getContent())) {
            throw BizException.of(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (!ACTION_CHAT.equals(requestVO.getAction()) && requestVO.getMessageId() == null) {
            throw BizException.of(ResultCode.PARAM_ERROR, "请提供最新回答 ID");
        }
        sseExecutor.execute(() -> streamAction(sessionId, userId, requestVO, emitter));
    }

    /**
     * 查询当前业主的全部 AI 会话。
     *
     * @return 按最近更新时间倒序排列的会话列表；无数据时返回空列表
     */
    @Override
    public List<AiSessionResponseVO> listSessions() {
        // 当前登录业主 ID
        Long userId = SecurityUtils.currentUserId();
        // 按更新时间倒序取本人全部会话
        List<AiChatSession> sessions = sessionMapper.selectList(Wrappers.<AiChatSession>lambdaQuery()
                .eq(AiChatSession::getUserId, userId)
                // 空会话（还没发过任何消息）不进入历史列表
                .gt(AiChatSession::getMessageCount, 0)
                .orderByDesc(AiChatSession::getUpdateTime, AiChatSession::getId));
        // 会话 ID -> 已生成的工单，供前端展示「查看工单」入口
        Map<Long, WorkOrder> orderMap = aiOrdersBySession(sessions);
        // 逐条转换为出参
        return sessions.stream().map(session -> toSessionVO(session, orderMap.get(session.getId()))).toList();
    }

    /**
     * 查询这批会话通过 AI 草稿生成的工单。
     *
     * <p>关联链路：work_order.ai_draft_id = ai_chat_message.id，再由消息的 session_id 归到会话。
     * 分三次查询完成，避免按会话逐条查询造成 N+1。</p>
     *
     * @param sessions 会话列表
     * @return 会话 ID 到工单的映射；没有关联工单时返回空 Map
     */
    private Map<Long, WorkOrder> aiOrdersBySession(List<AiChatSession> sessions) {
        // 没有会话时无需查询
        if (sessions.isEmpty()) {
            // 返回空映射
            return Map.of();
        }
        // 当前这批会话的 ID
        List<Long> sessionIds = sessions.stream().map(AiChatSession::getId).toList();
        // 取出这些会话里带草稿的 AI 消息
        List<AiChatMessage> draftMessages = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .in(AiChatMessage::getSessionId, sessionIds)
                .isNotNull(AiChatMessage::getFormDraft));
        // 没有草稿消息则说明还没生成过工单
        if (draftMessages.isEmpty()) {
            // 返回空映射
            return Map.of();
        }
        // 草稿消息 ID -> 会话 ID
        Map<Long, Long> messageToSession = draftMessages.stream()
                .collect(Collectors.toMap(AiChatMessage::getId, AiChatMessage::getSessionId, (a, b) -> a));
        // 按草稿消息 ID 反查工单
        List<WorkOrder> orders = workOrderMapper.selectList(Wrappers.<WorkOrder>lambdaQuery()
                .in(WorkOrder::getAiDraftId, messageToSession.keySet()));
        // 组装会话 ID -> 工单；同一会话有多张工单时保留最新的
        return orders.stream().collect(Collectors.toMap(
                order -> messageToSession.get(order.getAiDraftId()),
                order -> order,
                (first, second) -> first.getId() > second.getId() ? first : second));
    }

    /**
     * 查询指定会话的消息记录。
     *
     * @param sessionId 会话 ID
     * @return 按创建时间正序排列的消息列表
     * @throws BizException 会话不存在或不属于当前用户时抛出
     */
    @Override
    public List<AiMessageResponseVO> listMessages(Long sessionId) {
        // 校验会话归属
        requireOwnedSession(sessionId, SecurityUtils.currentUserId());
        // 按时间正序取全部消息
        List<AiChatMessage> messages = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime, AiChatMessage::getId));
        // 逐条转换为出参
        return messages.stream().map(this::toMessageVO).toList();
    }

    /**
     * 获取会话最新一次生成的工单草稿。
     *
     * @param sessionId 会话 ID
     * @return 草稿消息 ID 与反序列化后的表单内容
     * @throws BizException 会话不存在、不属于当前用户，或尚无草稿（AI_DRAFT_EMPTY）时抛出
     */
    @Override
    public AiDraftResponseVO getDraft(Long sessionId) {
        AiChatSession session = requireOwnedSession(sessionId, SecurityUtils.currentUserId());
        return toDraftVO(requireValidDraft(session));
    }

    /**
     * 确认草稿并生成正式工单。
     *
     * <p>合并规则：用户在确认请求中显式提交的字段优先，其次使用 AI 草稿值，紧急程度/影响范围
     * 最终兜底为 2/1。报修类型是生成工单的最小必要槽位，缺失时直接拒绝。工单创建成功后
     * 会话标记为「已转工单」；整个方法在同一事务内，任一步失败都会回滚。</p>
     *
     * @param sessionId 会话 ID
     * @param requestVO 用户在确认表单中填写/修改的字段
     * @return 新建工单的响应信息
     * @throws BizException 会话/草稿不存在、无权限、槽位不足（AI_SLOT_INSUFFICIENT）时抛出
     */
    @Override
    public OrderCreateResponseVO confirmDraft(Long sessionId, AiDraftConfirmRequestVO requestVO) {
        Long userId = SecurityUtils.currentUserId();
        // 锁在事务外取得，TransactionTemplate 返回时已提交，随后才释放锁。
        return withSessionLock(sessionId, () -> transactionTemplate.execute(status -> {
            AiChatSession session = requireOwnedSession(sessionId, userId);
            if (Objects.equals(session.getResolved(), RESOLVED_TRANSFER)) {
                AiChatMessage submittedDraft = messageMapper.selectById(requestVO.getDraftId());
                if (submittedDraft != null && Objects.equals(submittedDraft.getSessionId(), sessionId)) {
                    WorkOrder existing = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                            .eq(WorkOrder::getAiDraftId, submittedDraft.getId()).last("LIMIT 1"));
                    if (existing != null) {
                        return new OrderCreateResponseVO(existing.getId(), existing.getOrderNo());
                    }
                }
                throw BizException.of(ResultCode.CONFLICT, "本次排查已转工单，请查看已提交的工单");
            }
            AiChatMessage draftMessage = requireValidDraft(session);
            if (!Objects.equals(draftMessage.getId(), requestVO.getDraftId())) {
                throw BizException.of(ResultCode.AI_DRAFT_EMPTY, "草稿已更新，请查看当前草稿后再提交");
            }
            AiFormDraft draft = JSONUtil.toBean(draftMessage.getFormDraft(), AiFormDraft.class);
            CreateOrderRequestVO createVO = mergeDraft(draft, requestVO);
            if (createVO.getCategoryId() == null) {
                throw BizException.of(ResultCode.AI_SLOT_INSUFFICIENT, "请先选择报修类型");
            }
            OrderCreateResponseVO created = workOrderService.createFromAiDraft(createVO, draftMessage.getId());
            updateResolved(sessionId, RESOLVED_TRANSFER);
            log.info("AI 草稿确认转工单：sessionId={}, draftId={}, orderId={}",
                    sessionId, draftMessage.getId(), created.getOrderId());
            return created;
        }));
    }

    /**
     * 删除会话及其全部消息（逻辑删除）。
     *
     * <p>两次删除放在同一事务中，避免出现「会话已删、消息残留」的中间状态。</p>
     *
     * @param sessionId 会话 ID
     * @throws BizException 会话不存在或不属于当前用户时抛出
     */
    @Override
    public void deleteSession(Long sessionId) {
        Long userId = SecurityUtils.currentUserId();
        withSessionLock(sessionId, () -> {
            transactionTemplate.executeWithoutResult(status -> {
                requireOwnedSession(sessionId, userId);
                sessionMapper.deleteById(sessionId);
                messageMapper.delete(Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getSessionId, sessionId));
            });
            return null;
        });
        log.info("删除 AI 会话：sessionId={}", sessionId);
    }

    // ------------------------------------------------------------------ 流式处理

    /**
     * 会话写操作统一串行，模型调用不持有数据库事务。
     * 只有生成草稿动作进行槽位提取；普通对话和未解决反馈始终先提供方案。
     */
    private void streamAction(Long sessionId, Long userId, AiChatRequestVO requestVO, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        try {
            withSessionLock(sessionId, () -> {
                AiChatSession session = requireOwnedSession(sessionId, userId);
                String action = requestVO.getAction();
                AiChatMessage latest = ACTION_CHAT.equals(action)
                        ? null : requireLatestAnswer(sessionId, requestVO.getMessageId());

                // 已解决的同一次反馈可安全重试，不重复统计或生成消息。
                if (ACTION_RESOLVED.equals(action) && Objects.equals(session.getResolved(), RESOLVED_SELF)) {
                    sendDone(emitter, latest.getId(), session);
                    return null;
                }
                requireOngoingSession(session);

                if (ACTION_RESOLVED.equals(action)) {
                    transactionTemplate.executeWithoutResult(status -> updateResolved(sessionId, RESOLVED_SELF));
                    session.setResolved(RESOLVED_SELF);
                    sendDone(emitter, latest.getId(), session);
                } else if (ACTION_GENERATE_DRAFT.equals(action)) {
                    generateDraft(session, latest, emitter);
                } else {
                    String content = ACTION_UNRESOLVED.equals(action)
                            ? "问题还没有解决，请提供其他排查方案，并询问还需要哪些详细信息。"
                            : requestVO.getContent().trim();
                    // 消息与会话标题在短事务中保存，提交后再调用模型。
                    transactionTemplate.executeWithoutResult(status -> {
                        saveMessage(sessionId, SENDER_USER, content, null, null, null, null, null);
                        if (!StringUtils.hasText(session.getTitle())) {
                            AiChatSession titleUpdate = new AiChatSession();
                            titleUpdate.setId(sessionId);
                            titleUpdate.setTitle(abbreviate(content, TITLE_MAX));
                            sessionMapper.updateById(titleUpdate);
                        }
                    });
                    answerQuestion(session, content, ACTION_UNRESOLVED.equals(action), emitter);
                }
                return null;
            });
            emitter.complete();
            log.info("AI 会话动作完成：sessionId={}, action={}, cost={}ms",
                    sessionId, requestVO.getAction(), System.currentTimeMillis() - start);
        } catch (BizException e) {
            log.warn("AI 会话动作失败：sessionId={}, action={}, code={}",
                    sessionId, requestVO.getAction(), e.getCode());
            completeWithBusinessError(emitter, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("AI 流式会话异常：sessionId={}, action={}", sessionId, requestVO.getAction(), e);
            completeWithBusinessError(emitter, ResultCode.AI_INVOKE_FAILED.getCode(),
                    ResultCode.AI_INVOKE_FAILED.getMessage());
        }
    }

    /**
     * 流式提供本轮方案，不提取或自动更新草稿。
     */
    private void answerQuestion(AiChatSession session, String content, boolean unresolved, SseEmitter emitter) {
        Long sessionId = session.getId();
        String userText = aggregateUserText(sessionId);
        String intent = unresolved ? INTENT_TROUBLESHOOT : detectIntent(userText);
        sendEvent(emitter, "intent", Map.of("intent", intent));
        List<Document> documents = retrieve(userText);
        List<String> citationIds = documents.stream()
                .map(document -> document.getMetadata().getOrDefault("knowledgeId", document.getId()).toString())
                .distinct().toList();
        if (!citationIds.isEmpty()) {
            sendEvent(emitter, "citations", Map.of("ids", citationIds));
        }

        StringBuilder answer = new StringBuilder();
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            answer.append(DEGRADE_TIP);
            sendEvent(emitter, "message", Map.of("delta", DEGRADE_TIP));
        } else {
            String system = buildSystemPrompt(documents);
            if (unresolved) {
                system += "\n用户已明确反馈问题未解决。结合前文换一种排查方向，不把此前建议视为用户已尝试；"
                        + "先给可执行的其他方案，再追问一到两个能区分故障原因的关键问题。";
            }
            chatClient.prompt().system(system).user(buildUserPrompt(sessionId, content))
                    .stream().content().toIterable().forEach(delta -> {
                        answer.append(delta);
                        sendEvent(emitter, "message", Map.of("delta", delta));
                    });
        }
        if (!StringUtils.hasText(answer)) {
            throw BizException.of(ResultCode.AI_INVOKE_FAILED);
        }
        Long messageId = transactionTemplate.execute(status ->
                saveMessage(sessionId, SENDER_AI, answer.toString(), intent,
                        citationIds.isEmpty() ? null : String.join(",", citationIds), null, null, null));
        sendDone(emitter, messageId, session);
    }

    /**
     * 主动生成一份草稿；已有有效草稿时恢复原草稿，查看和再次点击均不续期。
     */
    private void generateDraft(AiChatSession session, AiChatMessage latest, SseEmitter emitter) {
        AiChatMessage existing = latestDraftMessage(session.getId());
        if (isDraftValid(session, existing)) {
            sendEvent(emitter, "draft", toDraftVO(existing));
            sendDone(emitter, latest.getId(), session);
            return;
        }

        sendEvent(emitter, "intent", Map.of("intent", INTENT_FILL_FORM));
        AiFormDraft draft = extractDraft(session.getId(), null);
        List<String> filled = filledSlots(draft);
        List<String> missing = missingSlots(draft);
        sendEvent(emitter, "slot", Map.of("filled", filled, "missing", missing));
        String answer = "已整理工单草稿，请核对并补充信息后提交。草稿有效 15 分钟，查看和继续聊天不会延长有效期。";
        sendEvent(emitter, "message", Map.of("delta", answer));
        String question = !missing.isEmpty() && followUpCount(session.getId()) < followUpLimit
                ? buildFollowUpQuestion(missing) : null;
        if (question != null) {
            sendEvent(emitter, "followup", Map.of("question", question));
        }
        String completeAnswer = question == null ? answer : answer + "\n\n" + question;
        Long messageId = transactionTemplate.execute(status ->
                saveMessage(session.getId(), SENDER_AI, completeAnswer, INTENT_FILL_FORM, null,
                        JSONUtil.toJsonStr(draft), joinSlots(filled), joinSlots(missing)));
        // 从已提交的消息读取创建时间，使 TTL 与刷新后的读取保持一致。
        sendEvent(emitter, "draft", toDraftVO(messageMapper.selectById(messageId)));
        sendDone(emitter, messageId, session);
    }

    private void sendDone(SseEmitter emitter, Long messageId, AiChatSession session) {
        sendEvent(emitter, "done", Map.of(
                "messageId", messageId,
                "resolved", Objects.equals(session.getResolved(), RESOLVED_SELF),
                "sessionResolved", session.getResolved(),
                "hasDraft", isDraftValid(session, latestDraftMessage(session.getId()))));
    }

    /**
     * 异步业务失败仍使用 HTTP 200 的命名 error 事件，随后关闭连接。
     */
    private void completeWithBusinessError(SseEmitter emitter, int code, String message) {
        try {
            sendEvent(emitter, "error", Map.of("code", code, "message", message));
            emitter.complete();
        } catch (Exception ignored) {
            // 连接已断开时不再重复尝试发送。
        }
    }

    /**
     * 使用统一 Jackson 配置保护雪花 ID 精度，并统一日期时间格式。
     */
    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 事件推送失败：" + event, e);
        }
    }

    // ------------------------------------------------------------------ 规则识别

    /**
     * 普通对话只识别问答或故障排查，填单意图由生成草稿按钮明确触发。
     */
    private String detectIntent(String text) {
        if (containsAny(text, "报修", "上门", "维修", "派单", "怎么办", "为什么", "不制冷", "不工作",
                "漏水", "跳闸", "异响", "打不开", "无法", "坏了", "堵了", "失灵")) {
            return INTENT_TROUBLESHOOT;
        }
        return INTENT_QA;
    }

    /**
     * 生成工单草稿：优先让大模型基于完整对话做结构化提取，失败时回退规则。
     *
     * <p>模型路径对应开发文档 7.3「结构化输出 .call().entity(FormDraft.class)」：
     * 输入是业主与助手的整段对话（含本轮回答），输出是结构化工单字段。
     * 模型不可用、调用异常或返回空结果时不阻断主链路，改用规则提取兜底，
     * 保证 AI 未启用时仍能生成草稿。</p>
     *
     * @param sessionId 会话 ID
     * @param answer    本轮刚生成、尚未落库的助手回答
     * @return 校验后的工单草稿
     */
    private AiFormDraft extractDraft(Long sessionId, String answer) {
        // 拼接完整对话上下文：历史消息 + 本轮回答
        String conversation = conversationText(sessionId, answer);
        // 规则兜底只使用用户陈述，助手的排查建议不作为故障事实。
        String userText = aggregateUserText(sessionId);
        // 延迟获取对话客户端，AI 未启用时为空
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        // 有模型时走结构化提取
        if (chatClient != null) {
            // 模型调用与解析都可能失败，失败不能影响本轮回答
            try {
                // 让模型按工单字段输出结构化结果
                AiFormDraft draft = chatClient.prompt()
                        // 注入抽取规则与可选类型清单
                        .system(buildDraftSystemPrompt())
                        // 注入完整对话
                        .user(buildDraftPrompt(conversation))
                        // 结构化输出为草稿对象
                        .call()
                        .entity(AiFormDraft.class);
                // 拿到结果后统一做字段校验与纠偏
                if (draft != null) {
                    // 返回校验后的草稿
                    return normalizeDraft(draft, userText);
                }
            // 模型异常时记录并降级
            } catch (Exception e) {
                // 提取失败只影响草稿质量，降级为规则提取
                log.warn("AI 槽位提取失败，降级为规则提取：sessionId={}", sessionId, e);
            }
        }
        // 无模型或提取失败时用用户陈述做规则兜底，未知信息留待用户补充
        return normalizeDraft(extractDraftByRules(userText), userText);
    }

    /**
     * 拼接槽位提取用的完整对话上下文。
     *
     * <p>与 aggregateUserText 只取用户消息不同，这里同时包含助手回答：助手的排查结论
     * 往往已经明确指出真正要受理的问题，模型据此判断比只看业主原话更准确。
     * 只取最近 DRAFT_CONTEXT_LIMIT 条，避免历史过长推高 token 成本。</p>
     *
     * @param sessionId 会话 ID
     * @param answer    本轮刚生成、尚未落库的助手回答
     * @return 「业主/助手」轮次拼接的对话文本
     */
    private String conversationText(Long sessionId, String answer) {
        // 按时间正序取本会话消息
        List<AiChatMessage> messages = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime, AiChatMessage::getId));
        // 上下文构建器
        StringBuilder builder = new StringBuilder();
        // 只保留最近若干条，历史过长时截断头部
        int from = Math.max(0, messages.size() - DRAFT_CONTEXT_LIMIT);
        // 逐条拼接为「角色：内容」
        for (int i = from; i < messages.size(); i++) {
            // 当前遍历到的消息
            AiChatMessage message = messages.get(i);
            // 角色名：用户侧的库里记为 USER，其余都按助手处理
            String role = SENDER_USER.equals(message.getSender()) ? "业主" : "助手";
            // 追加一轮对话
            builder.append(role).append("：").append(message.getContent()).append('\n');
        }
        // 本轮回答尚未落库，需要手动追加到上下文末尾
        if (StringUtils.hasText(answer)) {
            // 追加助手本轮回答
            builder.append("助手：").append(answer).append('\n');
        }
        // 返回完整上下文
        return builder.toString();
    }

    /**
     * 组装槽位提取的用户提示词。
     *
     * @param conversation 完整对话上下文
     * @return 用户提示词
     */
    private String buildDraftPrompt(String conversation) {
        // 给定对话并要求按系统提示词的字段规则整理
        return "【完整对话】\n" + conversation + "\n请基于以上对话整理工单字段。";
    }

    /**
     * 组装槽位提取的系统提示词，并追加带 ID 的报修类型清单。
     *
     * <p>类型清单必须带 ID，模型才能返回可直接落库的 categoryId；
     * 同时通过「只能从中选择」约束防止编造类型。</p>
     *
     * @return 完整系统提示词
     */
    private String buildDraftSystemPrompt() {
        // 固定规则 + 动态类型清单
        return DRAFT_SYSTEM_PROMPT + "\n\n【可选的报修类型（格式：ID 子类型（父类型））】\n" + categoryIdNames();
    }

    /**
     * 列出启用中的二级报修类型及其父类型，供模型选择。
     *
     * <p>一次性取出全部类型后在内存拼装父类型名，避免逐个查询父分类。</p>
     *
     * @return 每行「ID 子类型（父类型）」
     */
    private String categoryIdNames() {
        // 一次性取出全部启用中的类型
        List<RepairCategory> all = categoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .eq(RepairCategory::getStatus, 1));
        // 父类型 ID -> 名称，用于拼装展示路径
        Map<Long, String> parents = all.stream()
                .filter(item -> item.getParentId() == null || item.getParentId() == 0L)
                .collect(Collectors.toMap(RepairCategory::getId, RepairCategory::getName, (a, b) -> a));
        // 只列二级类型，一行一个
        return all.stream()
                .filter(item -> item.getParentId() != null && item.getParentId() != 0L)
                .filter(item -> StringUtils.hasText(item.getName()))
                .map(item -> item.getId() + " " + item.getName()
                        + "（" + parents.getOrDefault(item.getParentId(), "其他") + "）")
                .collect(Collectors.joining("\n"));
    }

    /**
     * 校验并纠偏模型返回的草稿字段。
     *
     * <p>模型输出不可全信：类型 ID 必须命中启用中的二级类型，枚举值必须落在合法区间，
     * 安全隐患只能是白名单标签；缺失的紧急程度与影响范围按文档默认值补齐，
     * 未提供的故障时长和设备规格保持空值，由用户核对补充。</p>
     *
     * @param draft    模型返回的草稿
     * @param userText 用户陈述，用于类型与描述的规则兜底
     * @return 校验后的草稿
     */
    private AiFormDraft normalizeDraft(AiFormDraft draft, String userText) {
        // 查询启用中的二级类型，用于校验模型给的类型 ID
        List<RepairCategory> categories = categoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .ne(RepairCategory::getParentId, 0L)
                .eq(RepairCategory::getStatus, 1));
        // 命中的类型，未命中保持 null
        RepairCategory matched = null;
        // 模型给的 ID 必须在启用类型中才采用
        if (draft.getCategoryId() != null) {
            // 按 ID 精确匹配
            matched = categories.stream()
                    .filter(item -> item.getId().equals(draft.getCategoryId()))
                    .findFirst().orElse(null);
        }
        // ID 无效时用类型路径或用户陈述做兜底匹配
        if (matched == null) {
            // 优先用模型返回的类型路径，其次用用户陈述
            String categoryText = StringUtils.hasText(draft.getCategoryPath())
                    ? draft.getCategoryPath() : userText;
            // 文本兜底匹配
            matched = matchCategoryByText(categories, categoryText);
        }
        // 命中则回填类型与展示路径，未命中清空避免脏 ID
        if (matched != null) {
            // 回填可信的类型 ID
            draft.setCategoryId(matched.getId());
            // 回填「父 > 子」展示路径
            draft.setCategoryPath(categoryPath(matched));
        // 没有合适类型时清空，交由用户在草稿中选择
        } else {
            // 清空不可信的类型 ID
            draft.setCategoryId(null);
            // 清空类型路径
            draft.setCategoryPath(null);
        }

        // 标题：模型没给或给出空白时，用描述或用户陈述兜底
        String title = StringUtils.hasText(draft.getTitle()) ? draft.getTitle().trim() : null;
        // 标题仍为空时按长度上限截取
        if (!StringUtils.hasText(title)) {
            // 优先用描述摘要，其次用用户陈述摘要
            title = StringUtils.hasText(draft.getDescription())
                    ? draft.getDescription().trim() : userText;
        }
        // 标题不得超过工单标题上限
        draft.setTitle(abbreviate(title, ORDER_TITLE_MAX));

        // 描述：模型没给时退化为用户陈述，避免空描述
        String description = StringUtils.hasText(draft.getDescription())
                ? draft.getDescription().trim() : userText;
        // 描述不得超过建单入参 1000 字上限
        draft.setDescription(abbreviate(description, 1000));

        // 紧急程度缺失或越界时按「中」补齐
        if (draft.getUrgency() == null || draft.getUrgency() < 1 || draft.getUrgency() > 4) {
            // 默认「中」
            draft.setUrgency(2);
        }
        // 影响范围缺失或越界时按「本户」补齐
        if (draft.getAffectScope() == null || draft.getAffectScope() < 1 || draft.getAffectScope() > 3) {
            // 默认「本户」
            draft.setAffectScope(1);
        }

        // 未提供的时长和设备规格保持空值，禁止按默认值估算。
        draft.setFaultDuration(StringUtils.hasText(draft.getFaultDuration())
                ? abbreviate(draft.getFaultDuration().trim(), 50) : null);
        draft.setDeviceInfo(StringUtils.hasText(draft.getDeviceInfo())
                ? abbreviate(draft.getDeviceInfo().trim(), 100) : null);
        // 安全隐患只保留白名单标签
        draft.setSafetyFlag(normalizeSafetyFlag(draft.getSafetyFlag()));
        // 返回校验后的草稿
        return draft;
    }

    /**
     * 按文本匹配报修类型。
     *
     * <p>先用「类型名被文本包含」匹配，覆盖模型返回「水管维修 > 马桶」这类路径；
     * 再用「文本被类型名包含」兜底，覆盖模型只给出简短类型名的情况。</p>
     *
     * @param categories 启用中的二级类型
     * @param text       待匹配文本
     * @return 命中的类型，未命中返回 null
     */
    private RepairCategory matchCategoryByText(List<RepairCategory> categories, String text) {
        // 空文本无法匹配
        if (!StringUtils.hasText(text)) {
            // 直接返回未命中
            return null;
        }
        // 第一轮：类型名出现在文本中
        for (RepairCategory category : categories) {
            // 名称非空且被文本包含即命中
            if (StringUtils.hasText(category.getName()) && text.contains(category.getName())) {
                // 返回命中类型
                return category;
            }
        }
        // 第二轮：文本本身就是一个类型名
        for (RepairCategory category : categories) {
            // 类型名包含文本时命中
            if (StringUtils.hasText(category.getName()) && category.getName().contains(text.trim())) {
                // 返回命中类型
                return category;
            }
        }
        // 两轮都未命中
        return null;
    }

    /**
     * 归一化安全隐患标签：只保留文档定义的涉电、涉水、涉气、高空。
     *
     * <p>模型可能返回中文逗号、顿号或带空格的组合，这里统一拆分为英文逗号连接。</p>
     *
     * @param raw 模型返回的原始标签串
     * @return 归一化后的标签串，无有效标签时返回 null
     */
    private String normalizeSafetyFlag(String raw) {
        // 空值直接返回
        if (!StringUtils.hasText(raw)) {
            // 无隐患标志
            return null;
        }
        // 允许中英文逗号、顿号混用
        String normalized = Arrays.stream(raw.split("[,，、]"))
                // 去掉标签两侧空白
                .map(String::trim)
                // 只保留白名单内的标签
                .filter(SAFETY_FLAGS::contains)
                // 去重保持原有顺序
                .distinct()
                // 统一用英文逗号拼接
                .collect(Collectors.joining(","));
        // 没有有效标签时返回 null
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 规则槽位提取：把对话文本映射为工单字段。
     *
     * <p>提取项包括报修类型（匹配启用中的二级类型名称）、紧急程度、影响范围、安全隐患、
     * 故障持续时间；无法识别的槽位保持 null，由 missingSlots 汇总后触发追问。
     * 该方法是模型不可用时的兜底路径，因此描述与标题只能取对话原文。</p>
     *
     * @param text 仅包含用户陈述的故障信息
     * @return 仅填充了可识别字段的草稿对象
     */
    private AiFormDraft extractDraftByRules(String text) {
        // 新建草稿对象，仅填充可识别字段
        AiFormDraft draft = new AiFormDraft();
        // 描述原文直接作为故障现象
        draft.setDescription(text);
        // 标题取描述摘要
        draft.setTitle(abbreviate(text, ORDER_TITLE_MAX));
        // 匹配报修类型：遍历二级类型名称，命中即采用
        List<RepairCategory> children = categoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .ne(RepairCategory::getParentId, 0L)
                .eq(RepairCategory::getStatus, 1));
        // 逐个匹配启用中的二级类型名称
        for (RepairCategory category : children) {
            // 名称命中则采用该类型
            if (StringUtils.hasText(category.getName()) && text.contains(category.getName())) {
                // 命中类型名称则回填类型 ID
                draft.setCategoryId(category.getId());
                // 记录父 > 子的展示路径
                draft.setCategoryPath(categoryPath(category));
                break;
            }
        }
        // 紧急程度：安全隐患类关键词直接判最高
        if (containsAny(text, "紧急", "危险", "漏电", "触电", "煤气", "燃气味", "着火", "冒烟")) {
            // 安全隐患类判最高紧急度
            draft.setUrgency(4);
        // 催促类关键词判较高
        } else if (containsAny(text, "尽快", "着急", "马上", "很急")) {
            // 记为较高紧急度
            draft.setUrgency(3);
        }
        // 影响范围：整栋最广，本层/相邻次之
        if (text.contains("整栋") || text.contains("全楼") || text.contains("整幢")) {
            // 整栋/全楼判为最广范围
            draft.setAffectScope(3);
        // 相邻楼层判次之
        } else if (containsAny(text, "隔壁", "楼上", "楼下", "本层", "整层", "楼道")) {
            // 记为本层范围
            draft.setAffectScope(2);
        }
        // 安全隐患标志：按关键词归类后拼接
        List<String> safety = new ArrayList<>();
        // 涉电关键词归类
        if (containsAny(text, "漏电", "触电", "跳闸", "火花", "线路")) {
            // 标记涉电隐患
            safety.add("涉电");
        }
        // 涉水关键词归类
        if (containsAny(text, "漏水", "下水", "水管", "积水")) {
            // 标记涉水隐患
            safety.add("涉水");
        }
        // 涉气关键词归类
        if (containsAny(text, "煤气", "燃气", "天然气")) {
            // 标记涉气隐患
            safety.add("涉气");
        }
        // 高空关键词归类
        if (containsAny(text, "高空", "外墙", "窗外", "楼顶")) {
            // 标记高空隐患
            safety.add("高空");
        }
        // 有隐患才写入标志字段
        if (!safety.isEmpty()) {
            // 逗号拼接隐患标签
            draft.setSafetyFlag(String.join(",", safety));
        }
        // 故障持续时间：正则提取
        Matcher matcher = DURATION_PATTERN.matcher(text);
        // 命中持续时间则写入
        if (matcher.find()) {
            // 记录故障持续时间
            draft.setFaultDuration(matcher.group());
        }
        // 返回识别后的草稿
        return draft;
    }

    /**
     * 收集已填充的必填槽位名。
     *
     * @param draft AI 草稿
     * @return 已填槽位名称列表
     */
    private List<String> filledSlots(AiFormDraft draft) {
        // 结果槽位列表
        List<String> slots = new ArrayList<>();
        // 类型已识别
        if (draft.getCategoryId() != null) {
            // 记录已填类型
            slots.add("categoryId");
        }
        // 描述已填充
        if (StringUtils.hasText(draft.getDescription())) {
            // 记录已填描述
            slots.add("description");
        }
        // 紧急度已识别
        if (draft.getUrgency() != null) {
            // 记录已填紧急度
            slots.add("urgency");
        }
        // 影响范围已识别
        if (draft.getAffectScope() != null) {
            // 记录已填影响范围
            slots.add("affectScope");
        }
        // 返回已填槽位
        return slots;
    }

    /**
     * 计算缺失的必填槽位。
     *
     * @param draft AI 草稿
     * @return 必填但尚未识别的槽位名，用于追问与追问次数统计
     */
    private List<String> missingSlots(AiFormDraft draft) {
        // 必填槽位清单
        List<String> required = List.of("categoryId", "description", "urgency", "affectScope");
        // 复用已填槽位计算
        List<String> filled = filledSlots(draft);
        // 过滤出必填但未填的槽位
        return required.stream().filter(slot -> !filled.contains(slot)).toList();
    }

    /**
     * 按缺失槽位生成下一句追问。
     *
     * <p>按 Map 的插入顺序只追问第一个缺失项，避免一次抛出多个问题让用户难以回答；
     * 都未命中时给出通用补充提示。</p>
     *
     * @param missing 缺失槽位名列表
     * @return 追问话术
     */
    private String buildFollowUpQuestion(List<String> missing) {
        // 槽位名到追问话术的映射
        Map<String, String> questions = new LinkedHashMap<>();
        // 类型缺失的追问话术
        questions.put("categoryId", "请问具体是哪类设施需要维修？（如水管、空调、门窗等）");
        // 紧急度缺失的追问话术
        questions.put("urgency", "这个问题目前有多紧急？（低 / 中 / 高 / 紧急）");
        // 影响范围缺失的追问话术
        questions.put("affectScope", "影响范围是只有您家，还是本层或整栋？");
        // 描述缺失的追问话术
        questions.put("description", "可以再具体描述一下故障现象吗？");
        // 按插入顺序取第一个缺失项追问
        for (String slot : missing) {
            // 命中已知槽位则返回话术
            if (questions.containsKey(slot)) {
                // 返回对应追问
                return questions.get(slot);
            }
        }
        // 未命中给出通用补充提示
        return "请补充更多信息，以便我帮您生成工单。";
    }

    /**
     * 统计本会话已发出的追问次数。
     *
     * <p>只统计填单意图且 missing_slots 非空的 AI 消息，不限制普通故障排查的轮次。</p>
     *
     * @param sessionId 会话 ID
     * @return 已追问次数
     */
    private int followUpCount(Long sessionId) {
        // 统计带缺失槽位的 AI 消息数，作为追问次数
        Long count = messageMapper.selectCount(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getSender, SENDER_AI)
                .eq(AiChatMessage::getIntent, INTENT_FILL_FORM)
                .isNotNull(AiChatMessage::getMissingSlots)
                .ne(AiChatMessage::getMissingSlots, ""));
        // 为空按 0 处理
        return count == null ? 0 : count.intValue();
    }

    // ------------------------------------------------------------------ RAG 与提示词

    /**
     * RAG 向量检索。
     *
     * <p>向量库不可用（AI 未启用）或检索异常时返回空列表，由上层继续走生成流程，
     * 保证知识库故障不会中断对话。</p>
     *
     * @param query 检索文本，通常为聚合后的用户描述
     * @return 相似度最高的若干知识条目，可能为空
     */
    private List<Document> retrieve(String query) {
        // 延迟获取向量库，AI 未启用时为空
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        // 向量库不可用时不做检索
        if (vectorStore == null) {
            // 未启用则直接返回空列表
            return List.of();
        }
        // 检索异常时降级为空
        try {
            // 构建相似度检索请求
            SearchRequest request = SearchRequest.builder().query(query).topK(RAG_TOPK).build();
            // 执行检索
            List<Document> documents = vectorStore.similaritySearch(request);
            // 结果为空按空列表返回
            return documents == null ? List.of() : documents;
        // 检索失败降级，不阻断回答
        } catch (Exception e) {
            // 检索异常降级为空，不影响后续回答
            log.warn("知识库检索失败：query={}", query, e);
            // 返回空列表
            return List.of();
        }
    }

    /**
     * 组装系统提示词。
     *
     * <p>在静态模板基础上追加动态内容：启用的报修类型清单（约束模型不得编造类型）
     * 以及本轮 RAG 命中的知识条目（让回答有据可依）。</p>
     *
     * @param documents 本轮检索命中的知识文档，可为空
     * @return 完整系统提示词
     */
    private String buildSystemPrompt(List<Document> documents) {
        // 以静态提示词为基底
        StringBuilder builder = new StringBuilder(systemPrompt);
        // 注入报修类型清单，约束模型不得编造类型
        builder.append("\n\n【报修类型列表】\n").append(categoryNames());
        // 有命中才注入知识条目
        if (!documents.isEmpty()) {
            // 注入检索命中的知识条目
            builder.append("\n\n【知识库检索结果】\n");
            // 逐条拼接标题与正文
            documents.forEach(document -> builder
                    .append("【").append(document.getMetadata().getOrDefault("title", "知识条目")).append("】")
                    .append(document.getText()).append('\n'));
        }
        // 返回完整系统提示词
        return builder.toString();
    }

    /**
     * 组装用户提示词：最近对话上下文 + 本轮聚合描述。
     *
     * @param sessionId 会话 ID
     * @param userText  聚合后的用户描述
     * @return 完整用户提示词
     */
    private String buildUserPrompt(Long sessionId, String userText) {
        // 组装用户提示词
        StringBuilder builder = new StringBuilder();
        // 注入最近对话上下文
        builder.append("【最近对话】\n").append(historyText(sessionId));
        // 注入本轮用户描述
        builder.append("\n\n【用户当前描述】\n").append(userText);
        // 返回完整用户提示词
        return builder.toString();
    }

    /**
     * 取最近若干条消息并拼接为上下文文本。
     *
     * <p>先按时间倒序 LIMIT 再翻转，避免历史很长时全量加载；只保留最近 HISTORY_LIMIT 条，
     * 控制提示词长度与 token 成本。</p>
     *
     * @param sessionId 会话 ID
     * @return 「用户/助手：内容」形式的上下文，无历史时返回占位文本
     */
    private String historyText(Long sessionId) {
        // 倒序取最近 HISTORY_LIMIT 条后翻转为正序
        List<AiChatMessage> messages = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getCreateTime, AiChatMessage::getId)
                .last("LIMIT " + HISTORY_LIMIT));
        // 无历史时返回占位
        if (messages.isEmpty()) {
            // 返回无历史占位
            return "（无）";
        }
        // 复制后翻转为正序
        List<AiChatMessage> ordered = new ArrayList<>(messages);
        // 反转为时间正序
        java.util.Collections.reverse(ordered);
        // 拼接为「用户/助手：内容」文本
        return ordered.stream()
                .map(message -> (SENDER_USER.equals(message.getSender()) ? "用户：" : "助手：") + message.getContent())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 查询全部启用中的二级报修类型名称。
     *
     * @return 以顿号连接的名称串
     */
    private String categoryNames() {
        // 查询启用中的二级报修类型
        List<RepairCategory> children = categoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .ne(RepairCategory::getParentId, 0L)
                .eq(RepairCategory::getStatus, 1));
        // 顿号拼接非空类型名
        return children.stream()
                .map(RepairCategory::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("、"));
    }

    // ------------------------------------------------------------------ 数据辅助

    /**
     * 校验会话存在且归属当前业主。
     *
     * <p>AI 会话包含用户隐私，所有读写入口都必须先经过此方法，防止通过会话 ID 越权访问。</p>
     *
     * @param sessionId 会话 ID
     * @param userId    当前登录用户 ID
     * @return 已校验的会话实体
     * @throws BizException 会话不存在（NOT_FOUND）或归属不符（FORBIDDEN）时抛出
     */
    private AiChatSession requireOwnedSession(Long sessionId, Long userId) {
        // 按主键读取会话
        AiChatSession session = sessionMapper.selectById(sessionId);
        // 会话不存在则拒绝
        if (session == null) {
            // 抛出不存在业务码
            throw BizException.of(ResultCode.NOT_FOUND, "会话不存在");
        }
        // 归属不符则拒绝
        if (!session.getUserId().equals(userId)) {
            // 抛出无权访问业务码
            throw BizException.of(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        // 校验通过返回会话
        return session;
    }

    /**
     * 锁在事务外获取，调用方的短事务提交后才返回并释放；看门狗覆盖模型调用时间。
     */
    private <T> T withSessionLock(Long sessionId, Supplier<T> operation) {
        RLock lock = redissonClient.getLock(RedisKeys.AI_SESSION_LOCK + sessionId);
        try {
            if (!lock.tryLock(0, TimeUnit.SECONDS)) {
                throw BizException.of(ResultCode.CONFLICT, "本会话正在处理，请稍后重试");
            }
            try {
                return operation.get();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.CONFLICT, "本次操作已中断，请重试");
        }
    }

    private void requireOngoingSession(AiChatSession session) {
        if (!Objects.equals(session.getResolved(), RESOLVED_ONGOING)) {
            throw BizException.of(ResultCode.CONFLICT, "本次排查已结束，新问题请创建新会话");
        }
    }

    private AiChatMessage requireLatestAnswer(Long sessionId, Long messageId) {
        AiChatMessage latest = messageMapper.selectOne(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getCreateTime, AiChatMessage::getId)
                .last("LIMIT 1"));
        if (latest == null || !SENDER_AI.equals(latest.getSender()) || !Objects.equals(latest.getId(), messageId)) {
            throw BizException.of(ResultCode.CONFLICT, "请操作最新一轮回答");
        }
        return latest;
    }

    private void updateResolved(Long sessionId, int resolved) {
        AiChatSession update = new AiChatSession();
        update.setId(sessionId);
        update.setResolved(resolved);
        int changed = sessionMapper.update(update, Wrappers.<AiChatSession>lambdaUpdate()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getResolved, RESOLVED_ONGOING));
        if (changed != 1) {
            throw BizException.of(ResultCode.CONFLICT, "会话状态已更新，请刷新后重试");
        }
    }

    private boolean isDraftValid(AiChatSession session, AiChatMessage message) {
        return Objects.equals(session.getResolved(), RESOLVED_ONGOING)
                && message != null && message.getCreateTime() != null
                && StringUtils.hasText(message.getFormDraft())
                && LocalDateTime.now().isBefore(message.getCreateTime().plus(DRAFT_VALIDITY));
    }

    private AiChatMessage requireValidDraft(AiChatSession session) {
        AiChatMessage message = latestDraftMessage(session.getId());
        if (!isDraftValid(session, message)) {
            throw BizException.of(ResultCode.AI_DRAFT_EMPTY, "草稿不存在、已过期或本次排查已结束");
        }
        return message;
    }

    private AiDraftResponseVO toDraftVO(AiChatMessage message) {
        LocalDateTime expiresAt = message.getCreateTime().plus(DRAFT_VALIDITY);
        long remainingMillis = Duration.between(LocalDateTime.now(), expiresAt).toMillis();
        return AiDraftResponseVO.builder()
                .draftId(message.getId())
                .form(JSONUtil.toBean(message.getFormDraft(), AiFormDraft.class))
                .expiresAt(expiresAt)
                .expiresIn(Math.max(0, (remainingMillis + 999) / 1000))
                .build();
    }

    /**
     * 查询会话最新一条带草稿的 AI 消息。
     *
     * @param sessionId 会话 ID
     * @return 最新草稿消息；没有草稿时返回 null
     */
    private AiChatMessage latestDraftMessage(Long sessionId) {
        // 取最新一条带草稿的消息
        return messageMapper.selectOne(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getSender, SENDER_AI)
                .isNotNull(AiChatMessage::getFormDraft)
                .orderByDesc(AiChatMessage::getCreateTime, AiChatMessage::getId)
                .last("LIMIT 1"));
    }

    /**
     * 聚合会话内全部用户消息。
     *
     * <p>多轮补充的信息可能分散在不同消息中，用句号拼接后统一做关键词匹配，
     * 避免只根据最后一句话而漏掉前文提供的槽位。</p>
     *
     * @param sessionId 会话 ID
     * @return 拼接后的用户描述
     */
    private String aggregateUserText(Long sessionId) {
        // 按时间正序取全部用户消息
        List<AiChatMessage> users = messageMapper.selectList(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getSender, SENDER_USER)
                .orderByAsc(AiChatMessage::getCreateTime, AiChatMessage::getId));
        // 用句号连接多轮描述，便于关键词整体匹配
        return users.stream().map(AiChatMessage::getContent).filter(Objects::nonNull)
                .collect(Collectors.joining("。"));
    }

    /**
     * 落库一条聊天消息并同步会话消息数。
     *
     * <p>消息数通过重新 count 得到而非自增，保证并发/异常场景下计数与真实数据一致；
     * updateById 同时刷新会话的 update_time，会话列表排序依赖该字段。</p>
     *
     * @param sessionId    会话 ID
     * @param sender       USER / AI
     * @param content      消息正文
     * @param intent       本轮意图，用户消息为 null
     * @param citations    引用知识条目 ID（逗号分隔），可为 null
     * @param draftJson    草稿 JSON，可为 null
     * @param filledSlots  已填槽位（逗号分隔），可为 null
     * @param missingSlots 缺失槽位（逗号分隔），可为 null
     * @return 新消息主键
     */
    private Long saveMessage(Long sessionId, String sender, String content, String intent,
                             String citations, String draftJson, String filledSlots, String missingSlots) {
        // 组装消息实体
        AiChatMessage message = new AiChatMessage();
        // 归属会话
        message.setSessionId(sessionId);
        // 发送方 USER / AI
        message.setSender(sender);
        // 消息正文
        message.setContent(content);
        // 本轮意图，用户消息为 null
        message.setIntent(intent);
        // 引用知识条目 ID
        message.setCitations(citations);
        // 草稿 JSON
        message.setFormDraft(draftJson);
        // 已填槽位快照
        message.setFilledSlots(filledSlots);
        // 缺失槽位快照
        message.setMissingSlots(missingSlots);
        // 落库消息
        messageMapper.insert(message);
        // 重新统计会话消息数并回写（同时刷新 update_time）
        AiChatSession session = new AiChatSession();
        // 指定会话 ID
        session.setId(sessionId);
        // 重新统计消息数
        session.setMessageCount(countMessages(sessionId));
        // 回写消息数并刷新更新时间
        sessionMapper.updateById(session);
        // 返回新消息主键
        return message.getId();
    }

    /**
     * 统计会话消息总数。
     *
     * @param sessionId 会话 ID
     * @return 消息条数
     */
    private int countMessages(Long sessionId) {
        // 统计会话消息条数
        Long count = messageMapper.selectCount(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, sessionId));
        // 为空按 0 处理
        return count == null ? 0 : count.intValue();
    }

    /**
     * 合并草稿与用户确认值。
     *
     * <p>优先级：用户显式提交 &gt; AI 草稿 &gt; 默认值（紧急程度 2、影响范围 1）。
     * 房产、联系人、期望上门时间等只来自用户确认表单，不从对话中猜测。</p>
     *
     * @param draft     AI 生成的草稿
     * @param requestVO 用户确认时提交的字段
     * @return 可直接用于创建工单的请求对象
     */
    private CreateOrderRequestVO mergeDraft(AiFormDraft draft, AiDraftConfirmRequestVO requestVO) {
        // 新建创建请求对象
        CreateOrderRequestVO vo = new CreateOrderRequestVO();
        // 房产与联系人由用户确认
        vo.setPropertyId(requestVO.getPropertyId());
        // 联系电话来自用户确认
        vo.setContactPhone(requestVO.getContactPhone());
        // 期望上门时间来自用户确认
        vo.setExpectedVisitTime(requestVO.getExpectedVisitTime());
        // 图片来自用户确认
        vo.setImages(requestVO.getImages());
        // 报修类型：用户覆盖优先
        vo.setCategoryId(requestVO.getCategoryId() != null ? requestVO.getCategoryId() : draft.getCategoryId());
        // 标题：用户覆盖优先
        vo.setTitle(StringUtils.hasText(requestVO.getTitle()) ? requestVO.getTitle() : draft.getTitle());
        // 描述：用户覆盖优先
        vo.setDescription(StringUtils.hasText(requestVO.getDescription())
                ? requestVO.getDescription() : draft.getDescription());
        // 紧急程度：用户覆盖优先，否则用草稿值，再无则默认「中」
        Integer urgency = requestVO.getUrgency() != null ? requestVO.getUrgency() : draft.getUrgency();
        // 兜底默认「中」
        vo.setUrgency(urgency == null ? 2 : urgency);
        // 故障持续时间：用户覆盖优先
        vo.setFaultDuration(StringUtils.hasText(requestVO.getFaultDuration())
                ? requestVO.getFaultDuration() : draft.getFaultDuration());
        // 影响范围：用户覆盖优先，否则用草稿值，再无则默认「本户」
        Integer affectScope = requestVO.getAffectScope() != null ? requestVO.getAffectScope() : draft.getAffectScope();
        // 兜底默认「本户」
        vo.setAffectScope(affectScope == null ? 1 : affectScope);
        // 安全隐患标志：用户覆盖优先
        vo.setSafetyFlag(StringUtils.hasText(requestVO.getSafetyFlag())
                ? requestVO.getSafetyFlag() : draft.getSafetyFlag());
        // 设备信息：用户覆盖优先
        vo.setDeviceInfo(StringUtils.hasText(requestVO.getDeviceInfo())
                ? requestVO.getDeviceInfo() : draft.getDeviceInfo());
        // 返回组装好的创建请求
        return vo;
    }

    /**
     * 拼装二级类型的展示路径「父类型 &gt; 子类型」。
     *
     * @param child 二级分类
     * @return 展示路径；父分类缺失时退化为子分类名
     */
    private String categoryPath(RepairCategory child) {
        // 无父类型时直接返回子类型名
        if (child.getParentId() == null || child.getParentId() == 0L) {
            // 无父类型直接返回子名
            return child.getName();
        }
        // 查父类型名称
        RepairCategory parent = categoryMapper.selectById(child.getParentId());
        // 父类型查不到时退化为子类型名
        return parent == null ? child.getName() : parent.getName() + " > " + child.getName();
    }

    /**
     * 会话实体转列表出参。
     *
     * @param session 会话实体
     * @param order   该会话通过 AI 草稿生成的工单，没有则为 null
     * @return 会话列表项
     */
    private AiSessionResponseVO toSessionVO(AiChatSession session, WorkOrder order) {
        // 装配会话列表出参
        return AiSessionResponseVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .messageCount(session.getMessageCount())
                .resolved(session.getResolved())
                // 已生成工单时回填工单 ID 与工单号，前端据此展示「查看工单」
                .orderId(order == null ? null : order.getId())
                .orderNo(order == null ? null : order.getOrderNo())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    /**
     * 消息实体转出参。
     *
     * @param message 消息实体
     * @return 消息列表项
     */
    private AiMessageResponseVO toMessageVO(AiChatMessage message) {
        // 装配消息列表出参
        return AiMessageResponseVO.builder()
                .id(message.getId())
                .sender(message.getSender())
                .content(message.getContent())
                .intent(message.getIntent())
                .citations(message.getCitations())
                .createTime(message.getCreateTime())
                .build();
    }

    /**
     * 文本截断，超出部分直接丢弃。
     *
     * <p>用于会话标题与工单标题等有长度上限的字段，避免超长文本入库失败。</p>
     *
     * @param text 原文本，可为 null
     * @param max  最大长度
     * @return 截断后的文本，null 原样返回
     */
    private String abbreviate(String text, int max) {
        // 空文本原样返回
        if (text == null) {
            // 空文本直接返回
            return null;
        }
        // 未超长原样返回
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 判断文本是否包含任一关键词。
     *
     * @param text     待匹配文本
     * @param keywords 关键词列表
     * @return 命中任一关键词返回 true
     */
    private boolean containsAny(String text, String... keywords) {
        // 逐个关键词匹配
        for (String keyword : keywords) {
            // 命中即返回真
            if (text.contains(keyword)) {
                // 命中任一即返回
                return true;
            }
        }
        // 均未命中返回假
        return false;
    }

    /**
     * 槽位列表拼接为逗号分隔字符串。
     *
     * <p>数据库以字符串保存槽位快照，空列表存 null 而非空串，
     * 便于 followUpCount 用「非空」判断追问次数。</p>
     *
     * @param slots 槽位名列表
     * @return 逗号分隔字符串；空列表返回 null
     */
    private String joinSlots(List<String> slots) {
        // 空列表存 null，否则逗号拼接
        return slots.isEmpty() ? null : String.join(",", slots);
    }
}
