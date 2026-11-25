package com.ycc.zhixiucloud.ai;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/** 模型 HTTP 连接在空闲后及时回收，降低复用已被网关关闭的连接的概率。 */
@Configuration
@ConditionalOnExpression("${repair.ai.enabled:true} and '${repair.ai-model.api-key:}'.length() > 0")
public class AiHttpClientConfig {

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider aiConnectionProvider() {
        return ConnectionProvider.builder("ai-upstream")
                .maxConnections(32)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(10))
                .lifo()
                .build();
    }

    @Bean
    public HttpClient aiHttpClient(ConnectionProvider aiConnectionProvider) {
        return HttpClient.create(aiConnectionProvider)
                .protocol(HttpProtocol.HTTP11)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(60));
    }
}
