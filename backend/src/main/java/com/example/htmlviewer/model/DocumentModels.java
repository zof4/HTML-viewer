package com.example.htmlviewer.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DocumentModels {
    public record CreateDocumentRequest(@NotBlank String name, @NotBlank String html) {}

    public record SaveDocumentRequest(@NotNull Integer baseVersion, @NotBlank String html, String clientId) {}

    public record DocumentVersionDto(int version, String html, String updatedBy, Instant updatedAt) {}

    public record DocumentDto(UUID id, String name, String currentHtml, String sanitizedHtml, int currentVersion,
                              String updatedBy, Instant updatedAt) {}

    public record SaveDocumentResponse(UUID documentId, int newVersion, Instant updatedAt) {}

    public record ConflictResponse(String error, int currentVersion, String currentHtml) {}

    public record DocumentHistoryResponse(UUID documentId, List<DocumentVersionDto> versions) {}

    public record UpdateEvent(String type, UUID documentId, int version, String updatedBy, Instant updatedAt) {}
}
