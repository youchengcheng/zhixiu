package com.ycc.zhixiucloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE（Server-Sent Events）流式响应线程池配置。
 *
 * <p>AI 助手通过 SseEmitter 向浏览器逐字推送模型输出（AiAssistantServiceImpl 注入
 * 名为 sseExecutor 的 Bean 执行流式任务）。一次流式会话可能持续数十秒，
 * 若占用 Tomcat 请求线程会迅速耗尽 Web 容器线程，因此单独建池把推送移出请求线程；
 * 与 AsyncConfig 的通用线程池隔离，避免 AI 长任务挤占通知、日志等后台任务。</p>
 *
 * <p>异步响应超时由 application.yml 的 spring.mvc.async.request-timeout（60000 毫秒）控制，
 * 需大于 AI 首 Token 返回时间，否则连接会被容器提前关闭。</p>
 */
@Configuration
public class SseConfig {

    /** 流式响应专用线程池，Bean 名 sseExecutor */
    @Bean("sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor() {
        // 创建流式响应专用线程池
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：常规并发流式会话
        executor.setCorePoolSize(4);
        // 最大线程数：高并发时扩容上限
        executor.setMaxPoolSize(16);
        // 队列容量：排队等待的流式请求
        executor.setQueueCapacity(100);
        // 线程名前缀，便于日志排查
        executor.setThreadNamePrefix("sse-");
        // 允许核心线程空闲回收：AI 会话具有明显的波峰波谷，空闲时释放资源
        executor.setAllowCoreThreadTimeOut(true);
        // 关闭时等待已提交任务执行完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 手动初始化线程池
        executor.initialize();
        // 返回初始化完成的线程池
        return executor;
    }
}
