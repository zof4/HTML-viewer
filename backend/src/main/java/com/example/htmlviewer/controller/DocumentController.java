package com.example.htmlviewer.controller;

import com.example.htmlviewer.model.DocumentModels;
import com.example.htmlviewer.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    public DocumentController(DocumentService documentService, SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentModels.DocumentDto create(@Valid @RequestBody DocumentModels.CreateDocumentRequest request,
                                             @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return documentService.create(request.name(), request.html(), userId);
    }

    @GetMapping("/{id}")
    public DocumentModels.DocumentDto get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @PutMapping("/{id}")
    public DocumentModels.SaveDocumentResponse save(@PathVariable UUID id,
                                                    @Valid @RequestBody DocumentModels.SaveDocumentRequest request,
                                                    @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        DocumentModels.SaveDocumentResponse response = documentService.save(id, request.baseVersion(), request.html(), userId);
        publishUpdate(id, response.newVersion(), userId);
        return response;
    }

    @GetMapping("/{id}/versions")
    public DocumentModels.DocumentHistoryResponse versions(@PathVariable UUID id) {
        return documentService.versions(id);
    }

    @PostMapping("/{id}/restore/{version}")
    public DocumentModels.SaveDocumentResponse restore(@PathVariable UUID id,
                                                       @PathVariable int version,
                                                       @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        DocumentModels.SaveDocumentResponse response = documentService.restore(id, version, userId);
        publishUpdate(id, response.newVersion(), userId);
        return response;
    }

    @GetMapping("/{id}/ws-token")
    public Map<String, Object> wsToken(@PathVariable UUID id,
                                       @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return Map.of(
                "documentId", id,
                "user", userId,
                "token", UUID.randomUUID().toString(),
                "issuedAt", Instant.now().toString()
        );
    }

    private void publishUpdate(UUID id, int version, String userId) {
        messagingTemplate.convertAndSend(
                "/topic/documents/" + id,
                new DocumentModels.UpdateEvent("DOCUMENT_UPDATED", id, version, userId, Instant.now())
        );
    }
}
