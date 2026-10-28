package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.AiChatRequestVO;
import com.ycc.zhixiucloud.request.AiDraftConfirmRequestVO;
import com.ycc.zhixiucloud.request.AiSessionCreateRequestVO;
import com.ycc.zhixiucloud.response.AiDraftResponseVO;
import com.ycc.zhixiucloud.response.AiMessageResponseVO;
import com.ycc.zhixiucloud.response.AiSessionCreateResponseVO;
import com.ycc.zhixiucloud.response.AiSessionResponseVO;
import com.ycc.zhixiucloud.response.OrderCreateResponseVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能维修助手服务：会话管理、RAG 检索、规则槽位填充、草稿生成与转工单。
 *
 * <p>职责边界：只负责“对话 → 草稿 → 工单创建入口”的链路，不直接维护工单状态；
 * 草稿确认后委托 {@link WorkOrderService#createFromAiDraft} 创建工单，
 * 后续派单、到场、完工等状态流转由工单状态机负责。</p>
 *
 * <p>主要使用方：业主端 AI 助手控制器（AiController）。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>流式交互：{@link #chat} 必须通过 SSE 推送，事件依次为
 *       message/intent/citations/slot/followup/draft/done/error，禁止改为一次性返回；</li>
 *   <li>异步执行：在专用线程池取得会话锁后，以短事务落库用户消息，再调用模型，
 *       避免长时间占用 Web 请求线程；</li>
 *   <li>降级：未配置大模型 API Key 时仍按 SSE 契约返回提示事件，引导业主改用手动报修；</li>
 *   <li>权限：所有会话操作都校验会话归属当前登录业主；</li>
 *   <li>事务：创建会话、确认草稿、删除会话使用数据库事务；
 *       流式回答过程中的多次落库不放在同一个长事务里。</li>
 * </ul>
 */
public interface AiAssistantService {

    /**
     * 创建新会话。
     *
     * <p>会话归属取自当前登录业主；标题可为空，首条用户消息发送时会用其摘要回填标题。
     * 初始消息数为 0，解决状态为“进行中”。</p>
     *
     * @param requestVO 创建参数，可为 null；title 为空时不设置标题
     * @return 新会话 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 未登录或数据库写入失败时
     *         由全局异常处理统一转换
     */
    AiSessionCreateResponseVO createSession(AiSessionCreateRequestVO requestVO);

    /**
     * 以 SSE 处理普通对话或最新回答的按钮动作。
     *
     * <p>CHAT 和 UNRESOLVED 流式给出方案；GENERATE_DRAFT 才预填写草稿；
     * RESOLVED 根据用户明确反馈结束排查。异步线程按会话加锁，模型期间不持有数据库事务。
     * 命名事件包括 message、intent、citations、slot、followup、draft、done、error。
     * done 返回真实消息 ID 和会话状态，失败通过 error 事件关闭连接。</p>
     *
     * @param sessionId 会话 ID，必须属于当前登录业主
     * @param requestVO CHAT 需 content，其他动作需最新已完成 AI 回答的 messageId
     * @param emitter SSE 连接，由控制器创建
     */
    void chat(Long sessionId, AiChatRequestVO requestVO, SseEmitter emitter);

    /**
     * 查询当前业主的全部会话，按更新时间倒序，用于侧边栏会话列表。
     *
     * @return 会话摘要列表（标题、消息数、解决状态、时间）；无会话时返回空列表
     */
    List<AiSessionResponseVO> listSessions();

    /**
     * 查询指定会话的历史消息，按创建时间正序返回，用于前端恢复对话。
     *
     * @param sessionId 会话 ID，必须属于当前登录业主
     * @return 消息列表；会话无消息时返回空列表
     * @throws com.ycc.zhixiucloud.common.exception.BizException 会话不存在（404）或
     *         不属于当前业主（403）
     */
    List<AiMessageResponseVO> listMessages(Long sessionId);

    /**
     * 获取会话最新有效 AI 草稿，供前端回显确认表单；成功生成起有效 15 分钟，读取不续期。
     *
     * @param sessionId 会话 ID，必须属于当前登录业主
     * @return 草稿 ID 与表单内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 会话无草稿、草稿过期或会话结束时抛出 3002
     *         （AI_DRAFT_EMPTY）；会话不存在或越权时抛 404/403
     */
    AiDraftResponseVO getDraft(Long sessionId);

    /**
     * 确认草稿并生成正式工单（业主报修的 AI 入口）。
     *
     * <p>用户确认值优先于草稿值；合并后仍缺少报修类型时拒绝创建。创建成功后把会话标记为
     * “已转工单”；重复提交同一草稿返回原工单。通过 {@link WorkOrderService#createFromAiDraft} 完成工单落库、
     * 状态机初始日志、待接单池缓存与 SLA 超时任务调度。</p>
     *
     * @param sessionId 会话 ID，必须属于当前登录业主
     * @param requestVO 当前有效 draftId 与业主确认/补充的字段
     * @return 新工单 ID 与工单号
     * @throws com.ycc.zhixiucloud.common.exception.BizException 无草稿抛 3002；
     *         缺少报修类型抛 3003；房产不存在或不属于本人等由工单服务抛出
     */
    OrderCreateResponseVO confirmDraft(Long sessionId, AiDraftConfirmRequestVO requestVO);

    /**
     * 删除会话及其下全部消息（逻辑删除）。
     *
     * @param sessionId 会话 ID，必须属于当前登录业主
     * @throws com.ycc.zhixiucloud.common.exception.BizException 会话不存在（404）或
     *         不属于当前业主（403）
     */
    void deleteSession(Long sessionId);
}
