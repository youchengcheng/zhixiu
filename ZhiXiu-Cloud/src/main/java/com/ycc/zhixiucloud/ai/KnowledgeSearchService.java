package com.ycc.zhixiucloud.ai;

import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.entity.RepairKnowledge;
import com.ycc.zhixiucloud.mapper.RepairKnowledgeMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 每个知识检索入口共用有效性检查和分数刻度。 */
@Service
public class KnowledgeSearchService {

    private static final int QUERY_MAX_CHARS = 1000;
    private static final int MAX_CANDIDATES = 100;

    private final ObjectProvider<KnowledgeVectorStores> storesProvider;
    private final RepairKnowledgeMapper knowledgeMapper;
    private final double threshold;

    public KnowledgeSearchService(ObjectProvider<KnowledgeVectorStores> storesProvider,
                                  RepairKnowledgeMapper knowledgeMapper,
                                  @Value("${repair.ai.knowledge-vector.similarity-threshold:0.75}") double threshold) {
        if (!(threshold >= 0.5 && threshold <= 1)) {
            throw new IllegalArgumentException("知识检索相关性阈值须在 0.5 至 1 之间");
        }
        this.storesProvider = storesProvider;
        this.knowledgeMapper = knowledgeMapper;
        this.threshold = threshold;
    }

    public List<Document> search(String query, int topK) {
        KnowledgeVectorStores stores = storesProvider.getIfAvailable();
        if (stores == null) {
            throw BizException.of(ResultCode.AI_INVOKE_FAILED, "AI 能力未启用，请先配置 AI API Key");
        }
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        boolean pgvector = stores.provider() == KnowledgeVectorStores.Provider.PGVECTOR;
        List<Document> candidates = stores.activeStore().similaritySearch(SearchRequest.builder()
                .query(query.length() > QUERY_MAX_CHARS ? query.substring(0, QUERY_MAX_CHARS) : query)
                .topK(Math.min(MAX_CANDIDATES, topK * 4))
                .similarityThreshold(pgvector ? 2 * threshold - 1 : threshold)
                .build());
        List<Long> ids = candidates.stream().map(KnowledgeChunks::knowledgeId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, RepairKnowledge> knowledge = knowledgeMapper.selectBatchIds(ids).stream()
                .filter(row -> Integer.valueOf(1).equals(row.getStatus()) && Integer.valueOf(1).equals(row.getVectorized()))
                .collect(Collectors.toMap(RepairKnowledge::getId, Function.identity()));

        return candidates.stream()
                .filter(chunk -> knowledge.containsKey(KnowledgeChunks.knowledgeId(chunk)))
                .filter(chunk -> !pgvector || KnowledgeChunks.hasCurrentVersion(
                        chunk, knowledge.get(KnowledgeChunks.knowledgeId(chunk))))
                .filter(chunk -> chunk.getScore() != null
                        && normalizedScore(chunk.getScore(), pgvector) + 1e-12 >= threshold)
                .limit(topK)
                .map(chunk -> withCurrentKnowledge(chunk, knowledge.get(KnowledgeChunks.knowledgeId(chunk)), pgvector))
                .toList();
    }

    private double normalizedScore(double score, boolean pgvector) {
        return pgvector ? (score + 1) / 2 : score;
    }

    private Document withCurrentKnowledge(Document chunk, RepairKnowledge knowledge, boolean pgvector) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("knowledgeId", knowledge.getId().toString());
        metadata.put("title", knowledge.getTitle());
        return Document.builder().id(chunk.getId()).text(chunk.getText()).metadata(metadata)
                .score(normalizedScore(chunk.getScore(), pgvector)).build();
    }
}
