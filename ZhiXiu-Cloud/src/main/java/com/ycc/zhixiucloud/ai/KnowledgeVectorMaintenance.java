package com.ycc.zhixiucloud.ai;

import com.ycc.zhixiucloud.ai.KnowledgeVectorStores.Provider;
import com.ycc.zhixiucloud.entity.RepairKnowledge;
import org.springframework.ai.document.Document;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 离线对账入口。最小 Spring 上下文只装配模型和向量库。 */
public final class KnowledgeVectorMaintenance {

    private KnowledgeVectorMaintenance() {
    }

    public static void run(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Dependencies.class)
                .profiles("knowledge-vector-maintenance").web(WebApplicationType.NONE).run(args)) {
            Environment env = context.getEnvironment();
            String command = env.getRequiredProperty("knowledge-vector.command");
            Provider target = Provider.parse(env.getRequiredProperty("knowledge-vector.target"));
            if (!Set.of("preview", "backfill", "verify").contains(command)) {
                throw new IllegalArgumentException("knowledge-vector.command 须为 preview、backfill 或 verify");
            }
            KnowledgeVectorStores stores = context.getBean(KnowledgeVectorStores.class);
            JdbcTemplate mysql = mysql(env);
            if ("backfill".equals(command)) {
                reconcile(stores, target, mysql, "backfill");
            }
            boolean matches = reconcile(stores, target, mysql, "preview".equals(command) ? command : "verify");
            if (!"preview".equals(command) && !matches) {
                throw new IllegalStateException("向量库对账未通过，请先处理报告中的差异");
            }
        }
    }

    private static JdbcTemplate mysql(Environment environment) {
        String url = environment.getRequiredProperty("spring.datasource.url");
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("回填源必须为应用的 MySQL 业务库");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
        return new JdbcTemplate(dataSource);
    }

    private static boolean reconcile(KnowledgeVectorStores stores, Provider target, JdbcTemplate mysql, String mode) {
        List<RepairKnowledge> source = mysql.query("""
                SELECT id, title, content, category_id, vectorized, version FROM repair_knowledge
                WHERE status = 1 AND deleted = 0 ORDER BY id
                """, (rs, row) -> {
            RepairKnowledge knowledge = new RepairKnowledge();
            knowledge.setId(rs.getLong("id"));
            knowledge.setTitle(rs.getString("title"));
            knowledge.setContent(rs.getString("content"));
            knowledge.setCategoryId(rs.getObject("category_id", Long.class));
            knowledge.setVectorized(rs.getInt("vectorized"));
            knowledge.setVersion(rs.getInt("version"));
            return knowledge;
        });
        Map<String, StoredChunk> actual = inventory(stores, target);
        Set<Long> pending = source.stream().filter(knowledge -> knowledge.getVectorized() != 1)
                .map(RepairKnowledge::getId).collect(Collectors.toSet());
        Map<Long, List<Document>> changes = new LinkedHashMap<>();
        int expectedCount = 0;

        for (RepairKnowledge knowledge : source) {
            if (pending.contains(knowledge.getId())) {
                continue;
            }
            List<Document> chunks = KnowledgeChunks.split(knowledge, knowledge.getVersion());
            expectedCount += chunks.size();
            boolean changed = false;
            for (Document chunk : chunks) {
                StoredChunk existing = actual.remove(chunk.getId());
                if (existing == null || !existing.matches(chunk)) {
                    changed = true;
                }
            }
            if (changed) {
                changes.put(knowledge.getId(), chunks);
            }
        }
        List<String> leftovers = actual.values().stream()
                .filter(chunk -> chunk.knowledgeId == null || !pending.contains(chunk.knowledgeId))
                .map(StoredChunk::id).sorted().toList();

        System.out.printf("target=%s mode=%s knowledge=%d expectedChunks=%d changed=%s staleChunks=%s pending=%s%n",
                target, mode, source.size(), expectedCount, changes.keySet(),
                leftovers, pending.stream().sorted().toList());
        if ("backfill".equals(mode)) {
            changes.forEach((id, docs) -> stores.replace(target, id, docs));
            stores.deleteChunks(target, leftovers);
        }
        return changes.isEmpty() && leftovers.isEmpty() && pending.isEmpty();
    }

    private static Map<String, StoredChunk> inventory(KnowledgeVectorStores stores, Provider provider) {
        Map<String, StoredChunk> inventory = new HashMap<>();
        if (provider == Provider.PGVECTOR) {
            stores.postgresJdbc().query("""
                    SELECT id, content, metadata ->> 'knowledgeId' AS knowledge_id, metadata ->> 'title' AS title,
                        metadata ->> 'knowledgeVersion' AS version, metadata ->> 'chunkIndex' AS chunk_index,
                        metadata ->> 'chunkCount' AS chunk_count, metadata ->> 'categoryId' AS category_id,
                        vector_dims(embedding) AS dimensions
                    FROM repair_knowledge_vectors
                    """, row -> {
                String id = row.getString("id");
                inventory.put(id, new StoredChunk(id, row.getString("content"), longValue(row.getString("knowledge_id")),
                        row.getString("title"), intValue(row.getString("version")), intValue(row.getString("chunk_index")),
                        intValue(row.getString("chunk_count")), row.getString("category_id"), row.getInt("dimensions")));
            });
            return inventory;
        }

        JedisPooled redis = stores.redis();
        ScanParams scan = new ScanParams().match(KnowledgeVectorStores.REDIS_PREFIX + "*").count(100);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = redis.scan(cursor, scan);
            for (String key : page.getResult()) {
                Map<?, ?> row = redis.jsonGet(key, Map.class);
                if (row == null) {
                    continue;
                }
                String id = key.substring(KnowledgeVectorStores.REDIS_PREFIX.length());
                inventory.put(id, new StoredChunk(id, string(row, "content"), longValue(string(row, "knowledgeId")),
                        string(row, "title"), intValue(row.get("knowledgeVersion")), intValue(row.get("chunkIndex")),
                        intValue(row.get("chunkCount")), string(row, "categoryId"),
                        row.get("embedding") instanceof List<?> embedding ? embedding.size() : 0));
            }
            cursor = page.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        return inventory;
    }

    private static String string(Map<?, ?> row, String key) {
        return Objects.toString(row.get(key), null);
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record StoredChunk(String id, String content, Long knowledgeId, String title, Integer version,
                               Integer chunkIndex, Integer chunkCount, String categoryId, int dimensions) {

        boolean matches(Document expected) {
            Map<String, Object> metadata = expected.getMetadata();
            return dimensions == 1024
                    && Objects.equals(expected.getText(), content)
                    && Objects.equals(KnowledgeChunks.knowledgeId(expected), knowledgeId)
                    && Objects.equals(metadata.get("title"), title)
                    && Objects.equals(metadata.get("knowledgeVersion"), version)
                    && Objects.equals(metadata.get("chunkIndex"), chunkIndex)
                    && Objects.equals(metadata.get("chunkCount"), chunkCount)
                    && Objects.equals(metadata.get("categoryId"), categoryId);
        }
    }

    @Import({SpringAiConfig.class, AiHttpClientConfig.class})
    static class Dependencies {
    }
}
