package minisearch.models;

import java.time.Instant;

public record Document(int id, String title, String content, String addedAt) {
    public Document(int id, String title, String content) {
        this(id, title, content, Instant.now().toString());
    }
    public int wordCount() { return content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length; }
}
