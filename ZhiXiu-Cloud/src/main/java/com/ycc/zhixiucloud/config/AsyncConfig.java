package com.ycc.zhixiucloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 通用异步线程池配置。
 *
 * <p>提供名为 taskExecutor 的线程池：Spring 的 @Async 在未显式指定执行器时，
 * 会优先查找该名称的 Bean，因此本类与启动类的 @EnableAsync 配套使用。
 * 主要承载 AI 异步任务、通知发送、日志落库等与主请求链路解耦的工作，
 * 避免这些耗时操作阻塞 Tomcat 请求线程。</p>
 */
@Configuration
public class AsyncConfig {

    /**
     * 通用异步执行器。
     *
     * <p>参数取值考虑：核心 8 线程覆盖日常并发；最大 16 线程应对突发流量；
     * 队列 200 缓冲短时峰值；非核心线程空闲 60 秒后回收。拒绝策略使用
     * CallerRunsPolicy，在线程池与队列都饱和时由提交任务的线程自己执行，
     * 形成天然背压，避免任务被静默丢弃。</p>
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        // 创建通用异步线程池实例
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：常驻线程，保证日常异步任务的吞吐
        executor.setCorePoolSize(8);
        // 最大线程数：等待队列满后最多扩容到 16 个线程
        executor.setMaxPoolSize(16);
        // 等待队列容量：缓冲突发任务，避免频繁创建/销毁线程
        executor.setQueueCapacity(200);
        // 非核心线程空闲存活时间（秒），空闲后回收以释放资源
        executor.setKeepAliveSeconds(60);
        // 线程名前缀，日志与线程转储中可直接识别异步任务
        executor.setThreadNamePrefix("async-");
        // 饱和时退回调用者线程执行（背压），保证任务不丢失并降低任务提交速度
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待已提交任务执行完毕，防止收尾数据（如日志落库）丢失
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 关闭时最长再等待 30 秒，避免停机卡死
        executor.setAwaitTerminationSeconds(30);
        // 手动初始化，确保 Bean 返回前线程池已创建就绪
        executor.initialize();
        // 返回线程池实例，供 @Async 使用
        return executor;
    }
}
