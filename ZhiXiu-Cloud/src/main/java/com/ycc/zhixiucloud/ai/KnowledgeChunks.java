package com.ycc.zhixiucloud.ai;

import com.ycc.zhixiucloud.entity.RepairKnowledge;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 在线向量化和离线回填共用的知识分片规则。 */
public final class KnowledgeChunks {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    private KnowledgeChunks() {
    }

    public static List<Document> split(RepairKnowledge knowledge, int version) {
        String content = knowledge.getContent();
        List<String> texts = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int remaining = content.codePointCount(start, content.length());
            int end = content.offsetByCodePoints(start, Math.min(CHUNK_SIZE, remaining));
            if (end < content.length()) {
                int minimumEnd = content.offsetByCodePoints(start, CHUNK_SIZE / 2);
                for (int index = end - 1; index >= minimumEnd; index--) {
                    if ("\n。！？；.!?;".indexOf(content.charAt(index)) >= 0) {
                        end = index + 1;
                        break;
                    }
                }
            }
            String text = content.substring(start, end).strip();
            if (StringUtils.hasText(text)) {
                texts.add(text);
            }
            if (end == content.length()) {
                break;
            }
            start = content.offsetByCodePoints(end, -CHUNK_OVERLAP);
        }

        List<Document> documents = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            Document.Builder builder = Document.builder()
                    .id(knowledge.getId() + ":chunk:" + index)
                    .text(texts.get(index))
                    .metadata("knowledgeId", knowledge.getId().toString())
                    .metadata("title", knowledge.getTitle())
                    .metadata("knowledgeVersion", version)
                    .metadata("chunkIndex", index + 1)
                    .metadata("chunkCount", texts.size());
            if (knowledge.getCategoryId() != null) {
                builder.metadata("categoryId", knowledge.getCategoryId().toString());
            }
            documents.add(builder.build());
        }
        return documents;
    }

    public static Long knowledgeId(Document chunk) {
        Object value = chunk.getMetadata().get("knowledgeId");
        return parseId(value == null ? chunk.getId() : value.toString());
    }

    private static Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean hasCurrentVersion(Document chunk, RepairKnowledge knowledge) {
        return Objects.equals(String.valueOf(knowledge.getVersion()),
                String.valueOf(chunk.getMetadata().get("knowledgeVersion")));
    }
}
