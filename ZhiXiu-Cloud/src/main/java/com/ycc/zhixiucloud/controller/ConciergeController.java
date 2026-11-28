package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.ConciergeRequest.Chat;
import com.ycc.zhixiucloud.request.ConciergeRequest.CreateSession;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.ConciergeResponse.Message;
import com.ycc.zhixiucloud.response.ConciergeResponse.Session;
import com.ycc.zhixiucloud.response.ConciergeResponse.Table;
import com.ycc.zhixiucloud.service.concierge.ConciergeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "全局 AI 管家")
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','WORKER','ADMIN')")
@RequestMapping("/api/ai/concierge")
public class ConciergeController {

    private final ConciergeService service;

    @PostMapping("/sessions")
    public Result<Session> create(@Valid @RequestBody(required = false) CreateSession request) {
        return Result.ok(service.create(request == null ? null : request.title()));
    }

    @GetMapping("/sessions")
    public Result<PageResult<Session>> list(@Valid PageRequestVO page) {
        return Result.ok(service.list(page));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<PageResult<Message>> history(@PathVariable @Positive Long id, @Valid PageRequestVO page) {
        return Result.ok(service.history(id, page));
    }

    @PostMapping(value = "/sessions/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable @Positive Long id, @Valid @RequestBody Chat request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        service.chat(id, request, emitter);
        return emitter;
    }

    @GetMapping("/sessions/{id}/messages/{messageId}/table")
    public Result<Table> table(@PathVariable @Positive Long id, @PathVariable @Positive Long messageId,
                               @RequestParam(defaultValue = "1") @Min(1) int pageNum) {
        return Result.ok(service.table(id, messageId, pageNum));
    }
}
