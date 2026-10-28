package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.AiChatRequestVO;
import com.ycc.zhixiucloud.request.AiDraftConfirmRequestVO;
import com.ycc.zhixiucloud.request.AiSessionCreateRequestVO;
import com.ycc.zhixiucloud.response.AiDraftResponseVO;
import com.ycc.zhixiucloud.response.AiMessageResponseVO;
import com.ycc.zhixiucloud.response.AiSessionCreateResponseVO;
import com.ycc.zhixiucloud.response.AiSessionResponseVO;
import com.ycc.zhixiucloud.response.OrderCreateResponseVO;
import com.ycc.zhixiucloud.service.AiAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能维修助手接口（业主端）：会话管理、SSE 流式对话、草稿确认转工单。
 * 类级 @PreAuthorize 限定 OWNER，所有会话与草稿均归属当前登录业主，服务层再做归属校验。
 * /api/ai/chat 保持 text/event-stream 流式输出，不得改为一次性返回。
 */
@Tag(name = "智能维修助手")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('OWNER')")
public class AiController {

    /** SSE 连接超时（毫秒），需大于 AI 首 Token 返回时间 */
    private static final long SSE_TIMEOUT_MILLIS = 60000L;

    /** 智能助手业务服务：会话、RAG 检索、流式对话与草稿转工单 */
    private final AiAssistantService aiAssistantService;

    @Operation(summary = "创建会话")
    @PostMapping("/session")
    public Result<AiSessionCreateResponseVO> createSession(
            @Valid @RequestBody(required = false) AiSessionCreateRequestVO requestVO) {
        // requestVO 可为空：为空时新建空会话，会话归属当前登录业主
        return Result.ok(aiAssistantService.createSession(requestVO));
    }

    @Operation(summary = "发送消息或处理回答反馈（SSE 流式）")
    @ApiLog(module = "AI助手", action = "发送消息")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequestVO requestVO) {
        // 建立 SSE 连接并设置 60 秒超时；客户端断开或超时由容器触发回调
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        // 服务层在异步线程中按 message/intent/citations/slot/followup/draft/done/error 事件推送
        aiAssistantService.chat(requestVO.getSessionId(), requestVO, emitter);
        // 立即返回 emitter，Tomcat 保持长连接，响应类型为 text/event-stream
        return emitter;
    }

    @Operation(summary = "会话列表")
    @GetMapping("/session/list")
    public Result<List<AiSessionResponseVO>> sessions() {
        // 仅返回当前登录业主的会话，避免越权查看他人对话
        return Result.ok(aiAssistantService.listSessions());
    }

    @Operation(summary = "会话历史消息")
    @GetMapping("/session/{id}/messages")
    public Result<List<AiMessageResponseVO>> messages(@PathVariable Long id) {
        // 服务层校验会话归属；会话不存在或不属于当前业主时抛业务异常
        return Result.ok(aiAssistantService.listMessages(id));
    }

    @Operation(summary = "获取当前工单草稿")
    @GetMapping("/session/{id}/draft")
    public Result<AiDraftResponseVO> draft(@PathVariable Long id) {
        // 仅返回最新有效草稿及剩余时间；无草稿、过期或会话结束时抛 3002
        return Result.ok(aiAssistantService.getDraft(id));
    }

    @Operation(summary = "确认草稿生成工单")
    @ApiLog(module = "AI助手", action = "确认草稿生成工单")
    @PostMapping("/session/{id}/confirm")
    public Result<OrderCreateResponseVO> confirm(@PathVariable Long id,
                                                 @Valid @RequestBody AiDraftConfirmRequestVO requestVO) {
        // 校验有效草稿并合并用户确认值创建工单，同一草稿重试返回原工单
        return Result.ok(aiAssistantService.confirmDraft(id, requestVO), "工单已提交");
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/session/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 删除当前业主的会话及其消息，删除后不可恢复
        aiAssistantService.deleteSession(id);
        // 无返回数据，仅提示成功
        return Result.<Void>ok(null, "会话已删除");
    }
}
