package com.ycc.zhixiucloud.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 仅在尚未输出内容时重试模型调用；不会重放用户消息、反馈或工单操作。 */
@Slf4j
@Component
public class AiStreamRetry {

    public void consume(Supplier<Flux<String>> source, Consumer<String> output, Long sessionId) {
        AtomicBoolean outputStarted = new AtomicBoolean();
        Flux.defer(source)
                .filter(StringUtils::hasLength)
                .retryWhen(Retry.backoff(1, Duration.ofMillis(500))
                        .jitter(0.2)
                        .filter(error -> !outputStarted.get() && isTransient(error))
                        .doBeforeRetry(signal -> log.warn(
                                "AI 流式连接重试：sessionId={}, retry=1/1, cause={}",
                                sessionId, Exceptions.unwrap(signal.failure()).getClass().getSimpleName()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnNext(delta -> {
                    outputStarted.set(true);
                    output.accept(delta);
                })
                .blockLast();
    }

    private boolean isTransient(Throwable error) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        Throwable cause = Exceptions.unwrap(error);
        while (cause != null) {
            if (cause instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                return status == 408 || status == 429 || status == 500
                        || status == 502 || status == 503 || status == 504;
            }
            if (cause instanceof TransientAiException
                    || cause instanceof IOException || cause instanceof TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
