package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.entity.RepairKnowledge;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.mapper.RepairKnowledgeMapper;
import com.ycc.zhixiucloud.mq.producer.AiTaskProducer;
import com.ycc.zhixiucloud.request.KnowledgeFileRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeQueryRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeSaveRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeTestSearchRequestVO;
import com.ycc.zhixiucloud.response.KnowledgeResponseVO;
import com.ycc.zhixiucloud.response.KnowledgeSearchResultResponseVO;
import com.ycc.zhixiucloud.service.RepairKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 维修知识库服务实现：管理端知识条目 CRUD、异步向量化与 Redis 向量库（RedisVectorStore）检索测试。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>写库与向量化解耦：新增/修改只负责 MySQL 落库并标记 {@code vectorized=0}，随后向
 *       RocketMQ 投递 VECTORIZE 任务，由消费端调用 {@link #vectorize(Long)} 写入 Redis 向量库；
 *       投递失败只记日志，不影响主业务，可人工再次触发。</li>
 *   <li>原文按段落或句末分片，Redis 分片 ID 清单记录所有尝试写入的文档，重新向量化及
 *       删除时完整清理，包含失败任务的部分写入和旧版单文档向量。</li>
 *   <li>向量库 Bean 可能不存在（AI 未启用），统一通过 {@link ObjectProvider} 判空降级：
 *       检索测试直接抛业务异常，后台向量化任务仅记录日志跳过，保证应用可正常启动。</li>
 *   <li>写操作使用 {@code @Transactional} 保证数据库一致性；向量库不参与本地事务，
 *       因此向量删除采用“失败不阻断”的补偿式处理。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairKnowledgeServiceImpl implements RepairKnowledgeService {

    /** 来源：人工录入 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 来源：文件导入 */
    private static final String SOURCE_IMPORT = "IMPORT";

    /** 文件上限 10MB，正文上限与 MySQL TEXT 容量一致 */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_CONTENT_BYTES = 65535;

    /** 支持的知识文件扩展名 */
    private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");

    /** 每片字符上限及相邻分片重叠字符数 */
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    /** 向量化标记：未向量化 */
    private static final int VECTORIZED_NO = 0;

    /** 向量化标记：已向量化 */
    private static final int VECTORIZED_YES = 1;

    // 知识库数据访问
    private final RepairKnowledgeMapper repairKnowledgeMapper;

    // 报修类型数据访问（取展示用类型名称）
    private final RepairCategoryMapper repairCategoryMapper;

    // AI 任务生产者（投递向量化任务）
    private final AiTaskProducer aiTaskProducer;

    // 向量库提供者（Bean 可能缺失，运行时判空）
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    // 保存全部分片 ID，便于重试、替换和删除时完整清理
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 分页查询知识条目。
     *
     * <p>支持按关联报修类型精确过滤；关键字非空时对标题与正文做 OR 模糊匹配，结果按更新时间
     * 倒序。类型名称不在知识表中，查询后通过一次批量查询回填，避免逐条查库。</p>
     *
     * @param requestVO 分页参数（页码、每页条数）与过滤条件（类型 ID、关键字）
     * @return 统一分页结果，记录为带类型名称的展示 VO
     */
    @Override
    public PageResult<KnowledgeResponseVO> page(KnowledgeQueryRequestVO requestVO) {
        // 按分页参数与查询条件分页
        Page<RepairKnowledge> page = repairKnowledgeMapper.selectPage(
                // 分页参数：页码与每页条数
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                // 构造知识库查询条件
                Wrappers.<RepairKnowledge>lambdaQuery()
                        // 关联类型精确匹配（非空才拼接）
                        .eq(requestVO.getCategoryId() != null, RepairKnowledge::getCategoryId,
                                requestVO.getCategoryId())
                        // 关键字非空时匹配标题或内容
                        .and(StringUtils.hasText(requestVO.getKeyword()), wrapper -> wrapper
                                .like(RepairKnowledge::getTitle, requestVO.getKeyword())
                                .or()
                                .like(RepairKnowledge::getContent, requestVO.getKeyword()))
                        // 按更新时间倒序
                        .orderByDesc(RepairKnowledge::getUpdateTime));

        // 批量查询类型名称，避免逐条查库
        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords());
        // 构造出参分页对象，沿用总记录数
        Page<KnowledgeResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条实体转换为出参 VO
        voPage.setRecords(page.getRecords().stream()
                .map(item -> toVO(item, categoryNames))
                .toList());
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 新增知识条目。
     *
     * <p>落库后在当前事务内投递异步向量化任务；消息只携带主键，消费端会重新查询最新数据。
     * 由于 {@code AiTaskProducer} 内部吞掉投递异常，MQ 不可用时条目仍会保存，但保持
     * {@code vectorized=0}，可调用 {@link #revectorize(Long)} 再次触发。</p>
     *
     * @param requestVO 标题、关联类型、正文与标签
     * @return 新知识条目主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeSaveRequestVO requestVO) {
        return saveKnowledge(requestVO, SOURCE_MANUAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromFile(KnowledgeFileRequestVO requestVO) {
        return saveKnowledge(toSaveRequest(requestVO, readFile(requestVO.getFile())), SOURCE_IMPORT);
    }

    /**
     * 修改知识条目。
     *
     * <p>内容变更后将 {@code vectorized} 重置为未向量化、版本号加一并投递异步重新向量化任务。
     * MyBatis-Plus 的 {@code updateById} 默认忽略 null 字段，因此入参为 null 的类型/标签
     * 不会被清空。</p>
     *
     * @param id        知识条目主键
     * @param requestVO 标题、关联类型、正文与标签
     * @throws BizException 知识条目不存在时抛出 404 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, KnowledgeSaveRequestVO requestVO) {
        // 先校验知识条目存在
        RepairKnowledge exists = requireKnowledge(id);
        updateKnowledge(exists, requestVO, exists.getSourceType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFromFile(Long id, KnowledgeFileRequestVO requestVO) {
        // 解析文件时不持有知识行锁，未上传文件则保留已有内容。
        String content = requestVO.getFile() == null ? null : readFile(requestVO.getFile());
        RepairKnowledge exists = requireKnowledge(id);
        updateKnowledge(exists, toSaveRequest(requestVO, content == null ? exists.getContent() : content),
                content == null ? exists.getSourceType() : SOURCE_IMPORT);
    }

    /**
     * 删除知识条目。
     *
     * <p>数据库执行逻辑删除，随后同步删除向量库中对应文档。向量删除失败只记日志，
     * 不会回滚已完成的逻辑删除，避免因外部向量库故障阻塞管理操作。</p>
     *
     * @param id 知识条目主键
     * @throws BizException 知识条目不存在时抛出 404 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 先校验知识条目存在
        requireKnowledge(id);
        // 按主键逻辑删除
        repairKnowledgeMapper.deleteById(id);
        // 同步清理该知识的全部分片及旧版单文档向量
        deleteVector(id);
        // 记录删除日志
        log.info("删除知识条目：id={}", id);
    }

    /**
     * 触发重新向量化。
     *
     * <p>仅校验条目存在并投递异步任务，实际向量写入由 MQ 消费端完成，管理端接口无需等待。</p>
     *
     * @param id 知识条目主键
     * @throws BizException 知识条目不存在时抛出 404 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revectorize(Long id) {
        // 先校验知识条目存在
        RepairKnowledge entity = requireKnowledge(id);
        RepairKnowledge update = new RepairKnowledge();
        update.setId(entity.getId());
        update.setVectorized(VECTORIZED_NO);
        repairKnowledgeMapper.updateById(update);
        // 记录触发日志
        log.info("触发重新向量化：id={}", id);
        // 投递异步向量化任务
        sendVectorizeAfterCommit(id);
    }

    /**
     * 向量相似度检索测试（管理端）。
     *
     * <p>直接调用 Redis 向量库的 {@code similaritySearch}，按相似度返回前 topK 条；当前未额外设置
     * 相似度阈值，命中结果中的 score 仅用于展示和人工判断。向量库 Bean 缺失说明 AI 未启用，
     * 此时抛出业务异常提示先配置 API Key。</p>
     *
     * @param requestVO 查询语句与返回条数 topK
     * @return 命中的知识文档列表（ID、标题、正文、相似度分数）；无命中返回空列表
     * @throws BizException 向量库不可用（AI 未启用）时抛出 AI 调用失败异常
     */
    @Override
    public List<KnowledgeSearchResultResponseVO> testSearch(KnowledgeTestSearchRequestVO requestVO) {
        // 取向量库 Bean，缺失即降级报错
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        // AI 未启用直接提示
        if (vectorStore == null) {
            // 向量库缺失说明 AI 未配置 API Key
            throw BizException.of(ResultCode.AI_INVOKE_FAILED, "AI 能力未启用，请先配置 AI API Key");
        }
        // 构造相似检索请求（查询语句 + 返回条数）
        // 说明：此处只按 topK 截断，未设置 similarityThreshold，低分结果也会返回，交由人工判断
        SearchRequest searchRequest = SearchRequest.builder()
                .query(requestVO.getQuery())
                .topK(requestVO.getTopK())
                .build();
        // 执行向量相似度检索
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        // 无命中返回空列表
        if (docs == null || docs.isEmpty()) {
            // 无检索结果直接返回空集合
            return List.of();
        }
        // 逐条转换为出参
        return docs.stream().map(this::toSearchVO).toList();
    }

    /**
     * 向量化单条知识（由 {@code AiTaskConsumer} 消费 VECTORIZE 消息时调用）。
     *
     * <p>流程：锁定知识条目 → 分片并登记全部文档 ID → 删除旧向量 → 写入 Redis 分片向量 →
     * 回写 {@code vectorized=1} 并递增版本号。条目不存在或
     * 向量库不可用时直接跳过且不抛异常，避免 MQ 无意义重试；重复执行时因先删后加仍保持幂等。</p>
     *
     * @param knowledgeId 知识条目主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vectorize(Long knowledgeId) {
        // 按主键查询知识条目（逻辑删除已由全局配置过滤）
        RepairKnowledge entity = repairKnowledgeMapper.selectOne(Wrappers.<RepairKnowledge>lambdaQuery()
                .eq(RepairKnowledge::getId, knowledgeId).last("FOR UPDATE"));
        // 条目不存在直接返回，不抛异常
        if (entity == null) {
            // 记录跳过原因，避免 MQ 无意义重试
            log.warn("向量化跳过：知识条目不存在 id={}", knowledgeId);
            return;
        }
        // 取向量库 Bean，缺失即降级
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        // AI 未启用只记日志，保持未向量化
        if (vectorStore == null) {
            // 记录告警后跳过，条目保持未向量化
            log.warn("向量库不可用，跳过向量化：id={}", knowledgeId);
            return;
        }
        List<Document> documents = splitDocuments(entity);
        String chunkKey = RedisKeys.of(RedisKeys.KNOWLEDGE_CHUNKS, knowledgeId);
        // 先登记再写向量，外部调用部分失败时，MQ 重试仍可清理全部已尝试写入的分片。
        stringRedisTemplate.opsForSet().add(chunkKey, documents.stream().map(Document::getId).toArray(String[]::new));
        deleteVectors(vectorStore, knowledgeId);
        vectorStore.add(documents);
        // 组装仅用于更新标记的实体
        RepairKnowledge update = new RepairKnowledge();
        // 指定主键
        update.setId(knowledgeId);
        // 标记已向量化
        update.setVectorized(VECTORIZED_YES);
        // 版本号自增
        update.setVersion(nextVersion(entity.getVersion()));
        // 持久化向量化标记
        repairKnowledgeMapper.updateById(update);
        // 记录向量化完成日志
        log.info("知识分片向量化完成：id={}, chunks={}", knowledgeId, documents.size());
    }

    /** 保存原文，事务提交后再投递向量化任务。 */
    private Long saveKnowledge(KnowledgeSaveRequestVO requestVO, String sourceType) {
        validateContent(requestVO.getContent());
        RepairKnowledge entity = new RepairKnowledge();
        entity.setTitle(requestVO.getTitle().trim());
        entity.setCategoryId(requestVO.getCategoryId());
        entity.setContent(requestVO.getContent());
        entity.setTags(requestVO.getTags());
        entity.setSourceType(sourceType);
        entity.setStatus(STATUS_ENABLED);
        entity.setVectorized(VECTORIZED_NO);
        entity.setVersion(0);
        repairKnowledgeMapper.insert(entity);
        log.info("新增知识条目：id={}, sourceType={}", entity.getId(), sourceType);
        sendVectorizeAfterCommit(entity.getId());
        return entity.getId();
    }

    /** 在当前事务中更新知识及来源。 */
    private void updateKnowledge(RepairKnowledge exists, KnowledgeSaveRequestVO requestVO, String sourceType) {
        validateContent(requestVO.getContent());
        RepairKnowledge entity = new RepairKnowledge();
        entity.setId(exists.getId());
        entity.setTitle(requestVO.getTitle().trim());
        // 保持原有可选字段策略，null 不覆盖已有类型和标签。
        entity.setCategoryId(requestVO.getCategoryId());
        entity.setContent(requestVO.getContent());
        entity.setTags(requestVO.getTags());
        entity.setSourceType(sourceType);
        entity.setVectorized(VECTORIZED_NO);
        entity.setVersion(nextVersion(exists.getVersion()));
        repairKnowledgeMapper.updateById(entity);
        log.info("修改知识条目：id={}", exists.getId());
        sendVectorizeAfterCommit(entity.getId());
    }

    /** 将文件表单转换为解析后的知识正文。 */
    private KnowledgeSaveRequestVO toSaveRequest(KnowledgeFileRequestVO requestVO, String content) {
        KnowledgeSaveRequestVO request = new KnowledgeSaveRequestVO();
        request.setTitle(requestVO.getTitle());
        request.setCategoryId(requestVO.getCategoryId());
        request.setTags(requestVO.getTags());
        request.setContent(content);
        return request;
    }

    /** 读取知识文件，只接收可提取文字的文档，超限不截断。 */
    private String readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_ERROR, "请上传非空的知识文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BizException.of(ResultCode.FILE_TOO_LARGE, "文件大小不能超过 10MB");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !FILE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw BizException.of(ResultCode.PARAM_ERROR, "仅支持 PDF、DOCX、TXT、MD/Markdown 文件");
        }
        validateFileStructure(file, extension.toLowerCase(Locale.ROOT));
        BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_BYTES + 1);
        String content;
        try {
            content = new TikaDocumentReader(file.getResource(), handler, ExtractedTextFormatter.defaults())
                    .get().stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        } catch (RuntimeException ex) {
            if (WriteLimitReachedException.isWriteLimitReached(ex)) {
                throw BizException.of(ResultCode.FILE_TOO_LARGE, "提取后的正文超过 64KB，请拆分文件后上传");
            }
            log.warn("知识文件解析失败：type={}, message={}", extension, ex.getMessage());
            throw BizException.of(ResultCode.PARAM_ERROR, "文件解析失败，请检查文件是否损坏或加密");
        }
        content = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1).strip();
        }
        validateContent(content);
        return content;
    }

    /** 防止将改过扩展名的普通文本或压缩包作为 PDF、DOCX 导入。 */
    private void validateFileStructure(MultipartFile file, String extension) {
        if (!"pdf".equals(extension) && !"docx".equals(extension)) {
            return;
        }
        try (InputStream input = file.getInputStream()) {
            if ("pdf".equals(extension)) {
                if (!"%PDF-".equals(new String(input.readNBytes(5), StandardCharsets.US_ASCII))) {
                    throw BizException.of(ResultCode.PARAM_ERROR, "PDF 文件格式无效，请检查文件是否损坏");
                }
                return;
            }
            boolean hasContentTypes = false;
            boolean hasDocument = false;
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                    hasDocument |= "word/document.xml".equals(entry.getName());
                    if (hasContentTypes && hasDocument) {
                        return;
                    }
                }
            }
            throw BizException.of(ResultCode.PARAM_ERROR, "DOCX 文件格式无效，请检查文件是否损坏");
        } catch (IOException ex) {
            throw BizException.of(ResultCode.PARAM_ERROR, "文件读取失败，请检查文件是否损坏");
        }
    }

    /** 正文长度按数据库实际使用的 UTF-8 字节数校验。 */
    private void validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw BizException.of(ResultCode.PARAM_ERROR, "文件中没有可提取的正文，请上传含文字的文档");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw BizException.of(ResultCode.FILE_TOO_LARGE, "提取后的正文超过 64KB，请拆分文件后上传");
        }
    }

    /** 优先按段落或句末切分，字符边界和重叠按 Unicode 码点计算。 */
    private List<Document> splitDocuments(RepairKnowledge entity) {
        String content = entity.getContent();
        List<String> texts = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int remaining = content.codePointCount(start, content.length());
            int end = content.offsetByCodePoints(start, Math.min(CHUNK_SIZE, remaining));
            if (end < content.length()) {
                int minimumEnd = content.offsetByCodePoints(start, CHUNK_SIZE / 2);
                for (int index = end - 1; index >= minimumEnd; index--) {
                    if ("\n。！？；.!?;".indexOf(content.charAt(index)) >= 0) {
                        end = index + 1;
                        break;
                    }
                }
            }
            String text = content.substring(start, end).strip();
            if (StringUtils.hasText(text)) {
                texts.add(text);
            }
            if (end == content.length()) {
                break;
            }
            start = content.offsetByCodePoints(end, -CHUNK_OVERLAP);
        }
        List<Document> documents = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            Document.Builder builder = Document.builder()
                    .id(entity.getId() + ":chunk:" + index)
                    .text(texts.get(index))
                    .metadata("knowledgeId", String.valueOf(entity.getId()))
                    .metadata("title", entity.getTitle())
                    .metadata("chunkIndex", index + 1)
                    .metadata("chunkCount", texts.size());
            if (entity.getCategoryId() != null) {
                builder.metadata("categoryId", String.valueOf(entity.getCategoryId()));
            }
            documents.add(builder.build());
        }
        return documents;
    }

    /** 避免消费者在知识事务提交前读取到不存在或过期的原文。 */
    private void sendVectorizeAfterCommit(Long knowledgeId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiTaskProducer.sendVectorize(knowledgeId);
            }
        });
    }

    /** 按完整清单删除全部分片，不依赖向量检索的默认条数限制。 */
    private void deleteVectors(VectorStore vectorStore, Long knowledgeId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(String.valueOf(knowledgeId));
        Set<String> chunks = stringRedisTemplate.opsForSet().members(RedisKeys.of(RedisKeys.KNOWLEDGE_CHUNKS, knowledgeId));
        if (chunks != null) {
            ids.addAll(chunks);
        }
        vectorStore.delete(new ArrayList<>(ids));
    }

    /**
     * 批量取类型名称（展示用关联字段，非表字段）。
     *
     * <p>先收集当前页记录中去重后的非空类型 ID，再用一次 {@code selectBatchIds} 查询，
     * 避免按记录逐条查库造成 N+1；无类型 ID 时返回空映射。</p>
     *
     * @param list 当前页知识条目
     * @return 类型 ID 到类型名称的映射
     */
    private Map<Long, String> loadCategoryNames(List<RepairKnowledge> list) {
        // 收集去重后的非空类型 ID
        Set<Long> ids = list.stream()
                .map(RepairKnowledge::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 无类型时直接返回空映射
        if (ids.isEmpty()) {
            // 无类型 ID 直接返回空映射，避免非法查询
            return Collections.emptyMap();
        }
        // 按数量预分配容量的名称映射
        Map<Long, String> names = new HashMap<>(ids.size());
        // 批量查类型，填充 ID 到名称的映射
        for (RepairCategory category : repairCategoryMapper.selectBatchIds(ids)) {
            // 记录类型 ID 到名称映射
            names.put(category.getId(), category.getName());
        }
        // 返回映射供装配出参
        return names;
    }

    /**
     * 删除知识的全部分片向量，向量库不可用时降级跳过。
     *
     * <p>向量删除不属于数据库事务，且失败不应影响知识条目本身的删除，因此这里捕获异常仅告警。</p>
     *
     * @param knowledgeId 知识 ID
     */
    private void deleteVector(Long knowledgeId) {
        // 取向量库 Bean，缺失即降级
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        // 向量库不可用只记日志，不阻断删除
        if (vectorStore == null) {
            // 记录告警后返回，不阻断知识条目删除
            log.warn("向量库不可用，跳过删除向量：knowledgeId={}", knowledgeId);
            return;
        }
        // 删除向量，失败不影响知识条目删除
        try {
            deleteVectors(vectorStore, knowledgeId);
            stringRedisTemplate.delete(RedisKeys.of(RedisKeys.KNOWLEDGE_CHUNKS, knowledgeId));
        // 捕获异常仅告警，不阻断主流程
        } catch (Exception e) {
            // 向量删除失败不阻断知识条目删除
            log.warn("删除分片向量失败：knowledgeId={}", knowledgeId, e);
        }
    }

    /**
     * 校验知识条目存在。
     *
     * @param id 知识条目主键
     * @return 查到的知识实体
     * @throws BizException 条目不存在（或被逻辑删除）时抛出 404 业务异常
     */
    private RepairKnowledge requireKnowledge(Long id) {
        // 按主键查询知识条目
        RepairKnowledge entity = repairKnowledgeMapper.selectOne(Wrappers.<RepairKnowledge>lambdaQuery()
                .eq(RepairKnowledge::getId, id).last("FOR UPDATE"));
        // 不存在则报错
        if (entity == null) {
            // 条目不存在（或被逻辑删除）统一抛 404
            throw BizException.of(ResultCode.NOT_FOUND, "知识条目不存在");
        }
        // 返回查到的实体
        return entity;
    }

    /**
     * 版本号自增：空值按 0 处理。
     *
     * @param version 当前版本号，可为 null
     * @return 新版本号
     */
    private int nextVersion(Integer version) {
        // 旧版本号为空按 0 计算再加一
        return (version == null ? 0 : version) + 1;
    }

    /**
     * 检索命中文档转换为出参。
     *
     * @param doc 向量库返回的分片文档，元数据保存原知识 ID
     * @return 检索结果 VO；元数据缺失时对应字段为 null
     */
    private KnowledgeSearchResultResponseVO toSearchVO(Document doc) {
        // 通过 Builder 逐字段装配出参
        return KnowledgeSearchResultResponseVO.builder()
                // 新版分片使用原知识 ID，兼容旧版单文档向量
                .id(Long.valueOf(doc.getMetadata().getOrDefault("knowledgeId", doc.getId()).toString()))
                // 标题取自文档元数据
                .title(stringMeta(doc, "title"))
                // 内容取文档正文
                .content(doc.getText())
                // 相似度分数可能为空
                .score(doc.getScore())
                // 构建出参对象
                .build();
    }

    /**
     * 读取元数据中的字符串值。
     *
     * @param doc 向量文档
     * @param key 元数据键
     * @return 字符串值，元数据或键不存在时返回 null
     */
    private String stringMeta(Document doc, String key) {
        // 取出元数据原始值
        Object value = doc.getMetadata() == null ? null : doc.getMetadata().get(key);
        // 空值返回 null，否则转字符串
        return value == null ? null : value.toString();
    }

    /**
     * 知识实体转出参 VO。
     *
     * @param entity        知识实体
     * @param categoryNames 批量查询得到的类型 ID→名称映射
     * @return 带类型名称的知识展示 VO
     */
    private KnowledgeResponseVO toVO(RepairKnowledge entity, Map<Long, String> categoryNames) {
        // 通过 Builder 逐字段装配出参
        return KnowledgeResponseVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .categoryId(entity.getCategoryId())
                // 类型名称取自批量查询映射
                .categoryName(categoryNames.get(entity.getCategoryId()))
                .content(entity.getContent())
                .tags(entity.getTags())
                .sourceType(entity.getSourceType())
                .status(entity.getStatus())
                .vectorized(entity.getVectorized())
                .version(entity.getVersion())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                // 构建出参对象
                .build();
    }
}
