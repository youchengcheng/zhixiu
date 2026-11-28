package com.ycc.zhixiucloud.ai;

import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 向量库连接及按篇写入。PostgreSQL 连接池仅供此组件使用。 */
public final class KnowledgeVectorStores implements AutoCloseable {

    public static final String REDIS_PREFIX = "repair-knowledge:";
    private static final String POSTGRES_SETTINGS = "repair.ai.knowledge-vector.postgres.";
    private static final String POSTGRES_TABLE = "repair_knowledge_vectors";
    private static final String DELETE_POSTGRES = """
            DELETE FROM repair_knowledge_vectors WHERE metadata ->> 'knowledgeId' = ?
            """;

    public enum Provider {
        REDIS, PGVECTOR;

        public static Provider parse(String name) {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final Environment environment;
    private final EmbeddingModel embeddingModel;
    private final Provider provider;
    private JedisPooled redis;
    private RedisVectorStore redisStore;
    private HikariDataSource postgres;
    private JdbcTemplate postgresJdbc;
    private TransactionTemplate postgresTransaction;
    private PgVectorStore postgresStore;

    public KnowledgeVectorStores(Environment environment, EmbeddingModel embeddingModel) {
        this.environment = environment;
        this.embeddingModel = embeddingModel;
        this.provider = Provider.parse(environment.getProperty("repair.ai.knowledge-vector.provider", "redis"));
    }

    public Provider provider() {
        return provider;
    }

    public VectorStore activeStore() {
        return provider == Provider.REDIS ? redisStore() : postgresStore();
    }

    public synchronized JedisPooled redis() {
        if (redis == null) {
            DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                    .database(environment.getProperty("spring.data.redis.database", Integer.class, 0));
            String password = environment.getProperty("spring.data.redis.password", "");
            if (StringUtils.hasText(password)) {
                config.password(password);
            }
            redis = new JedisPooled(new HostAndPort(environment.getProperty("spring.data.redis.host", "localhost"),
                    environment.getProperty("spring.data.redis.port", Integer.class, 6379)), config.build());
        }
        return redis;
    }

    public synchronized JdbcTemplate postgresJdbc() {
        if (postgresJdbc == null) {
            String url = environment.getProperty(POSTGRES_SETTINGS + "url", "");
            String username = environment.getProperty(POSTGRES_SETTINGS + "username", "");
            if (!url.startsWith("jdbc:postgresql:") || !StringUtils.hasText(username)
                    || environment.getProperty("repair.ai-model.vector-dimensions", Integer.class, 1024) != 1024) {
                throw new IllegalStateException("pgvector 需要 PostgreSQL JDBC URL、用户名及 1024 维向量配置");
            }
            HikariConfig config = new HikariConfig();
            config.setPoolName("knowledge-pgvector");
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(environment.getProperty(POSTGRES_SETTINGS + "password", ""));
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(1);
            postgres = new HikariDataSource(config);
            postgresJdbc = new JdbcTemplate(postgres);
            postgresTransaction = new TransactionTemplate(new DataSourceTransactionManager(postgres));
        }
        return postgresJdbc;
    }

    private synchronized RedisVectorStore redisStore() {
        if (redisStore == null) {
            RedisVectorStore store = RedisVectorStore.builder(redis(), embeddingModel)
                    .indexName("repair-knowledge")
                    .prefix(REDIS_PREFIX)
                    .metadataFields(RedisVectorStore.MetadataField.tag("knowledgeId"),
                            RedisVectorStore.MetadataField.text("title"))
                    .initializeSchema(true)
                    .build();
            store.afterPropertiesSet();
            redisStore = store;
        }
        return redisStore;
    }

    private synchronized PgVectorStore postgresStore() {
        if (postgresStore == null) {
            JdbcTemplate jdbc = postgresJdbc();
            jdbc.queryForList("SELECT id, content, metadata, embedding FROM " + POSTGRES_TABLE + " LIMIT 0");
            PgVectorStore store = PgVectorStore.builder(jdbc, embeddingModel)
                    .vectorTableName(POSTGRES_TABLE)
                    .dimensions(1024)
                    .idType(PgVectorStore.PgIdType.TEXT)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .indexType(PgVectorStore.PgIndexType.NONE)
                    .initializeSchema(false)
                    .build();
            store.afterPropertiesSet();
            postgresStore = store;
        }
        return postgresStore;
    }

    public void replace(Provider target, long knowledgeId, List<Document> chunks) {
        if (target == Provider.PGVECTOR) {
            PgVectorStore store = postgresStore();
            postgresTransaction.executeWithoutResult(status -> {
                postgresJdbc.update(DELETE_POSTGRES, Long.toString(knowledgeId));
                if (!chunks.isEmpty()) {
                    store.add(chunks);
                }
            });
            return;
        }

        RedisVectorStore store = redisStore();
        String manifest = RedisKeys.of(RedisKeys.KNOWLEDGE_CHUNKS, knowledgeId);
        String[] newIds = chunks.stream().map(Document::getId).toArray(String[]::new);
        if (newIds.length > 0) {
            // 先记住所有尝试写入的 ID，任务失败重试仍能清理。
            redis().sadd(manifest, newIds);
        }
        Set<String> tracked = new LinkedHashSet<>(redis().smembers(manifest));
        tracked.add(Long.toString(knowledgeId));
        store.delete(List.copyOf(tracked));
        if (newIds.length > 0) {
            store.add(chunks);
            tracked.removeAll(List.of(newIds));
        }
        if (!tracked.isEmpty()) {
            redis().srem(manifest, tracked.toArray(String[]::new));
        }
    }

    /** 清理对账发现的无归属分片，及未被旧 Redis 清单覆盖的残留。 */
    public void deleteChunks(Provider target, List<String> ids) {
        if (!ids.isEmpty()) {
            (target == Provider.REDIS ? redisStore() : postgresStore()).delete(ids);
        }
    }

    @Override
    public synchronized void close() {
        if (redis != null) {
            redis.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }
}
