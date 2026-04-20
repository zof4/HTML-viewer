package com.example.htmlviewer.service;

import com.example.htmlviewer.model.DocumentModels;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {

    private final Map<UUID, StoredDocument> documents = new ConcurrentHashMap<>();

    public DocumentModels.DocumentDto create(String name, String html, String userId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        StoredDocument stored = new StoredDocument(id, name);
        stored.currentHtml = html;
        stored.currentVersion = 1;
        stored.updatedBy = userId;
        stored.updatedAt = now;
        stored.versions.add(new VersionSnapshot(1, html, userId, now));
        documents.put(id, stored);
        return toDto(stored);
    }

    public DocumentModels.DocumentDto get(UUID id) {
        StoredDocument stored = requireDocument(id);
        return toDto(stored);
    }

    public DocumentModels.SaveDocumentResponse save(UUID id, int baseVersion, String html, String userId) {
        StoredDocument stored = requireDocument(id);
        synchronized (stored) {
            if (baseVersion != stored.currentVersion) {
                throw new VersionConflictException(stored.currentVersion, stored.currentHtml);
            }
            int nextVersion = stored.currentVersion + 1;
            Instant now = Instant.now();
            stored.currentVersion = nextVersion;
            stored.currentHtml = html;
            stored.updatedBy = userId;
            stored.updatedAt = now;
            stored.versions.add(new VersionSnapshot(nextVersion, html, userId, now));
            return new DocumentModels.SaveDocumentResponse(id, nextVersion, now);
        }
    }

    public DocumentModels.DocumentHistoryResponse versions(UUID id) {
        StoredDocument stored = requireDocument(id);
        List<DocumentModels.DocumentVersionDto> out = stored.versions.stream()
                .sorted(Comparator.comparingInt(VersionSnapshot::version))
                .map(v -> new DocumentModels.DocumentVersionDto(v.version, v.html, v.updatedBy, v.updatedAt))
                .toList();
        return new DocumentModels.DocumentHistoryResponse(id, out);
    }

    public DocumentModels.SaveDocumentResponse restore(UUID id, int version, String userId) {
        StoredDocument stored = requireDocument(id);
        synchronized (stored) {
            VersionSnapshot target = stored.versions.stream()
                    .filter(v -> v.version == version)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
            int nextVersion = stored.currentVersion + 1;
            Instant now = Instant.now();
            stored.currentHtml = target.html;
            stored.currentVersion = nextVersion;
            stored.updatedBy = userId;
            stored.updatedAt = now;
            stored.versions.add(new VersionSnapshot(nextVersion, target.html, userId, now));
            return new DocumentModels.SaveDocumentResponse(id, nextVersion, now);
        }
    }

    public String sanitizeHtml(String html) {
        Safelist safelist = Safelist.relaxed()
                .addTags("iframe")
                .addAttributes("iframe", "src", "srcdoc", "width", "height", "title")
                .addProtocols("iframe", "src", "http", "https");
        return Jsoup.clean(html, safelist);
    }

    private DocumentModels.DocumentDto toDto(StoredDocument stored) {
        return new DocumentModels.DocumentDto(
                stored.id,
                stored.name,
                stored.currentHtml,
                sanitizeHtml(stored.currentHtml),
                stored.currentVersion,
                stored.updatedBy,
                stored.updatedAt
        );
    }

    private StoredDocument requireDocument(UUID id) {
        StoredDocument stored = documents.get(id);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return stored;
    }

    public static class VersionConflictException extends RuntimeException {
        private final int currentVersion;
        private final String currentHtml;

        public VersionConflictException(int currentVersion, String currentHtml) {
            super("Version conflict");
            this.currentVersion = currentVersion;
            this.currentHtml = currentHtml;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }

        public String getCurrentHtml() {
            return currentHtml;
        }
    }

    private static class StoredDocument {
        private final UUID id;
        private final String name;
        private String currentHtml;
        private int currentVersion;
        private String updatedBy;
        private Instant updatedAt;
        private final List<VersionSnapshot> versions = new ArrayList<>();

        private StoredDocument(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private record VersionSnapshot(int version, String html, String updatedBy, Instant updatedAt) {}
}
