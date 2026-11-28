package com.ycc.zhixiucloud.service.concierge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.request.ConciergeRequest.Chat;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.CitationItem;
import com.ycc.zhixiucloud.response.ConciergeResponse.Message;
import com.ycc.zhixiucloud.response.ConciergeResponse.Session;
import com.ycc.zhixiucloud.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.UnaryOperator;

@Repository
@RequiredArgsConstructor
public class ConciergeStore {

    private static final String OWNER = "user_id = ? AND role_code = ?";
    private static final String MESSAGES = """
            SELECT id, request_id, user_content, context_order_id, answer, citations, query_spec, state, create_time
            FROM ai_concierge_message WHERE session_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public Session create(LoginUser user, String title) {
        long id = IdWorker.getId();
        String name = StringUtils.hasText(title) ? title.strip() : "新对话";
        jdbc.update("""
                INSERT INTO ai_concierge_session (id, user_id, role_code, title) VALUES (?, ?, ?, ?)
                """, id, user.getUserId(), user.getRoleCode(), name);
        return requireSession(id, user);
    }

    public PageResult<Session> list(LoginUser user, PageRequestVO page) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ai_concierge_session WHERE " + OWNER,
                Long.class, user.getUserId(), user.getRoleCode());
        List<Session> rows = jdbc.query("""
                SELECT id, title, update_time FROM ai_concierge_session WHERE
                """ + OWNER + " ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?",
                this::session, user.getUserId(), user.getRoleCode(), page.getPageSize(), offset(page));
        return result(page, total, rows);
    }

    public Session requireSession(long id, LoginUser user) {
        return jdbc.query("SELECT id, title, update_time FROM ai_concierge_session WHERE id = ? AND " + OWNER,
                this::session, id, user.getUserId(), user.getRoleCode()).stream().findFirst()
                .orElseThrow(() -> BizException.of(ResultCode.NOT_FOUND, "会话不存在"));
    }

    public PageResult<Message> history(long sessionId, PageRequestVO page, UnaryOperator<Message> enrich) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ai_concierge_message WHERE session_id = ?",
                Long.class, sessionId);
        List<Message> rows = jdbc.query(MESSAGES + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (result, index) -> enrich.apply(stored(result).message()), sessionId, page.getPageSize(), offset(page));
        return result(page, total, rows);
    }

    public SavedMessage requireMessage(long sessionId, long id) {
        return jdbc.query(MESSAGES + " AND id = ?", (row, index) -> stored(row), sessionId, id)
                .stream().findFirst().orElseThrow(() -> BizException.of(ResultCode.NOT_FOUND, "消息不存在"));
    }

    public SavedMessage findRequest(long sessionId, String requestId) {
        return jdbc.query(MESSAGES + " AND request_id = ?", (row, index) -> stored(row), sessionId, requestId)
                .stream().findFirst().orElse(null);
    }

    public long start(long sessionId, Chat chat) {
        long id = IdWorker.getId();
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO ai_concierge_message
                      (id, session_id, request_id, user_content, context_order_id, state)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """, id, sessionId, chat.requestId().toString(), chat.content().strip(), chat.orderId());
            String stripped = chat.content().strip();
            int end = stripped.offsetByCodePoints(0, Math.min(32, stripped.codePointCount(0, stripped.length())));
            jdbc.update("""
                    UPDATE ai_concierge_session
                    SET title = CASE WHEN title = '新对话' THEN ? ELSE title END, update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, stripped.substring(0, end), sessionId);
        });
        return id;
    }

    public void finish(long sessionId, long id, String answer, List<CitationItem> citations, ConciergeQueries.Query query) {
        String citationsJson = citations.isEmpty() ? null : encode(citations);
        String queryJson = query == null ? null : encode(query);
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE ai_concierge_message SET answer = ?, citations = ?, query_spec = ?, state = 'COMPLETE'
                    WHERE id = ? AND session_id = ?
                    """, answer, citationsJson, queryJson, id, sessionId);
            jdbc.update("UPDATE ai_concierge_session SET update_time = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
        });
    }

    public void fail(long id) {
        jdbc.update("UPDATE ai_concierge_message SET state = 'FAILED' WHERE id = ? AND state <> 'COMPLETE'", id);
    }

    private SavedMessage stored(ResultSet row) throws SQLException {
        String query = row.getString("query_spec");
        String citations = row.getString("citations");
        List<CitationItem> items = citations == null ? List.of() : decode(citations, new TypeReference<>() {});
        var message = new Message(row.getLong("id"), row.getString("request_id"), row.getString("user_content"),
                row.getObject("context_order_id", Long.class), row.getString("answer"), items, query != null,
                row.getString("state"), row.getObject("create_time", LocalDateTime.class), null);
        return new SavedMessage(message, query == null ? null : decode(query, new TypeReference<>() {}));
    }

    private Session session(ResultSet row, int index) throws SQLException {
        return new Session(row.getLong("id"), row.getString("title"), row.getObject("update_time", LocalDateTime.class));
    }

    private static long offset(PageRequestVO page) {
        return ((long) page.getPageNum() - 1) * page.getPageSize();
    }

    private static <T> PageResult<T> result(PageRequestVO page, long total, List<T> rows) {
        return PageResult.of(new Page<T>(page.getPageNum(), page.getPageSize(), total).setRecords(rows));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("管家消息序列化失败", e);
        }
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("管家历史数据读取失败", e);
        }
    }

    public record SavedMessage(Message message, ConciergeQueries.Query query) {
    }
}
