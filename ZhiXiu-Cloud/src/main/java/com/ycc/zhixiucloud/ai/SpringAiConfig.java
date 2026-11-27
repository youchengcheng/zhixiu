package com.ycc.zhixiucloud.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring AI 装配：ChatClient、EmbeddingModel 和维修知识向量库。
 *
 * <p>开发文档要求「ChatClient + RedisVectorStore + ChatMemory」三件套，这里按本机实际情况落地：
 * 维修知识向量库按配置装配；会话记忆改用 ai_chat_message 表自行维护（Redis 版
 * ChatMemory 仓库在本仓库无对应构件），效果等价且可持久化。</p>
 *
 * <p>在整体架构中的位置：本类是 AI 能力的装配层，只负责创建底层客户端与模型 Bean；
 * 业务编排（意图识别、RAG、槽位提取、流式推送、草稿生成）在 AiAssistantServiceImpl 中完成。
 * 通过阿里云百炼的 OpenAI 兼容模式接入，对话与向量化共用同一个 {@link OpenAiApi} 客户端。</p>
 *
 * <p>条件装配与降级策略：仅当 repair.ai.enabled 为 true 且 repair.ai-model.api-key 非空时才创建 Bean。
 * 未配置 Key 时整个 AI 链路自动降级（AiAssistantService 通过 ObjectProvider 取不到 Bean），
 * 应用照常启动，其余模块不受影响；知识库相关工具在向量库缺失时也会返回提示文案而非报错。</p>
 *
 * <p>配置项均带默认值：配置缺失时不阻断启动，便于本地开发直接运行非 AI 功能。</p>
 */
@Slf4j
@Configuration
@ConditionalOnExpression("${repair.ai.enabled:true} and '${repair.ai-model.api-key:}'.length() > 0")
public class SpringAiConfig {

    /** 大模型 API Key */
    @Value("${repair.ai-model.api-key:}")
    private String apiKey;

    /** OpenAI 兼容接口地址（默认阿里云百炼兼容模式） */
    @Value("${repair.ai-model.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String baseUrl;

    /** 对话模型名 */
    @Value("${repair.ai-model.chat-model:qwen-plus}")
    private String chatModelName;

    /** 向量化模型名 */
    @Value("${repair.ai-model.embedding-model:text-embedding-v4}")
    private String embeddingModelName;

    /** 向量维度，须与向量化模型一致 */
    @Value("${repair.ai-model.vector-dimensions:1024}")
    private int vectorDimensions;

    /**
     * 构建 OpenAI 兼容 API 客户端，对话与向量化共用。
     *
     * <p>只依赖 baseUrl 与 apiKey，因此可对接阿里云百炼、OpenAI 或任意兼容
     * Chat Completions / Embeddings 协议的服务；切换供应商只需改配置。</p>
     *
     * @param aiHttpClient 具有空闲连接回收策略的模型 HTTP 客户端
     * @return OpenAI 兼容客户端
     */
    @Bean
    public OpenAiApi openAiApi(HttpClient aiHttpClient) {
        ReactorClientHttpRequestFactory requestFactory = new ReactorClientHttpRequestFactory(aiHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        // 按兼容模式地址与 API Key 构建客户端
        return OpenAiApi.builder()
                // 指定 OpenAI 兼容接口地址
                .baseUrl(baseUrl)
                // 设置大模型 API Key
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .webClientBuilder(WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(aiHttpClient)))
                .build();
    }

    /** 非流式模型调用使用有界重试，避免长退避占满 SSE 的等待窗口。 */
    @Bean
    public RetryTemplate aiModelRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .fixedBackoff(500)
                .retryOn(TransientAiException.class)
                .retryOn(ResourceAccessException.class)
                .build();
    }

    /**
     * 对话模型：供 ChatClient 流式/非流式对话使用。
     *
     * <p>手工装配而非依赖自动配置，是为了精确控制模型名、温度、重试策略，
     * 并显式注册 {@link ToolCallingManager} 以启用 {@code @Tool} 注解的方法调用能力。</p>
     *
     * @param openAiApi 共用的 OpenAI 兼容客户端
     * @return 对话模型
     */
    @Bean
    public ChatModel chatModel(OpenAiApi openAiApi, RetryTemplate aiModelRetryTemplate) {
        // 默认对话选项：指定模型名与温度
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                // 指定对话模型名
                .model(chatModelName)
                // 温度偏低，减少幻觉
                .temperature(0.3)
                .build();
        // 手工装配 ChatModel，注册有界重试与工具调用管理器（@Tool 支持）
        return OpenAiChatModel.builder()
                // 复用上方的兼容客户端
                .openAiApi(openAiApi)
                // 应用默认对话选项
                .defaultOptions(options)
                // 注册工具调用管理器，启用 @Tool 方法调用
                .toolCallingManager(ToolCallingManager.builder().build())
                // 非流式调用最多重试一次，避免长退避占满 SSE 超时窗口
                .retryTemplate(aiModelRetryTemplate)
                .build();
    }

    /**
     * 向量化模型：知识库入库与相似度检索共用。
     *
     * <p>维度必须与配置项 {@code repair.ai-model.vector-dimensions} 及向量索引 schema 保持一致，
     * 否则写入或检索会因维度不匹配失败。</p>
     *
     * @param openAiApi 共用的 OpenAI 兼容客户端
     * @return 向量化模型
     */
    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, RetryTemplate aiModelRetryTemplate) {
        // 向量化选项：指定模型名与维度（维度须与索引一致）
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                // 指定向量化模型名
                .model(embeddingModelName)
                // 指定向量维度，须与索引一致
                .dimensions(vectorDimensions)
                .build();
        // 手工装配向量化模型
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, aiModelRetryTemplate);
    }

    /**
     * 基础 ChatClient：仅绑定对话模型，不预置系统提示词。
     * 业务层每次对话时按会话动态设置 system prompt、用户消息与工具，再发起流式调用。
     *
     * @param chatModel 对话模型
     * @return ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        // 基于对话模型构建 ChatClient
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public KnowledgeVectorStores knowledgeVectorStores(Environment environment, EmbeddingModel embeddingModel) {
        return new KnowledgeVectorStores(environment, embeddingModel);
    }

    @Bean
    @Profile("!knowledge-vector-maintenance")
    public SmartInitializingSingleton initializeKnowledgeVectorStore(KnowledgeVectorStores stores) {
        return stores::activeStore;
    }
}
