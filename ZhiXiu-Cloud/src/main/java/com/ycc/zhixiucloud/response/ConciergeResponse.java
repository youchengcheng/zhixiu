package com.ycc.zhixiucloud.response;

import java.time.LocalDateTime;
import java.util.List;

public final class ConciergeResponse {

    private ConciergeResponse() {
    }

    public record Session(Long id, String title, LocalDateTime updateTime) {
    }

    public record Message(Long id, String requestId, String content, Long orderId, String answer,
                          List<CitationItem> citations, boolean hasTable, String state, LocalDateTime createTime,
                          Link link) {

        public Message withLink(Link destination) {
            return new Message(id, requestId, content, orderId, answer, citations, hasTable, state, createTime, destination);
        }
    }

    public record Table(String title, List<String> columns, List<Row> rows, long total, int page, boolean hasMore,
                        String summary) {
    }

    public record Row(List<String> cells, String path) {
    }

    public record Link(String label, String path) {
    }
}
