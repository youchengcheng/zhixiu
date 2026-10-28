package com.ycc.zhixiucloud.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 助手 Tool Calling 工具集：工单查询、在线师傅查询、知识库检索。
 * 工具方法统一返回可读文本，交给大模型组织语言，避免把实体裸暴露给模型。
 *
 * <p>在整体架构中的位置：本类是「大模型」与「业务数据库」之间的受控桥梁。
 * 方法上的 {@code @Tool} 由 Spring AI 的 ToolCallingManager 扫描，业务层在发起对话时
 * 将本 Bean 注册给 ChatClient 后，大模型可以根据用户问题、结合方法 description
 * 自主决定调用哪个工具，并把模型生成的参数传进来。</p>
 *
 * <p>Function Calling 执行链路：</p>
 * <ol>
 *   <li>大模型根据工具描述选择工具并输出结构化调用参数（如 orderId、query）；</li>
 *   <li>Spring AI 反射调用对应的 {@code @Tool} 方法；</li>
 *   <li>方法查库/检索后返回纯文本结果，作为工具消息回传给模型；</li>
 *   <li>模型基于工具结果生成最终自然语言回答。</li>
 * </ol>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>输出统一为人类可读文本，而非 JSON 实体，既减少 Token 也避免暴露内部字段；</li>
 *   <li>参数缺失、查无数据、依赖未装配、检索异常都返回提示文案而不抛异常，
 *       保证工具调用链路不中断，模型仍可继续组织回答；</li>
 *   <li>向量库通过 {@link ObjectProvider} 惰性获取，AI 未启用时工具类仍可正常注册。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepairAssistantTools {

    /** 在线且审核通过的师傅状态值 */
    private static final int WORK_STATUS_ONLINE = 1;

    /** 审核状态：通过 */
    private static final int AUDIT_PASSED = 1;

    /** 在线师傅返回上限 */
    private static final int WORKER_LIMIT = 10;

    /** 知识检索默认条数 */
    private static final int KNOWLEDGE_TOPK = 3;

    /** 工单表访问：按 ID 查询工单摘要 */
    private final WorkOrderMapper workOrderMapper;

    /** 维修师傅表访问：查询在线师傅、更新负载与接单计数 */
    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    /** 用户表访问：批量查询师傅姓名 */
    private final UserMapper userMapper;

    /** 向量库可能未装配（AI 未启用），用 ObjectProvider 惰性获取 */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /**
     * 工具：按工单 ID 查询工单摘要。
     *
     * <p>输入：模型从用户问题中提取的工单主键 orderId；输出：工单号、标题、当前状态、
     * 紧急程度组成的单行文本。主键为空或工单不存在时返回提示文案，不抛异常，避免中断对话。</p>
     *
     * @param orderId 工单主键
     * @return 供模型阅读的工单摘要文本
     */
    @Tool(description = "根据工单ID查询工单的状态、标题与报修类型，用于回答用户的进度咨询")
    public String getOrder(Long orderId) {
        // 入参为空时直接返回提示，避免无意义查询
        if (orderId == null) {
            // 缺少工单 ID，返回提示文案
            return "未提供工单 ID";
        }
        // 按主键查询工单
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 查不到时给出明确提示
        if (order == null) {
            // 工单不存在，返回提示文案
            return "未找到工单：" + orderId;
        }
        // 组装精简文本，避免把无关字段暴露给模型
        return "工单号：" + order.getOrderNo()
                + "；标题：" + order.getTitle()
                + "；当前状态：" + order.getStatus()
                + "；紧急程度：" + order.getUrgency();
    }

    /**
     * 工具：查询当前可指派的在线师傅。
     *
     * <p>输入：无参数；输出：每行一位师傅的姓名、技能标签与当前负载，最多
     * {@link #WORKER_LIMIT} 条，按负载升序排列，便于模型推荐负载较低的师傅。
     * 只统计审核通过且状态在线的师傅。</p>
     *
     * @return 供模型阅读的在线师傅列表文本
     */
    @Tool(description = "查询当前在线且审核通过的维修师傅，返回姓名与当前负载，用于判断能否指派")
    public String getOnlineWorkers() {
        // 只取审核通过且状态在线的师傅
        List<MaintenanceWorker> workers = maintenanceWorkerMapper.selectList(
                Wrappers.<MaintenanceWorker>lambdaQuery()
                        .eq(MaintenanceWorker::getAuditStatus, AUDIT_PASSED)
                        .eq(MaintenanceWorker::getWorkStatus, WORK_STATUS_ONLINE)
                        .orderByAsc(MaintenanceWorker::getCurrentLoad)
                        .last("LIMIT " + WORKER_LIMIT));
        // 无在线师傅时给出提示
        if (workers.isEmpty()) {
            // 返回无可用师傅提示
            return "当前没有在线的维修师傅";
        }
        // 批量取姓名，避免逐条查库
        Map<Long, String> names = loadNames(workers);
        // 拼成一行行文本
        return workers.stream()
                .map(worker -> "师傅：" + names.getOrDefault(worker.getUserId(), "未知")
                        + "，技能：" + Objects.requireNonNullElse(worker.getSkillTags(), "未填写")
                        + "，当前负载：" + worker.getCurrentLoad())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 工具：检索维修知识库（RAG）。
     *
     * <p>输入：模型根据用户问题改写/提取的检索语句 query；输出：最相关的前
     * {@link #KNOWLEDGE_TOPK} 条知识，按「【标题】正文」格式拼接。
     * 向量库未装配、无命中或检索异常时均返回提示文案，由模型降级回答。</p>
     *
     * @param query 检索语句
     * @return 供模型阅读的知识条目文本
     */
    @Tool(description = "根据用户问题检索维修知识库，返回相关条目的标题与内容摘要")
    public String searchKnowledge(String query) {
        // 向量库未装配（AI 未启用）时返回提示
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            // 向量库未装配，返回提示
            return "知识库检索不可用";
        }
        // 构建检索请求并执行，异常统一降级为提示文案
        try {
            // 构建相似度检索请求
            SearchRequest request = SearchRequest.builder().query(query).topK(KNOWLEDGE_TOPK).build();
            // 执行向量检索
            List<Document> documents = vectorStore.similaritySearch(request);
            // 无命中时给出提示
            if (documents.isEmpty()) {
                // 返回空结果提示
                return "知识库中没有相关条目";
            }
            // 拼接检索结果文本
            return documents.stream()
                    .map(document -> "【" + document.getMetadata().getOrDefault("title", "知识条目") + "】"
                            + document.getText())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            // 检索异常不抛出，交给模型降级回答
            log.warn("知识库检索失败：query={}", query, e);
            // 返回失败提示，模型可据此降级回答
            return "知识库检索失败";
        }
    }

    /**
     * 批量查询师傅对应的用户姓名，避免在循环中逐条查库（N+1 问题）。
     *
     * @param workers 师傅列表
     * @return userId -&gt; 姓名；真名为空时回退为登录名
     */
    private Map<Long, String> loadNames(List<MaintenanceWorker> workers) {
        // 收集去重后的用户 ID
        List<Long> userIds = workers.stream()
                .map(MaintenanceWorker::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 无用户 ID 时返回空映射
        if (userIds.isEmpty()) {
            // 直接返回空映射
            return Map.of();
        }
        // 批量查用户并按 ID 建立姓名映射（无真名回退用户名）
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> Objects.requireNonNullElse(user.getRealName(), user.getUsername())));
    }
}
